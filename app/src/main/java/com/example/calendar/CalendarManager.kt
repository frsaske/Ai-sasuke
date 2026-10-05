package com.example.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val isPrimary: Boolean,
    val color: Int
)

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isAllDay: Boolean,
    val timezone: String,
    val formattedStart: String,
    val formattedEnd: String
)

object CalendarManager {

    fun hasCalendarPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    suspend fun listCalendars(context: Context): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) return@withContext emptyList()

        val list = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val primaryCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                val colorCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)

                while (c.moveToNext()) {
                    list.add(
                        CalendarInfo(
                            id = c.getLong(idCol),
                            name = c.getString(nameCol) ?: "Calendar",
                            accountName = c.getString(accCol) ?: "",
                            isPrimary = c.getInt(primaryCol) == 1,
                            color = c.getInt(colorCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        list
    }

    suspend fun listEvents(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        calendarId: Long? = null,
        maxResults: Int = 30
    ): List<CalendarEventItem> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) return@withContext emptyList()

        val list = mutableListOf<CalendarEventItem>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startTimeMs)
        ContentUris.appendId(builder, endTimeMs)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_TIMEZONE
        )

        val selection = if (calendarId != null) "${CalendarContract.Instances.CALENDAR_ID} = ?" else null
        val selectionArgs = if (calendarId != null) arrayOf(calendarId.toString()) else null

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleCol = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descCol = c.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val locCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val startCol = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val tzCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_TIMEZONE)

                while (c.moveToNext() && list.size < maxResults) {
                    val s = c.getLong(startCol)
                    val e = c.getLong(endCol)
                    list.add(
                        CalendarEventItem(
                            id = c.getLong(idCol),
                            title = c.getString(titleCol) ?: "Event",
                            description = c.getString(descCol),
                            location = c.getString(locCol),
                            startTimeMs = s,
                            endTimeMs = e,
                            isAllDay = c.getInt(allDayCol) == 1,
                            timezone = c.getString(tzCol) ?: TimeZone.getDefault().id,
                            formattedStart = sdf.format(Date(s)),
                            formattedEnd = sdf.format(Date(e))
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        list
    }

    suspend fun searchEvents(
        context: Context,
        query: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<CalendarEventItem> = withContext(Dispatchers.IO) {
        val allEvents = listEvents(context, startTimeMs, endTimeMs, maxResults = 100)
        val q = query.lowercase(Locale.ROOT)
        allEvents.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
            it.description?.lowercase(Locale.ROOT)?.contains(q) == true ||
            it.location?.lowercase(Locale.ROOT)?.contains(q) == true
        }
    }

    suspend fun createEvent(
        context: Context,
        title: String,
        startTimeMs: Long,
        endTimeMs: Long,
        calendarId: Long? = null,
        description: String? = null,
        location: String? = null,
        isAllDay: Boolean = false,
        timezone: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) {
            return@withContext Result.failure(SecurityException("Calendar permissions not granted."))
        }

        val targetCalendarId = calendarId ?: listCalendars(context).firstOrNull()?.id ?: 1L
        val tz = timezone ?: TimeZone.getDefault().id

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTimeMs)
            put(CalendarContract.Events.DTEND, endTimeMs)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description ?: "")
            put(CalendarContract.Events.EVENT_LOCATION, location ?: "")
            put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
        }

        try {
            val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            if (eventId != null) {
                // Add default 15 min reminder
                val remValues = ContentValues().apply {
                    put(CalendarContract.Reminders.MINUTES, 15)
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, remValues)
                Result.success(eventId)
            } else {
                Result.failure(Exception("Failed to insert event into Calendar Provider."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(context: Context, eventId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) {
            return@withContext Result.failure(SecurityException("Calendar permissions not granted."))
        }
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.delete(uri, null, null)
            Result.success(rows > 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findFreeTime(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        durationMinutes: Int = 60
    ): List<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        val events = listEvents(context, startTimeMs, endTimeMs, maxResults = 100)
            .sortedBy { it.startTimeMs }

        val freeSlots = mutableListOf<Pair<Long, Long>>()
        val durationMs = durationMinutes * 60 * 1000L

        var currentStart = startTimeMs
        for (event in events) {
            if (event.startTimeMs - currentStart >= durationMs) {
                freeSlots.add(Pair(currentStart, event.startTimeMs))
            }
            if (event.endTimeMs > currentStart) {
                currentStart = event.endTimeMs
            }
        }

        if (endTimeMs - currentStart >= durationMs) {
            freeSlots.add(Pair(currentStart, endTimeMs))
        }

        freeSlots.take(5)
    }
}
