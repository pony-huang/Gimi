package github.ponyhuang.asssistantai.agent.tools.systems

import android.os.SystemClock
import com.google.adk.kt.annotations.Tool
import java.time.DateTimeException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Gets the current UTC time supplied by Android's network and GNSS time services. */
@Singleton
class TimeTool @Inject constructor() {

    @Tool(
        name = "get_current_time",
        description = "Gets the current UTC time synchronized by the device's Network location provider. Use when network time is required.",
        requireConfirmation = false,
        isLongRunning = false,
    )
    fun getCurrentTime(): Map<String, Any> = try {
        val epochMillis = SystemClock.currentNetworkTimeClock().millis()
        mapOf(
            "success" to true,
            "timeSource" to "network",
            "epochMillis" to epochMillis,
            "utcTime" to Instant.ofEpochMilli(epochMillis).toString(),
        )
    } catch (exception: DateTimeException) {
        mapOf(
            "success" to false,
            "timeSource" to "network",
            "error" to "Network time is not available on this device.",
        )
    }

    @Tool(
        name = "get_current_gnss_time",
        description = "Gets the current UTC time synchronized by the device's GNSS receiver. Use when satellite-based time is required and network time is unavailable or untrusted.",
        requireConfirmation = false,
        isLongRunning = false,
    )
    fun getCurrentGnssTime(): Map<String, Any> = try {
        val epochMillis = SystemClock.currentGnssTimeClock().millis()
        mapOf(
            "success" to true,
            "timeSource" to "gnss",
            "epochMillis" to epochMillis,
            "utcTime" to Instant.ofEpochMilli(epochMillis).toString(),
        )
    } catch (exception: DateTimeException) {
        mapOf(
            "success" to false,
            "timeSource" to "gnss",
            "error" to "GNSS time is not available on this device.",
        )
    }
}