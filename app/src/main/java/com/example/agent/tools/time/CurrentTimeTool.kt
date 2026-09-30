package com.example.agent.tools.time

import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CurrentTimeTool : Tool {
    override val name: String = "current_time"
    override val description: String =
        "Get the current local time, date, day of week, and timezone for any city, country, or timezone ID in the world."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "timezone" to mapOf(
                "type" to "STRING",
                "description" to "City or timezone name (e.g. 'Tokyo', 'Asia/Tokyo', 'New York', 'London', 'India', 'UTC')"
            )
        ),
        "required" to listOf("timezone")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.Default) {
        val rawInput = arguments["timezone"]?.toString()?.trim() ?: "UTC"
        val timeZone = resolveTimeZone(rawInput)

        if (timeZone == null) {
            return@withContext ToolResult.failure(
                "INVALID_TIMEZONE",
                "Could not resolve timezone for '$rawInput'. Try standard names like 'Asia/Tokyo', 'America/New_York', 'Europe/London', etc."
            )
        }

        try {
            val now = Date()
            val timeFormatter = SimpleDateFormat("hh:mm:ss a (HH:mm)", Locale.US).apply { this.timeZone = timeZone }
            val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }
            val dayOfWeekFormatter = SimpleDateFormat("EEEE", Locale.US).apply { this.timeZone = timeZone }

            val offsetMillis = timeZone.getOffset(now.time)
            val offsetHours = offsetMillis / (1000 * 60 * 60)
            val offsetMinutes = kotlin.math.abs((offsetMillis / (1000 * 60)) % 60)
            val formattedOffset = buildString {
                append(if (offsetHours >= 0) "+" else "-")
                append(String.format(Locale.US, "%02d:%02d", kotlin.math.abs(offsetHours), offsetMinutes))
            }

            val formattedTime = timeFormatter.format(now)
            val formattedDate = dateFormatter.format(now)
            val dayOfWeek = dayOfWeekFormatter.format(now)

            val data = mapOf(
                "input" to rawInput,
                "timezone" to timeZone.id,
                "current_time" to formattedTime,
                "date" to formattedDate,
                "day_of_week" to dayOfWeek,
                "utc_offset" to "UTC$formattedOffset"
            )

            ToolResult.success(
                data = data,
                summary = "${timeZone.id}: $formattedTime, $formattedDate (UTC$formattedOffset)"
            )
        } catch (e: Exception) {
            ToolResult.failure("TIME_ERROR", e.localizedMessage ?: "Failed to compute time.")
        }
    }

    private fun resolveTimeZone(input: String): TimeZone? {
        val lower = input.lowercase(Locale.ROOT).trim()

        val cityMap = mapOf(
            "tokyo" to "Asia/Tokyo",
            "japan" to "Asia/Tokyo",
            "delhi" to "Asia/Kolkata",
            "mumbai" to "Asia/Kolkata",
            "india" to "Asia/Kolkata",
            "ist" to "Asia/Kolkata",
            "lucknow" to "Asia/Kolkata",
            "bangalore" to "Asia/Kolkata",
            "new york" to "America/New_York",
            "nyc" to "America/New_York",
            "london" to "Europe/London",
            "uk" to "Europe/London",
            "gmt" to "GMT",
            "utc" to "UTC",
            "paris" to "Europe/Paris",
            "berlin" to "Europe/Berlin",
            "san francisco" to "America/Los_Angeles",
            "los angeles" to "America/Los_Angeles",
            "california" to "America/Los_Angeles",
            "seattle" to "America/Los_Angeles",
            "chicago" to "America/Chicago",
            "toronto" to "America/Toronto",
            "sydney" to "Australia/Sydney",
            "melbourne" to "Australia/Melbourne",
            "singapore" to "Asia/Singapore",
            "dubai" to "Asia/Dubai",
            "uae" to "Asia/Dubai",
            "beijing" to "Asia/Shanghai",
            "china" to "Asia/Shanghai",
            "shanghai" to "Asia/Shanghai",
            "hong kong" to "Asia/Hong_Kong",
            "seoul" to "Asia/Seoul",
            "korea" to "Asia/Seoul",
            "moscow" to "Europe/Moscow",
            "russia" to "Europe/Moscow",
            "cairo" to "Africa/Cairo"
        )

        cityMap[lower]?.let {
            return TimeZone.getTimeZone(it)
        }

        // Direct TimeZone match
        val available = TimeZone.getAvailableIDs()
        val directMatch = available.firstOrNull { it.equals(input, ignoreCase = true) }
        if (directMatch != null) {
            return TimeZone.getTimeZone(directMatch)
        }

        val partialMatch = available.firstOrNull { it.lowercase(Locale.ROOT).contains(lower) }
        if (partialMatch != null) {
            return TimeZone.getTimeZone(partialMatch)
        }

        return TimeZone.getTimeZone("UTC")
    }
}
