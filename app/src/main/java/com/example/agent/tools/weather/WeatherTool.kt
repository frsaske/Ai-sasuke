package com.example.agent.tools.weather

import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WeatherTool : Tool {
    override val name: String = "get_weather"
    override val description: String =
        "Get live weather, temperature, humidity, wind, and forecast for any city or location in the world using Open-Meteo."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "location" to mapOf(
                "type" to "STRING",
                "description" to "The name of the city, region, or location (e.g. 'Lucknow', 'Tokyo', 'London')"
            )
        ),
        "required" to listOf("location")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val location = arguments["location"]?.toString()?.trim()
        if (location.isNullOrEmpty()) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "Location parameter is required.")
        }

        try {
            // 1. Geocode location via Open-Meteo
            val encodedLoc = URLEncoder.encode(location, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedLoc&count=1&language=en&format=json"

            val geoRequest = Request.Builder().url(geoUrl).build()
            val geoResponse = httpClient.newCall(geoRequest).execute()
            if (!geoResponse.isSuccessful) {
                return@withContext ToolResult.failure("GEOCODING_FAILED", "Failed to find coordinates for '$location'.")
            }

            val geoJson = JSONObject(geoResponse.body?.string() ?: "{}")
            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext ToolResult.failure("LOCATION_NOT_FOUND", "Could not find any location matching '$location'.")
            }

            val match = results.getJSONObject(0)
            val name = match.optString("name", location)
            val country = match.optString("country", "")
            val admin1 = match.optString("admin1", "")
            val lat = match.optDouble("latitude")
            val lon = match.optDouble("longitude")

            // 2. Fetch Forecast
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"

            val weatherRequest = Request.Builder().url(weatherUrl).build()
            val weatherResponse = httpClient.newCall(weatherRequest).execute()
            if (!weatherResponse.isSuccessful) {
                return@withContext ToolResult.failure("WEATHER_API_ERROR", "Failed to retrieve forecast data.")
            }

            val weatherJson = JSONObject(weatherResponse.body?.string() ?: "{}")
            val current = weatherJson.optJSONObject("current")
            val daily = weatherJson.optJSONObject("daily")

            val temp = current?.optDouble("temperature_2m") ?: 0.0
            val feelsLike = current?.optDouble("apparent_temperature") ?: temp
            val humidity = current?.optInt("relative_humidity_2m", 0) ?: 0
            val windSpeed = current?.optDouble("wind_speed_10m") ?: 0.0
            val precip = current?.optDouble("precipitation") ?: 0.0
            val weatherCode = current?.optInt("weather_code", 0) ?: 0
            val condition = getWeatherCondition(weatherCode)

            val maxTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0) ?: temp
            val minTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0) ?: temp

            val formattedLocation = if (admin1.isNotEmpty() && admin1 != name) "$name, $admin1, $country" else "$name, $country"

            val data = mapOf(
                "location" to formattedLocation,
                "temperature" to "$temp°C",
                "apparent_temperature" to "$feelsLike°C",
                "condition" to condition,
                "humidity" to "$humidity%",
                "wind_speed" to "$windSpeed km/h",
                "precipitation" to "$precip mm",
                "daily_high" to "$maxTemp°C",
                "daily_low" to "$minTemp°C"
            )

            ToolResult.success(
                data = data,
                summary = "$name: $temp°C, $condition"
            )
        } catch (e: Exception) {
            ToolResult.failure("NETWORK_ERROR", e.localizedMessage ?: "Failed to connect to weather service.")
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear sky ☀️"
            1 -> "Mainly clear 🌤"
            2 -> "Partly cloudy ⛅"
            3 -> "Overcast ☁️"
            45, 48 -> "Foggy 🌫"
            51, 53, 55 -> "Light Drizzle 🌦"
            56, 57 -> "Freezing Drizzle 🌧"
            61, 63 -> "Rain 🌧"
            65 -> "Heavy Rain 🌧🌧"
            66, 67 -> "Freezing Rain 🌨"
            71, 73 -> "Snow fall 🌨"
            75 -> "Heavy Snow fall ❄️"
            77 -> "Snow grains ❄️"
            80, 81, 82 -> "Rain showers 🌦"
            85, 86 -> "Snow showers 🌨"
            95 -> "Thunderstorm ⛈"
            96, 99 -> "Thunderstorm with hail ⛈⚡"
            else -> "Variably Cloudy"
        }
    }
}
