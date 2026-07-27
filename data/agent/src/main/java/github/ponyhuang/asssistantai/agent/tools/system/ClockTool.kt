package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import android.provider.AlarmClock
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Creates alarms and timers through the device's system clock application. */
@Singleton
class ClockTool @Inject constructor(
    private val queue: IntentActionQueue,
) {
    @Tool(name = "set_alarm", description = "Creates an alarm in the system clock for the requested time.", requireConfirmation = true)
    fun setAlarm(
        @Param("Alarm hour in 24-hour time, from 0 to 23.") hour: Int,
        @Param("Alarm minute, from 0 to 59.") minute: Int,
        @Param("Optional label shown by the system alarm app.") label: String? = null,
    ): Map<String, Any> {
        val appliedHour = hour.coerceIn(0, 23)
        val appliedMinute = minute.coerceIn(0, 59)
        val appliedLabel = label ?: "Assistant alarm"
        return queue.request(
            "Set alarm",
            "Set an alarm for %02d:%02d%s.".format(appliedHour, appliedMinute, appliedLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()),
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, appliedHour)
                .putExtra(AlarmClock.EXTRA_MINUTES, appliedMinute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, appliedLabel)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
        )
    }

    @Tool(name = "set_timer", description = "Starts a countdown timer in the system clock for the requested duration.", requireConfirmation = true)
    fun setTimer(
        @Param("Timer duration in seconds. Must be at least one second.") durationSeconds: Int,
        @Param("Optional label shown by the system timer app.") label: String? = null,
    ): Map<String, Any> {
        val appliedDuration = durationSeconds.coerceAtLeast(1)
        val appliedLabel = label ?: "Assistant timer"
        return queue.request(
            "Set timer",
            "Set a timer for $appliedDuration seconds${appliedLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}.",
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, appliedDuration)
                .putExtra(AlarmClock.EXTRA_MESSAGE, appliedLabel)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
        )
    }

    @Tool(name = "show_alarms", description = "Opens the system clock app's alarm list.")
    fun showAlarms(): Map<String, Any> = queue.request(
        "Show alarms",
        "Open the system clock's alarm list.",
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
    )

    @Tool(
        name = "get_current_time",
        description = "Gets the current local time on the device, including the configured timezone.",
        requireConfirmation = false,
        isLongRunning = false,
    )
    fun getCurrentTime(): Map<String, Any> {
        val now = ZonedDateTime.now()
        val dayOfWeek = now.dayOfWeek
        return mapOf(
            "timestamp" to now.toInstant().toEpochMilli(),
            "iso8601" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")),
            "date" to now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
            "time" to now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            "timezone" to now.zone.id,
            "dayOfWeek" to dayOfWeek.name,
            "isWeekend" to (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY),
        )
    }
}
