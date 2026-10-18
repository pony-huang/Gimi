package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.time.LocalDate

@Preview(name = "天气 · 一天 · 浅色", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun WeatherTodayLightPreview() {
    WeatherPreviewCanvas(darkTheme = false) {
        WeatherTodayCard(data = weatherPreviewData())
    }
}

@Preview(
    name = "天气 · 一天 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WeatherTodayDarkPreview() {
    WeatherPreviewCanvas(darkTheme = true) {
        WeatherTodayCard(data = weatherPreviewData())
    }
}

@Preview(name = "天气 · 七天 · 浅色", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun WeatherForecastLightPreview() {
    WeatherPreviewCanvas(darkTheme = false) {
        WeatherForecastCard(data = weatherPreviewData(days = 7))
    }
}

@Preview(
    name = "天气 · 七天 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WeatherForecastDarkPreview() {
    WeatherPreviewCanvas(darkTheme = true) {
        WeatherForecastCard(data = weatherPreviewData(days = 7))
    }
}

@Preview(
    name = "天气 · 十五天 · 窄屏大字号",
    showBackground = true,
    widthDp = 280,
    heightDp = 520,
    fontScale = 1.3f,
)
@Composable
private fun WeatherForecastFifteenDayPreview() {
    WeatherPreviewCanvas(darkTheme = false) {
        WeatherForecastCard(data = weatherPreviewData(days = 15))
    }
}

@Composable
private fun WeatherPreviewCanvas(darkTheme: Boolean, content: @Composable () -> Unit) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

private fun weatherPreviewData(days: Int = 7): WeatherCardData {
    val conditions = listOf(
        WeatherConditionCode.CLEAR,
        WeatherConditionCode.PARTLY_CLOUDY,
        WeatherConditionCode.CLOUDY,
        WeatherConditionCode.RAIN,
        WeatherConditionCode.THUNDERSTORM,
        WeatherConditionCode.PARTLY_CLOUDY,
    )
    val startDate = LocalDate.of(2026, 10, 18)
    return WeatherCardData(
        location = "上海",
        updatedAt = "14:20",
        current = WeatherCurrent(
            conditionCode = WeatherConditionCode.CLEAR,
            conditionText = null,
            temperatureC = 28,
            feelsLikeC = 30,
            humidityPercent = 68,
            windDirection = "东南",
            windSpeedKph = 12,
            precipitationPercent = 20,
            uvIndex = 6,
            airQuality = "良",
        ),
        hourly = listOf(
            WeatherHour("15时", WeatherConditionCode.CLEAR, null, 29, 10),
            WeatherHour("16时", WeatherConditionCode.PARTLY_CLOUDY, null, 28, 15),
            WeatherHour("17时", WeatherConditionCode.PARTLY_CLOUDY, null, 27, 20),
            WeatherHour("18时", WeatherConditionCode.CLOUDY, null, 26, 25),
            WeatherHour("19时", WeatherConditionCode.CLOUDY, null, 25, 25),
            WeatherHour("20时", WeatherConditionCode.RAIN, null, 24, 45),
            WeatherHour("21时", WeatherConditionCode.RAIN, null, 24, 50),
        ),
        daily = List(days.coerceAtLeast(1)) { index ->
            val condition = conditions[index % conditions.size]
            WeatherDay(
                date = startDate.plusDays(index.toLong()).toString(),
                conditionCode = condition,
                conditionText = null,
                highC = (31 - index / 2).coerceAtLeast(19),
                lowC = (24 - index / 3).coerceAtLeast(14),
                precipitationPercent = when (condition) {
                    WeatherConditionCode.THUNDERSTORM -> 80
                    WeatherConditionCode.RAIN -> 60
                    WeatherConditionCode.CLOUDY -> 30
                    else -> 10
                },
                sunrise = "05:58",
                sunset = "17:24",
            )
        },
    )
}
