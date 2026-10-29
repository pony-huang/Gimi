package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "当天天气 · 浅色", widthDp = 390, showBackground = true)
@Composable
private fun WeatherTodayLightPreview() = WeatherPreview(false, WeatherConditionCode.CLEAR)

@Preview(name = "当天天气 · 深色晴间多云", widthDp = 390, showBackground = true)
@Composable
private fun WeatherTodayDarkPreview() = WeatherPreview(true, WeatherConditionCode.PARTLY_CLOUDY)

@Preview(name = "当天天气 · 窄屏大字号", widthDp = 280, fontScale = 1.3f, showBackground = true)
@Composable
private fun WeatherTodayNarrowPreview() = WeatherPreview(false, WeatherConditionCode.THUNDERSTORM)

@Preview(name = "当天天气 · 缺少指标", widthDp = 390, showBackground = true)
@Composable
private fun WeatherTodayMissingPreview() = WeatherPreview(false, WeatherConditionCode.CLOUDY, missing = true)

@Composable
private fun WeatherPreview(dark: Boolean, condition: WeatherConditionCode, missing: Boolean = false) {
    AsssistantaiTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                WeatherTodayWidget(WeatherWidgetData(
                    location = "上海", updatedAt = if (missing) null else "14:20",
                    current = WeatherCurrent(condition, null, 28,
                        humidityPercent = if (missing) null else 68,
                        windSpeedKph = if (missing) null else 12,
                        precipitationPercent = if (missing) null else 20),
                    daily = listOf(WeatherDay("2026-10-25", condition, null, 31, 24, null)),
                ))
            }
        }
    }
}
