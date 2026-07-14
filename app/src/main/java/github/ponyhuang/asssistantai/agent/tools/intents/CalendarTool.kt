package github.ponyhuang.asssistantai.agent.tools.intents

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.permission.CalendarPermissionActivity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and creates events in the device calendar provider. */
@Singleton
class CalendarTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val resolver = context.contentResolver

    @Tool(name = "list_calendars", description = "Lists calendars available on the device. Requires calendar read permission.")
    fun listCalendars(): Map<String, Any> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) return readPermissionError()
        val calendars = mutableListOf<Map<String, Any>>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += mapOf(
                    "calendarId" to cursor.getLong(CALENDAR_ID_INDEX),
                    "displayName" to cursor.getString(CALENDAR_NAME_INDEX).orEmpty(),
                    "accountName" to cursor.getString(CALENDAR_ACCOUNT_INDEX).orEmpty(),
                    "isPrimary" to (cursor.getInt(CALENDAR_PRIMARY_INDEX) == 1),
                )
            }
        }
        return mapOf("success" to true, "calendars" to calendars)
    }

    @Tool(name = "get_upcoming_calendar_events", description = "Lists calendar events occurring in the requested number of upcoming days. Requires calendar read permission.")
    fun getUpcomingCalendarEvents(
        @Param("Number of upcoming days to query, from 1 to 31.") days: Int,
    ): Map<String, Any> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) return readPermissionError()
        val startMillis = System.currentTimeMillis()
        val appliedDays = days.coerceIn(1, MAXIMUM_QUERY_DAYS)
        val endMillis = startMillis + appliedDays * MILLIS_PER_DAY
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
        }.build()
        val events = mutableListOf<Map<String, Any>>()
        resolver.query(
            instancesUri,
            INSTANCE_PROJECTION,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && events.size < MAXIMUM_RETURNED_EVENTS) {
                events += mapOf(
                    "eventId" to cursor.getLong(INSTANCE_EVENT_ID_INDEX),
                    "calendarId" to cursor.getLong(INSTANCE_CALENDAR_ID_INDEX),
                    "title" to cursor.getString(INSTANCE_TITLE_INDEX).orEmpty(),
                    "startTimeMillis" to cursor.getLong(INSTANCE_BEGIN_INDEX),
                    "endTimeMillis" to cursor.getLong(INSTANCE_END_INDEX),
                    "allDay" to (cursor.getInt(INSTANCE_ALL_DAY_INDEX) == 1),
                )
            }
        }
        return mapOf(
            "success" to true,
            "days" to appliedDays,
            "events" to events,
        )
    }

    @Tool(name = "create_calendar_event", description = "Creates a calendar event in a selected device calendar. Requires calendar write permission.")
    fun createCalendarEvent(
        @Param("Calendar ID returned by listCalendars, represented as a decimal string.") calendarId: String,
        @Param("Event title.") title: String,
        @Param("Event start time in yyyymmddhhmmss format, interpreted in the device's local timezone.") startTimeMillis: String,
        @Param("Event end time in yyyymmddhhmmss format, interpreted in the device's local timezone. It must be later than the start time.") endTimeMillis: String,
        @Param("Optional event description.") description: String? = null,
    ): Map<String, Any> {
        val parsedCalendarId = calendarId.toLongOrNull()
            ?: return invalidNumberError("calendarId")
        val parsedStartTimeMillis = parseLocalDateTimeMillis(startTimeMillis)
            ?: return invalidTimeError("startTimeMillis")
        val parsedEndTimeMillis = parseLocalDateTimeMillis(endTimeMillis)
            ?: return invalidTimeError("endTimeMillis")
        if (parsedEndTimeMillis <= parsedStartTimeMillis) {
            return mapOf("success" to false, "error" to "endTimeMillis must be later than startTimeMillis.")
        }
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) return writePermissionError()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, parsedCalendarId)
            put(CalendarContract.Events.DTSTART, parsedStartTimeMillis)
            put(CalendarContract.Events.DTEND, parsedEndTimeMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = try {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (_: SecurityException) {
            return writePermissionError()
        } ?: return mapOf("success" to false, "error" to "Calendar provider rejected the insert.")
        return mapOf(
            "success" to true,
            "eventId" to ContentUris.parseId(uri),
            "uri" to uri.toString(),
        )
    }

    @Tool(name = "request_calendar_permissions", description = "Requests the calendar permissions required to read and write calendars.")
    fun requestCalendarPermissions(): Map<String, Any> = queue.request(
        "Grant calendar access",
        "Request permission to read and write your calendars for upcoming-event queries and direct event creation.",
        Intent(context, CalendarPermissionActivity::class.java),
    )

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun readPermissionError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "READ_CALENDAR permission is required. Call requestCalendarPermissions and grant calendar access.",
    )

    private fun writePermissionError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "WRITE_CALENDAR permission is required. Call requestCalendarPermissions and grant calendar access.",
    )

    private fun invalidNumberError(parameter: String): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "$parameter must be a valid decimal integer string.",
    )

    private fun invalidTimeError(parameter: String): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "$parameter must be a yyyymmddhhmmss string.",
    )

    private fun parseLocalDateTimeMillis(value: String): Long? = try {
        LocalDateTime.parse(value, LOCAL_DATE_TIME_FORMATTER)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        const val MAXIMUM_QUERY_DAYS = 31
        const val MAXIMUM_RETURNED_EVENTS = 50

        val LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        const val CALENDAR_ID_INDEX = 0
        const val CALENDAR_NAME_INDEX = 1
        const val CALENDAR_ACCOUNT_INDEX = 2
        const val CALENDAR_PRIMARY_INDEX = 3

        val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        const val INSTANCE_EVENT_ID_INDEX = 0
        const val INSTANCE_CALENDAR_ID_INDEX = 1
        const val INSTANCE_TITLE_INDEX = 2
        const val INSTANCE_BEGIN_INDEX = 3
        const val INSTANCE_END_INDEX = 4
        const val INSTANCE_ALL_DAY_INDEX = 5
    }
}
