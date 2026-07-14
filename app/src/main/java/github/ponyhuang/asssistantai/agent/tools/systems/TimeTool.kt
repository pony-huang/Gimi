package github.ponyhuang.asssistantai.agent.tools.systems

import com.google.adk.kt.annotations.Tool
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Gets the current local time on the device. */
@Singleton
class TimeTool @Inject constructor() {

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
