package com.example.agent.tools.currency

import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExchangeRateTool : Tool {
    override val name: String = "exchange_rate"
    override val description: String =
        "Get live foreign exchange rates and convert currencies using the European Central Bank data via Frankfurter API."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "base" to mapOf("type" to "STRING", "description" to "Base 3-letter currency code (e.g. 'USD', 'EUR', 'GBP')"),
            "target" to mapOf("type" to "STRING", "description" to "Target 3-letter currency code (e.g. 'INR', 'JPY', 'CAD')"),
            "amount" to mapOf("type" to "NUMBER", "description" to "Optional amount to convert (default 1.0)")
        ),
        "required" to listOf("base", "target")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val base = arguments["base"]?.toString()?.trim()?.uppercase(Locale.ROOT) ?: "USD"
        val target = arguments["target"]?.toString()?.trim()?.uppercase(Locale.ROOT) ?: "INR"
        val amount = (arguments["amount"] as? Number)?.toDouble() ?: 1.0

        if (base.length != 3 || target.length != 3) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "Currencies must be 3-letter ISO codes (e.g. USD, INR, EUR).")
        }

        try {
            val url = "https://api.frankfurter.app/latest?from=$base&to=$target"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext ToolResult.failure(
                    "HTTP_ERROR",
                    "Exchange rate API returned HTTP ${response.code}. Check if currency codes '$base' and '$target' are supported."
                )
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val rates = json.optJSONObject("rates")
            val rate = rates?.optDouble(target) ?: 0.0

            if (rate <= 0.0) {
                return@withContext ToolResult.failure("RATE_NOT_FOUND", "Could not find rate for $base to $target.")
            }

            val converted = amount * rate
            val date = json.optString("date", "")

            val data = mapOf(
                "base" to base,
                "target" to target,
                "rate" to rate,
                "amount" to amount,
                "converted_amount" to "%.2f".format(Locale.US, converted),
                "date" to date
            )

            ToolResult.success(
                data = data,
                summary = "$amount $base = ${"%.2f".format(Locale.US, converted)} $target ($date)"
            )
        } catch (e: Exception) {
            ToolResult.failure("NETWORK_ERROR", e.localizedMessage ?: "Failed to connect to currency exchange service.")
        }
    }
}
