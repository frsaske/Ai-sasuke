package com.example.ai.service

import com.example.ai.api.GeminiApiService
import com.example.ai.model.AIErrorType
import com.example.ai.model.Content
import com.example.ai.model.GenerateContentRequest
import com.example.ai.model.GenerationConfig
import com.example.ai.model.Part
import com.example.ai.model.StreamEvent
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.BufferedReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GeminiClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(com.example.ai.model.PartJsonAdapterFactory())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Executes generateContent with intelligent dual-mode auth handling (query vs header)
     * to eliminate Google's ACCESS_TOKEN_TYPE_UNSUPPORTED error for both AQ and AIza keys.
     */
    suspend fun executeGenerateContent(
        model: String,
        apiKey: String,
        request: GenerateContentRequest
    ): retrofit2.Response<com.example.ai.model.GenerateContentResponse> {
        val cleanKey = apiKey.trim()

        // 1. Primary standard method: key query parameter (universally supported by Gemini for AQ & AIza keys)
        val firstResponse = apiService.generateContent(
            model = model,
            apiKeyQuery = cleanKey,
            apiKeyHeader = null,
            authHeader = null,
            request = request
        )

        if (firstResponse.isSuccessful) return firstResponse

        // 2. If rejected with 400/401/403, retry with x-goog-api-key header
        if (firstResponse.code() in listOf(400, 401, 403)) {
            val headerResponse = apiService.generateContent(
                model = model,
                apiKeyQuery = null,
                apiKeyHeader = cleanKey,
                authHeader = null,
                request = request
            )
            if (headerResponse.isSuccessful) return headerResponse

            // 3. Fallback to Bearer authorization if user provided an OAuth token
            val bearerResponse = apiService.generateContent(
                model = model,
                apiKeyQuery = null,
                apiKeyHeader = null,
                authHeader = "Bearer $cleanKey",
                request = request
            )
            if (bearerResponse.isSuccessful) return bearerResponse
        }

        return firstResponse
    }

    /**
     * Executes streamGenerateContent with intelligent multi-stage auth fallback.
     */
    suspend fun executeStreamContent(
        model: String,
        apiKey: String,
        request: GenerateContentRequest
    ): retrofit2.Response<ResponseBody> {
        val cleanKey = apiKey.trim()

        // 1. Primary standard method: key query parameter
        val firstResponse = apiService.streamGenerateContent(
            model = model,
            apiKeyQuery = cleanKey,
            apiKeyHeader = null,
            authHeader = null,
            request = request
        )

        if (firstResponse.isSuccessful) return firstResponse

        // 2. If rejected, fallback to x-goog-api-key header
        if (firstResponse.code() in listOf(400, 401, 403)) {
            val headerResponse = apiService.streamGenerateContent(
                model = model,
                apiKeyQuery = null,
                apiKeyHeader = cleanKey,
                authHeader = null,
                request = request
            )
            if (headerResponse.isSuccessful) return headerResponse

            // 3. Fallback to Bearer authorization
            val bearerResponse = apiService.streamGenerateContent(
                model = model,
                apiKeyQuery = null,
                apiKeyHeader = null,
                authHeader = "Bearer $cleanKey",
                request = request
            )
            if (bearerResponse.isSuccessful) return bearerResponse
        }

        return firstResponse
    }

    /**
     * Streams generation response for the provided request.
     */
    fun streamContent(
        model: String,
        apiKey: String,
        request: GenerateContentRequest
    ): Flow<StreamEvent> = flow {
        if (apiKey.isBlank()) {
            emit(
                StreamEvent.Error(
                    AIErrorType.INVALID_API_KEY,
                    "Gemini API key is missing. Please set your API key in Settings."
                )
            )
            return@flow
        }

        val accumulatedText = StringBuilder()
        var hasEmittedAnyChunk = false

        try {
            val response = executeStreamContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: ""
                val (errorType, friendlyMsg) = parseHttpError(response.code(), errorBody)
                emit(StreamEvent.Error(errorType, friendlyMsg))
                return@flow
            }

            val body = response.body()
            if (body == null) {
                emit(
                    StreamEvent.Error(
                        AIErrorType.EMPTY_RESPONSE,
                        "Received empty response from Gemini server."
                    )
                )
                return@flow
            }

            val reader = BufferedReader(body.charStream())
            var line: String? = null

            while (currentCoroutineContext().isActive && reader.readLine().also { line = it } != null) {
                val rawLine = line?.trim() ?: continue
                if (!rawLine.startsWith("data:")) continue

                val jsonPayload = rawLine.removePrefix("data:").trim()
                if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                try {
                    val jsonObject = JSONObject(jsonPayload)
                    if (jsonObject.has("error")) {
                        val errorObj = jsonObject.getJSONObject("error")
                        val code = errorObj.optInt("code", 0)
                        val message = errorObj.optString("message", "")
                        val (errType, errDesc) = parseHttpError(code, message)
                        emit(StreamEvent.Error(errType, errDesc))
                        return@flow
                    }

                    val candidates = jsonObject.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                accumulatedText.append(text)
                                hasEmittedAnyChunk = true
                                emit(StreamEvent.Chunk(text))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient chunk parse failures
                }
            }

            if (!hasEmittedAnyChunk && accumulatedText.isEmpty()) {
                emit(
                    StreamEvent.Error(
                        AIErrorType.EMPTY_RESPONSE,
                        "Gemini returned an empty response. Try rephrasing your prompt."
                    )
                )
            } else {
                emit(StreamEvent.Completed(accumulatedText.toString()))
            }
        } catch (e: Exception) {
            val (errorType, friendlyMsg) = parseException(e)
            emit(StreamEvent.Error(errorType, friendlyMsg))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Lightweight ping to test API key validity.
     */
    suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API key is blank."))
        }

        try {
            val testRequest = GenerateContentRequest(
                contents = listOf(
                    Content(
                        role = "user",
                        parts = listOf(Part(text = "Respond with 'SasukeX connected'"))
                    )
                ),
                generationConfig = GenerationConfig(maxOutputTokens = 20)
            )

            val response = executeGenerateContent(
                model = model,
                apiKey = apiKey,
                request = testRequest
            )

            if (response.isSuccessful) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val text = candidate?.content?.parts?.firstOrNull()?.text?.trim()
                Result.success(text ?: "Connected successfully!")
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val (_, friendlyMsg) = parseHttpError(response.code(), errorBody)
                Result.failure(Exception(friendlyMsg))
            }
        } catch (e: Exception) {
            val (_, friendlyMsg) = parseException(e)
            Result.failure(Exception(friendlyMsg))
        }
    }

    fun parseHttpError(code: Int, errorBody: String): Pair<AIErrorType, String> {
        val lower = errorBody.lowercase()
        return when {
            lower.contains("access_token_type_unsupported") || lower.contains("expected oauth 2 access token") -> {
                AIErrorType.INVALID_API_KEY to "Authentication failed. Please verify your Gemini API key in Settings. Ensure the Generative Language API is enabled for your Google account."
            }
            lower.contains("api_key_service_blocked") -> {
                AIErrorType.INVALID_API_KEY to "Generative Language API is blocked or not enabled for this key in Google Cloud. Please enable Generative Language API in your Google Cloud Console."
            }
            code == 400 || code == 401 || code == 403 || lower.contains("api_key") || lower.contains("apikey") -> {
                AIErrorType.INVALID_API_KEY to "Invalid or unauthorized API key. Please verify your API key in Settings > Gemini API Key."
            }
            code == 429 || lower.contains("rate_limit") || lower.contains("resource_exhausted") -> {
                AIErrorType.RATE_LIMIT to "Rate limit reached. Please wait a brief moment before sending another prompt."
            }
            code == 503 || code == 500 || lower.contains("unavailable") -> {
                AIErrorType.SERVICE_UNAVAILABLE to "Google Gemini service is temporarily overloaded or unavailable. Please retry shortly."
            }
            lower.contains("quota") -> {
                AIErrorType.QUOTA_EXCEEDED to "API quota exhausted for this key. Please check your Google AI Studio plan."
            }
            else -> {
                AIErrorType.UNKNOWN to "Gemini service returned code $code. Error: ${errorBody.take(120)}"
            }
        }
    }

    private fun parseException(e: Exception): Pair<AIErrorType, String> {
        return when (e) {
            is UnknownHostException, is ConnectException -> {
                AIErrorType.NO_INTERNET to "No internet connection detected. Please verify your Wi-Fi or mobile data."
            }
            is SocketTimeoutException -> {
                AIErrorType.TIMEOUT to "Request timed out waiting for Gemini response. Please try again."
            }
            else -> {
                AIErrorType.UNKNOWN to (e.localizedMessage ?: "An unexpected network error occurred.")
            }
        }
    }
}
