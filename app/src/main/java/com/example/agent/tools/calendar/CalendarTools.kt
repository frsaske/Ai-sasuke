package com.example.agent.tools.calendar

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.calendar.CalendarManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarListCalendarsTool(private val context: Context) : Tool {
    override val name: String = "calendar_list_calendars"
    override val description: String = "List all Google and local calendars available on this device."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf("type" to "OBJECT", "properties" to emptyMap<String, Any>())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!CalendarManager.hasCalendarPermissions(context)) {
            return ToolResult.failure("PERMISSION_REQUIRED", "Calendar permissions not granted. Grant Calendar permission in settings.")
        }
        val calendars = CalendarManager.listCalendars(context)
        return ToolResult.success(
            data = mapOf("count" to calendars.size, "calendars" to calendars),
            summary = "Found ${calendars.size} calendar(s) on device"
        )
    }
}

class CalendarListEventsTool(private val context: Context) : Tool {
    override val name: String = "calendar_list_events"
    override val description: String =
        "List events within a time range. You can specify days_ahead (e.g. 7 for next week) or start/end timestamps."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "days_ahead" to mapOf("type" to "INTEGER", "description" to "Number of days from now to list (default 7)"),
            "calendar_id" to mapOf("type" to "INTEGER", "description" to "Optional specific calendar ID")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!CalendarManager.hasCalendarPermissions(context)) {
            return ToolResult.failure("PERMISSION_REQUIRED", "Calendar permission required.")
        }

        val daysAhead = (arguments["days_ahead"] as? Number)?.toInt() ?: 7
        val calendarId = (arguments["calendar_id"] as? Number)?.toLong()

        val start = System.currentTimeMillis() - 3600_000L // 1 hour ago
        val end = start + (daysAhead.toLong() * 24 * 3600_000L)

        val events = CalendarManager.listEvents(context, start, end, calendarId)
        val compact = events.map {
            mapOf(
                "id" to it.id,
                "title" to it.title,
                "start" to it.formattedStart,
                "end" to it.formattedEnd,
                "location" to (it.location ?: ""),
                "description" to (it.description ?: "")
            )
        }

        return ToolResult.success(
            data = mapOf("count" to compact.size, "events" to compact),
            summary = "Found ${compact.size} event(s) in next $daysAhead day(s)"
        )
    }
}

class CalendarSearchEventsTool(private val context: Context) : Tool {
    override val name: String = "calendar_search_events"
    override val description: String = "Search calendar events by keyword, title, or description."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf("type" to "STRING", "description" to "Keyword to search for (e.g. 'physics', 'meeting')"),
            "days_window" to mapOf("type" to "INTEGER", "description" to "Number of days window to search (default 30)")
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val query = arguments["query"]?.toString()?.trim() ?: ""
        val days = (arguments["days_window"] as? Number)?.toInt() ?: 30

        val start = System.currentTimeMillis() - (7 * 24 * 3600_000L) // past 7 days
        val end = System.currentTimeMillis() + (days.toLong() * 24 * 3600_000L)

        val events = CalendarManager.searchEvents(context, query, start, end)
        return ToolResult.success(
            data = mapOf("query" to query, "count" to events.size, "events" to events),
            summary = "Found ${events.size} event(s) matching '$query'"
        )
    }
}

class CalendarCreateEventTool(private val context: Context) : Tool {
    override val name: String = "calendar_create_event"
    override val description: String =
        "Create a new event on Google Calendar. Requires title, start_time (ISO string or 'YYYY-MM-DD HH:mm'), duration_minutes."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "title" to mapOf("type" to "STRING", "description" to "Event title (e.g. 'Physics Study Session')"),
            "start_time" to mapOf("type" to "STRING", "description" to "Start date & time (e.g. '2026-10-04 17:00' or 'tomorrow 5pm')"),
            "duration_minutes" to mapOf("type" to "INTEGER", "description" to "Duration in minutes (default 60)"),
            "description" to mapOf("type" to "STRING", "description" to "Optional event details/notes"),
            "location" to mapOf("type" to "STRING", "description" to "Optional location"),
            "attendees" to mapOf("type" to "STRING", "description" to "Optional comma-separated email list of attendees")
        ),
        "required" to listOf("title", "start_time")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val title = arguments["title"]?.toString()?.trim() ?: ""
        val startTimeStr = arguments["start_time"]?.toString()?.trim() ?: ""
        val durationMin = (arguments["duration_minutes"] as? Number)?.toInt() ?: 60
        val desc = arguments["description"]?.toString()
        val loc = arguments["location"]?.toString()

        val parsedStart = parseDateTime(startTimeStr) ?: (System.currentTimeMillis() + 3600_000L)
        val parsedEnd = parsedStart + (durationMin * 60 * 1000L)

        return CalendarManager.createEvent(
            context = context,
            title = title,
            startTimeMs = parsedStart,
            endTimeMs = parsedEnd,
            description = desc,
            location = loc
        ).fold(
            onSuccess = { id ->
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                ToolResult.success(
                    data = mapOf(
                        "event_id" to id,
                        "title" to title,
                        "start" to sdf.format(Date(parsedStart)),
                        "end" to sdf.format(Date(parsedEnd))
                    ),
                    summary = "Created event '$title' for ${sdf.format(Date(parsedStart))}"
                )
            },
            onFailure = { ToolResult.failure("CALENDAR_ERROR", it.localizedMessage ?: "Failed to create event") }
        )
    }

    private fun parseDateTime(str: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (f in formats) {
            try {
                val d = f.parse(str)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }
}

class CalendarDeleteEventTool(private val context: Context) : Tool {
    override val name: String = "calendar_delete_event"
    override val description: String = "Delete a calendar event by ID. Requires confirmation."
    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE
    override val requiresConfirmation: Boolean = true

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "event_id" to mapOf("type" to "INTEGER", "description" to "ID of the event to delete")
        ),
        "required" to listOf("event_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val eventId = (arguments["event_id"] as? Number)?.toLong()
            ?: return ToolResult.failure("INVALID_ARGUMENT", "event_id is required.")

        return CalendarManager.deleteEvent(context, eventId).fold(
            onSuccess = {
                ToolResult.success(data = mapOf("deleted" to true, "event_id" to eventId), summary = "Deleted event #$eventId")
            },
            onFailure = { ToolResult.failure("DELETE_ERROR", it.localizedMessage ?: "Failed to delete event") }
        )
    }
}

class CalendarFindFreeTimeTool(private val context: Context) : Tool {
    override val name: String = "calendar_find_free_time"
    override val description: String = "Find available free time slots between events for meetings or study sessions."
    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "days_ahead" to mapOf("type" to "INTEGER", "description" to "Number of days ahead to look (default 3)"),
            "duration_minutes" to mapOf("type" to "INTEGER", "description" to "Desired slot duration in minutes (default 60)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val days = (arguments["days_ahead"] as? Number)?.toInt() ?: 3
        val duration = (arguments["duration_minutes"] as? Number)?.toInt() ?: 60

        val start = System.currentTimeMillis()
        val end = start + (days.toLong() * 24 * 3600_000L)

        val slots = CalendarManager.findFreeTime(context, start, end, duration)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedSlots = slots.map {
            mapOf("start" to sdf.format(Date(it.first)), "end" to sdf.format(Date(it.second)))
        }

        return ToolResult.success(
            data = mapOf("count" to formattedSlots.size, "free_slots" to formattedSlots),
            summary = "Found ${formattedSlots.size} free slot(s) of $duration min in next $days day(s)"
        )
    }
}
