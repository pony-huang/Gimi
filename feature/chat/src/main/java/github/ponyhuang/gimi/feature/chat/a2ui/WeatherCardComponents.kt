package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.Yellow80
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** 一天天气卡片：共享 `weather` 入参，展示当前观测、太阳轨迹、指标和接下来数小时。 */
internal object ChatA2uiWeatherToday : A2uiComponent {
    override val name = "WeatherToday"
    override val description =
        "Detailed native weather card for today. Use as the root when the user asks for a " +
            "today-only detailed view. Set the required weather property to the complete shared " +
            "weather object or a binding, and never rebuild this layout from Card/Text/List."
    override val properties = listOf(WeatherA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(
        properties: androidx.a2ui.compose.runtime.A2uiComponentProperties,
    ): Boolean = properties.bind(WeatherA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: androidx.a2ui.compose.runtime.A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(WeatherA2uiProperty) ?: return
        WeatherTodayCard(data = data, modifier = modifier)
    }
}

/** N 天天气卡片：共享同一 `weather` 入参，最多展示 15 天，窄屏横向滚动。 */
internal object ChatA2uiWeatherForecast : A2uiComponent {
    override val name = "WeatherForecast"
    override val description =
        "Native multi-day weather card. Use as the root for any 2-15 day forecast, including a " +
            "four-day result. Set the required weather property to the complete shared weather " +
            "object or a binding, and never rebuild this layout from Card/Text/List."
    override val properties = listOf(WeatherA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(
        properties: androidx.a2ui.compose.runtime.A2uiComponentProperties,
    ): Boolean = properties.bind(WeatherA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: androidx.a2ui.compose.runtime.A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(WeatherA2uiProperty) ?: return
        WeatherForecastCard(data = data, modifier = modifier)
    }
}

@Composable
internal fun WeatherTodayCard(
    data: WeatherCardData,
    modifier: Modifier = Modifier,
) {
    val today = data.daily.first()
    val feelsLike = data.current.feelsLikeC ?: data.current.temperatureC
    val unavailable = stringResource(R.string.chat_weather_not_available)
    WeatherCardFrame(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.location,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    WeatherTag(text = stringResource(R.string.chat_weather_today))
                }
                data.updatedAt?.let {
                    Text(
                        text = stringResource(R.string.chat_weather_updated_at, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WeatherOrb(condition = data.current.conditionCode)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.chat_weather_temperature_value,
                    data.current.temperatureC,
                ),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-4).sp,
                ),
            )
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = data.current.conditionCode.displayText(data.current.conditionText),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.chat_weather_feels_high_low,
                        feelsLike,
                        today.highC,
                        today.lowC,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!today.sunrise.isNullOrBlank() && !today.sunset.isNullOrBlank()) {
            SunPath(
                observedAt = data.updatedAt,
                sunrise = today.sunrise,
                sunset = today.sunset,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherMetric(
                icon = Icons.Rounded.Thermostat,
                label = stringResource(R.string.chat_weather_metric_feels_like),
                value = stringResource(R.string.chat_weather_temperature_value, feelsLike),
                modifier = Modifier.weight(1f),
            )
            WeatherMetric(
                icon = Icons.Rounded.WaterDrop,
                label = stringResource(R.string.chat_weather_metric_humidity),
                value = data.current.humidityPercent?.let {
                    stringResource(R.string.chat_weather_percent_value, it)
                } ?: unavailable,
                modifier = Modifier.weight(1f),
            )
            WeatherMetric(
                icon = Icons.Rounded.Air,
                label = stringResource(R.string.chat_weather_metric_wind),
                value = windText(
                    direction = data.current.windDirection,
                    speedKph = data.current.windSpeedKph,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherMetric(
                icon = Icons.Rounded.Umbrella,
                label = stringResource(R.string.chat_weather_metric_precipitation),
                value = data.current.precipitationPercent?.let {
                    stringResource(R.string.chat_weather_percent_value, it)
                } ?: unavailable,
                modifier = Modifier.weight(1f),
            )
            WeatherMetric(
                icon = Icons.Rounded.Visibility,
                label = stringResource(R.string.chat_weather_metric_uv),
                value = data.current.uvIndex?.toString() ?: unavailable,
                modifier = Modifier.weight(1f),
            )
            WeatherMetric(
                icon = Icons.Rounded.WbTwilight,
                label = stringResource(R.string.chat_weather_metric_air_quality),
                value = data.current.airQuality ?: unavailable,
                modifier = Modifier.weight(1f),
            )
        }

        if (data.hourly.isNotEmpty()) {
            WeatherSectionDivider(label = stringResource(R.string.chat_weather_next_hours))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data.hourly.take(7).forEach { hour ->
                    HourlyWeatherItem(hour)
                }
            }
        }
    }
}

@Composable
internal fun WeatherForecastCard(
    data: WeatherCardData,
    modifier: Modifier = Modifier,
) {
    val days = data.daily.take(WeatherCardData.MAX_FORECAST_DAYS)
    val today = days.first()
    val globalLowC = days.minOf { it.lowC }
    val globalHighC = days.maxOf { it.highC }
    val rainyDays = days.count { (it.precipitationPercent ?: 0) >= 50 }
    WeatherCardFrame(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = data.location,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.chat_weather_forecast_range, days.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeatherConditionBadge(
                condition = data.current.conditionCode,
                text = data.current.conditionCode.displayText(data.current.conditionText),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_weather_temperature_value, today.highC),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp,
                ),
            )
            Text(
                text = stringResource(R.string.chat_weather_low_temperature, today.lowC),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.chat_weather_rainy_days, rainyDays),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }

        WeatherSectionDivider(label = stringResource(R.string.chat_weather_daily))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (days.size <= 7) 4.dp else 8.dp
            val itemWidth = if (days.size <= 7) {
                ((maxWidth - gap * (days.size - 1).toFloat()) / days.size.toFloat())
                    .coerceIn(44.dp, 76.dp)
            } else {
                56.dp
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                days.forEachIndexed { index, day ->
                    DailyWeatherItem(
                        day = day,
                        dayLabel = weatherDayLabel(index = index, date = day.date),
                        dateLabel = weatherDateLabel(day.date),
                        isToday = index == 0,
                        width = itemWidth,
                        globalLowC = globalLowC,
                        globalHighC = globalHighC,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RangeSummary(
                label = stringResource(R.string.chat_weather_average_high),
                value = stringResource(
                    R.string.chat_weather_temperature_value,
                    days.map { it.highC }.average().roundToInt(),
                ),
            )
            RangeSummary(
                label = stringResource(R.string.chat_weather_lowest),
                value = stringResource(R.string.chat_weather_temperature_value, globalLowC),
            )
            RangeSummary(
                label = stringResource(R.string.chat_weather_temperature_spread),
                value = stringResource(
                    R.string.chat_weather_temperature_value,
                    globalHighC - globalLowC,
                ),
            )
        }
    }
}

