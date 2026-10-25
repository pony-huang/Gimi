package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R

/** 聊天中的当天天气卡片；不承载逐时或多日预报。 */
internal object ChatA2uiWeatherToday : A2uiComponent {
    override val name = "WeatherToday"
    override val description = "Compact native weather card for today only: location, observation time, " +
        "current conditions, daily high/low, precipitation probability, humidity and wind speed. " +
        "Use the required weather property. Never use for future-day or multi-day forecasts."
    override val properties = listOf(WeatherA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(WeatherA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val data = properties.bind(WeatherA2uiProperty) ?: return
        WeatherTodayWidget(data, modifier)
    }
}

@Composable
internal fun WeatherTodayWidget(data: WeatherWidgetData, modifier: Modifier = Modifier) {
    val today = data.daily.single()
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.chat_weather_location_today, data.location),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    data.updatedAt?.let {
                        Text(stringResource(R.string.chat_weather_updated_at, it), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Text(data.current.conditionCode.displayText(data.current.conditionText), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.chat_weather_temperature_value, data.current.temperatureC), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.chat_weather_high_low, today.highC, today.lowC), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                WeatherIllustration(data.current.conditionCode, Modifier.size(88.dp))
            }
            // 缺失指标不显示占位卡片，也不把未知值误当成零。
            if (data.current.precipitationPercent != null || data.current.humidityPercent != null || data.current.windSpeedKph != null) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.current.precipitationPercent?.let {
                        WeatherMetric(Icons.Rounded.Umbrella, stringResource(R.string.chat_weather_metric_precipitation), stringResource(R.string.chat_weather_percent_value, it), Modifier.weight(1f))
                    }
                    data.current.humidityPercent?.let {
                        WeatherMetric(Icons.Rounded.WaterDrop, stringResource(R.string.chat_weather_metric_humidity), stringResource(R.string.chat_weather_percent_value, it), Modifier.weight(1f))
                    }
                    data.current.windSpeedKph?.let {
                        WeatherMetric(Icons.Rounded.Air, stringResource(R.string.chat_weather_metric_wind), stringResource(R.string.chat_weather_wind_speed, it), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WeatherConditionCode.displayText(fallback: String?): String = when (this) {
    WeatherConditionCode.CLEAR -> stringResource(R.string.chat_weather_condition_clear)
    WeatherConditionCode.PARTLY_CLOUDY ->
        stringResource(R.string.chat_weather_condition_partly_cloudy)
    WeatherConditionCode.CLOUDY -> stringResource(R.string.chat_weather_condition_cloudy)
    WeatherConditionCode.RAIN -> stringResource(R.string.chat_weather_condition_rain)
    WeatherConditionCode.THUNDERSTORM ->
        stringResource(R.string.chat_weather_condition_thunderstorm)
    WeatherConditionCode.SNOW -> stringResource(R.string.chat_weather_condition_snow)
    WeatherConditionCode.FOG -> stringResource(R.string.chat_weather_condition_fog)
    WeatherConditionCode.WIND -> stringResource(R.string.chat_weather_condition_wind)
    WeatherConditionCode.OTHER -> fallback?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.chat_weather_condition_unknown)
}
