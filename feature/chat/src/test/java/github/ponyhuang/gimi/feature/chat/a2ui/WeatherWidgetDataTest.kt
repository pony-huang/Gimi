package github.ponyhuang.gimi.feature.chat.a2ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetDataTest {
    @Test
    fun parsesTodayOnlyAndClampsMeasurements() {
        val data = WeatherWidgetData.fromA2ui(
            weatherPayload(days = 18, hours = 30),
        )

        requireNotNull(data)
        assertEquals(1, data.daily.size)
        assertEquals(100, data.current.humidityPercent)
        assertEquals(0, data.current.precipitationPercent)
    }

    @Test
    fun rejectsPayloadWithoutUsableDailyForecast() {
        val payload = weatherPayload(days = 0, hours = 2)

        assertNull(WeatherWidgetData.fromA2ui(payload))
    }

    @Test
    fun keepsProviderTextWhenConditionCodeIsUnknown() {
        val payload = weatherPayload(days = 1, hours = 0).toMutableMap()
        payload["current"] = (payload.getValue("current") as Map<*, *>).toMutableMap().apply {
            this["conditionCode"] = "sandstorm"
            this["conditionText"] = "扬沙"
        }

        val data = WeatherWidgetData.fromA2ui(payload)

        requireNotNull(data)
        assertEquals(WeatherConditionCode.OTHER, data.current.conditionCode)
        assertEquals("扬沙", data.current.conditionText)
    }

    @Test
    fun invalidTodayNeverFallsBackToALaterDay() {
        val payload = weatherPayload(days = 2, hours = 0).toMutableMap()
        val days = (payload.getValue("daily") as List<*>).toMutableList()
        days[0] = mapOf("conditionCode" to "rain")
        payload["daily"] = days

        val data = WeatherWidgetData.fromA2ui(payload)

        assertNull(data)
    }

    private fun weatherPayload(days: Int, hours: Int): Map<String, Any?> {
        val start = LocalDate.of(2026, 10, 18)
        return mapOf(
            "location" to "上海",
            "updatedAt" to "14:20",
            "current" to mapOf(
                "conditionCode" to "clear",
                "temperatureC" to 28,
                "feelsLikeC" to 30,
                "humidityPercent" to 140,
                "windDirection" to "东南",
                "windSpeedKph" to 12,
                "precipitationPercent" to -5,
                "uvIndex" to 6,
                "airQuality" to "良",
            ),
            "hourly" to List(hours) { index ->
                mapOf(
                    "timeLabel" to "${15 + index % 6}时",
                    "conditionCode" to "clear",
                    "temperatureC" to 28 - index % 4,
                    "precipitationPercent" to index,
                )
            },
            "daily" to List(days) { index ->
                mapOf(
                    "date" to start.plusDays(index.toLong()).toString(),
                    "conditionCode" to "partly_cloudy",
                    "highC" to 31 - index / 3,
                    "lowC" to 23 - index / 4,
                    "precipitationPercent" to index * 5,
                    "sunrise" to "05:58",
                    "sunset" to "17:24",
                )
            },
        )
    }
}