@Composable
private fun WeatherCardFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                val step = 36.dp.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += step
                }
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun WeatherTag(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun WeatherOrb(condition: WeatherConditionCode) {
    val accent = weatherAccent(condition)
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = accent.copy(alpha = 0.11f),
                radius = size.minDimension * 0.49f,
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = size.minDimension * 0.38f,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = accent.copy(alpha = 0.42f),
                radius = 2.dp.toPx(),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
            )
        }
        Icon(
            imageVector = condition.icon(),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun SunPath(observedAt: String?, sunrise: String, sunset: String) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    val accent = Yellow80
    val progress = sunProgress(observedAt, sunrise, sunset)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            val start = Offset(size.width * 0.06f, size.height * 0.82f)
            val end = Offset(size.width * 0.94f, size.height * 0.82f)
            val control = Offset(size.width * 0.5f, -size.height * 0.04f)
            val path = Path().apply {
                moveTo(start.x, start.y)
                quadraticBezierTo(control.x, control.y, end.x, end.y)
            }
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.25f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 6.dp.toPx()),
                    ),
                ),
            )
            val t = progress
            val oneMinusT = 1f - t
            val marker = Offset(
                x = oneMinusT * oneMinusT * start.x +
                    2f * oneMinusT * t * control.x +
                    t * t * end.x,
                y = oneMinusT * oneMinusT * start.y +
                    2f * oneMinusT * t * control.y +
                    t * t * end.y,
            )
            drawCircle(accent.copy(alpha = 0.16f), radius = 8.dp.toPx(), center = marker)
            drawCircle(accent, radius = 4.dp.toPx(), center = marker)
            drawLine(
                color = line,
                start = start,
                end = end,
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2.dp.toPx(), 8.dp.toPx()),
                ),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.chat_weather_sunrise, sunrise),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.chat_weather_sunset, sunset),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HourlyWeatherItem(hour: WeatherHour) {
    Column(
        modifier = Modifier.width(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = hour.timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = hour.conditionCode.icon(),
            contentDescription = null,
            tint = weatherAccent(hour.conditionCode),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.chat_weather_temperature_value, hour.temperatureC),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = hour.precipitationPercent?.let {
                    stringResource(R.string.chat_weather_percent_value, it)
                } ?: stringResource(R.string.chat_weather_not_available),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DailyWeatherItem(
    day: WeatherDay,
    dayLabel: String,
    dateLabel: String,
    isToday: Boolean,
    width: Dp,
    globalLowC: Int,
    globalHighC: Int,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp),
        color = if (isToday) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
            Icon(
                imageVector = day.conditionCode.icon(),
                contentDescription = null,
                tint = weatherAccent(day.conditionCode),
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(22.dp),
            )
            Text(
                text = stringResource(R.string.chat_weather_temperature_value, day.highC),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            TemperatureRangeBar(
                lowC = day.lowC,
                highC = day.highC,
                globalLowC = globalLowC,
                globalHighC = globalHighC,
                modifier = Modifier
                    .width(8.dp)
                    .height(32.dp),
            )
            Text(
                text = stringResource(R.string.chat_weather_temperature_value, day.lowC),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = day.precipitationPercent?.let {
                        stringResource(R.string.chat_weather_percent_value, it)
                    } ?: stringResource(R.string.chat_weather_not_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TemperatureRangeBar(
    lowC: Int,
    highC: Int,
    globalLowC: Int,
    globalHighC: Int,
    modifier: Modifier = Modifier,
) {
    val range = (globalHighC - globalLowC).coerceAtLeast(1).toFloat()
    val start = ((globalHighC - highC) / range).coerceIn(0f, 1f)
    val end = ((globalHighC - lowC) / range).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val radius = size.width / 2f
        drawLine(
            color = track,
            start = Offset(radius, 0f),
            end = Offset(radius, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = fill,
            start = Offset(radius, size.height * start),
            end = Offset(radius, size.height * end),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WeatherConditionBadge(condition: WeatherConditionCode, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = condition.icon(),
                contentDescription = null,
                tint = weatherAccent(condition),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeatherSectionDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun RangeSummary(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun windText(direction: String?, speedKph: Int?): String = when {
    direction != null && speedKph != null -> stringResource(
        R.string.chat_weather_wind_direction_speed,
        direction,
        speedKph,
    )
    speedKph != null -> stringResource(R.string.chat_weather_wind_speed, speedKph)
    direction != null -> direction
    else -> stringResource(R.string.chat_weather_not_available)
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

@Composable
private fun WeatherConditionCode.icon(): ImageVector = when (this) {
    WeatherConditionCode.CLEAR -> Icons.Rounded.WbSunny
    WeatherConditionCode.PARTLY_CLOUDY -> Icons.Rounded.Cloud
    WeatherConditionCode.CLOUDY -> Icons.Rounded.Cloud
    WeatherConditionCode.RAIN -> Icons.Rounded.WaterDrop
    WeatherConditionCode.THUNDERSTORM -> Icons.Rounded.Thunderstorm
    WeatherConditionCode.SNOW -> Icons.Rounded.AcUnit
    WeatherConditionCode.FOG -> Icons.Rounded.Visibility
    WeatherConditionCode.WIND -> Icons.Rounded.Air
    WeatherConditionCode.OTHER -> Icons.Rounded.Cloud
}

@Composable
private fun weatherAccent(condition: WeatherConditionCode): Color =
    if (condition == WeatherConditionCode.CLEAR) {
        Yellow80
    } else {
        MaterialTheme.colorScheme.primary
    }

@Composable
private fun weatherDayLabel(index: Int, date: String?): String {
    if (index == 0) return stringResource(R.string.chat_weather_today)
    if (index == 1) return stringResource(R.string.chat_weather_tomorrow)
    val locale = LocalConfiguration.current.locales[0]
    return date.toLocalDateOrNull()
        ?.dayOfWeek
        ?.getDisplayName(TextStyle.SHORT, locale)
        ?: stringResource(R.string.chat_weather_day_index, index + 1)
}

@Composable
private fun weatherDateLabel(date: String?): String {
    val locale = LocalConfiguration.current.locales[0]
    val parsed = date.toLocalDateOrNull() ?: return ""
    return parsed.format(DateTimeFormatter.ofPattern("M/d", locale))
}

private fun String?.toLocalDateOrNull(): LocalDate? {
    val value = this?.trim()?.take(10)?.takeIf(String::isNotEmpty) ?: return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
}

private fun sunProgress(observedAt: String?, sunrise: String, sunset: String): Float {
    val observed = parseClockMinutes(observedAt) ?: return 0.5f
    val start = parseClockMinutes(sunrise) ?: return 0.5f
    val end = parseClockMinutes(sunset) ?: return 0.5f
    if (end <= start) return 0.5f
    return ((observed - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

private fun parseClockMinutes(value: String?): Int? {
    val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val hours = text.substringBeforeLast(":").toIntOrNull() ?: return null
    val minutes = text.substringAfterLast(":", missingDelimiterValue = "").toIntOrNull()
        ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}
