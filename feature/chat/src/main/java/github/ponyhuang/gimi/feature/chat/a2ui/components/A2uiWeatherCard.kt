package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class WeatherKind {
    Sun,
    CloudSun,
    Cloud,
    Fog,
    Drizzle,
    Rain,
    Snow,
    Storm,
}

private data class WeatherDetailMetric(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

private data class DailyForecastEntry(
    val label: String,
    val icon: WeatherKind,
    val precip: Double?,
    val min: Double,
    val max: Double,
)

private val SunAmber = Color(0xFFF59E0B)
private val SunYellow = Color(0xFFFBBF24)
private val CloudSlate = Color(0xFF94A3B8)
private val RainSky = Color(0xFF38BDF8)
private val SnowIce = Color(0xFF7DD3FC)
private val StormViolet = Color(0xFFA78BFA)
private val PrecipLight = Color(0xFF0284C7)
private val PrecipDark = Color(0xFF7DD3FC)
private val RangeGradient = listOf(Color(0xFF38BDF8), Color(0xFFA3E635), Color(0xFFFB923C))

@Composable
internal fun WeatherCard(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val unit = values.text("unit").takeIf { it == "F" } ?: "C"
    val condition = values.text("condition")
    val kind = resolveWeatherKind(values.text("icon"), condition)

    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val wellBg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow
    val dividerColor = colors.outlineVariant.copy(alpha = 0.65f)
    val precipColor = if (darkTheme) PrecipDark else PrecipLight

    val details = buildList {
        values.number("feels")?.let { feels ->
            add(WeatherDetailMetric(Icons.Rounded.DeviceThermostat, stringResource(R.string.chat_a2ui_feels), "${feels.roundToInt()}°"))
        }
        values.number("humidity")?.let { humidity ->
            add(WeatherDetailMetric(Icons.Rounded.WaterDrop, stringResource(R.string.chat_a2ui_humidity), "${humidity.roundToInt()}%"))
        }
        values.number("wind")?.let { wind ->
            val speedUnit = if (unit == "F") "mph" else "km/h"
            add(WeatherDetailMetric(Icons.Rounded.Air, stringResource(R.string.chat_a2ui_wind), "${wind.roundToInt()} $speedUnit"))
        }
    }

    val hourly = values.rows("hourly").filter { it.number("temp") != null }
    val daily = values.rows("daily").mapNotNull { row ->
        val min = row.number("min") ?: return@mapNotNull null
        val max = row.number("max") ?: return@mapNotNull null
        DailyForecastEntry(
            label = row.text("label"),
            icon = resolveWeatherKind(row.text("icon"), ""),
            precip = row.number("precip")?.takeIf { it > 0.0 },
            min = min,
            max = max,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 右上角天气氛围径向光晕，提示当前天气冷暖基调
            AmbientWeatherGlow(kind = kind, modifier = Modifier.matchParentSize())

            Column(modifier = Modifier.fillMaxWidth()) {
                WeatherHeroSection(
                    location = values.text("location"),
                    temp = values.number("temp"),
                    unit = unit,
                    condition = condition,
                    high = values.number("high"),
                    low = values.number("low"),
                    kind = kind,
                )

                if (details.isNotEmpty()) {
                    WeatherDetailsRow(details = details, wellBg = wellBg)
                }

                if (hourly.isNotEmpty()) {
                    HorizontalDivider(color = dividerColor)
                    WeatherHourlyStrip(
                        hourly = hourly,
                        precipColor = precipColor,
                        activeSlotBg = colors.onSurface.copy(alpha = 0.05f),
                        activeSlotBorder = colors.outlineVariant.copy(alpha = 0.85f),
                    )
                }

                if (daily.isNotEmpty()) {
                    HorizontalDivider(color = dividerColor)
                    WeatherDailyList(
                        daily = daily,
                        precipColor = precipColor,
                        trackColor = colors.onSurface.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientWeatherGlow(kind: WeatherKind, modifier: Modifier = Modifier) {
    val tint = when (kind) {
        WeatherKind.Sun -> SunAmber.copy(alpha = 0.24f)
        WeatherKind.CloudSun -> SunAmber.copy(alpha = 0.19f)
        WeatherKind.Cloud, WeatherKind.Fog -> CloudSlate.copy(alpha = 0.16f)
        WeatherKind.Drizzle, WeatherKind.Rain -> RainSky.copy(alpha = 0.22f)
        WeatherKind.Snow -> SnowIce.copy(alpha = 0.22f)
        WeatherKind.Storm -> StormViolet.copy(alpha = 0.22f)
    }
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val radius = 150.dp.toPx()
        val center = Offset(x = size.width - 24.dp.toPx(), y = 12.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun WeatherHeroSection(
    location: String,
    temp: Double?,
    unit: String,
    condition: String,
    high: Double?,
    low: Double?,
    kind: WeatherKind,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            if (location.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = temp?.let { "${it.roundToInt()}" } ?: "--",
                    style = TextStyle(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 54.sp,
                        letterSpacing = (-2).sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurface,
                )
                Text(
                    text = "°$unit",
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurfaceVariant,
                )
            }

            if (condition.isNotBlank()) {
                Text(
                    text = condition,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
            }

            val hlParts = listOfNotNull(
                high?.let { stringResource(R.string.chat_a2ui_weather_high, "${it.roundToInt()}°") },
                low?.let { stringResource(R.string.chat_a2ui_weather_low, "${it.roundToInt()}°") },
            )
            if (hlParts.isNotEmpty()) {
                Text(
                    text = hlParts.joinToString("  ·  "),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.onSurfaceVariant,
                )
            }
        }

        WeatherVectorIcon(
            kind = kind,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun WeatherDetailsRow(details: List<WeatherDetailMetric>, wellBg: Color) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        details.forEach { metric ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(wellBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = metric.icon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherHourlyStrip(
    hourly: List<CardValues>,
    precipColor: Color,
    activeSlotBg: Color,
    activeSlotBorder: Color,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .boundedHorizontalScroll()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hourly.forEachIndexed { index, slot ->
            val isFirst = index == 0
            val slotShape = RoundedCornerShape(10.dp)
            val slotModifier = Modifier
                .widthIn(min = 52.dp)
                .then(
                    if (isFirst) {
                        Modifier
                            .background(activeSlotBg, slotShape)
                            .border(1.dp, activeSlotBorder, slotShape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp)

            Column(
                modifier = slotModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = slot.text("label"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isFirst) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (isFirst) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
                WeatherVectorIcon(
                    kind = resolveWeatherKind(slot.text("icon"), ""),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${slot.number("temp")?.roundToInt() ?: "--"}°",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurface,
                )
                val precip = slot.number("precip")?.takeIf { it > 0.0 }
                // 无降水时保留占位高度，避免逐小时卡片基线上下错位
                Text(
                    text = "${precip?.roundToInt() ?: 0}%",
                    modifier = Modifier.alpha(if (precip != null) 1f else 0f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = precipColor,
                )
            }
        }
    }
}

@Composable
private fun WeatherDailyList(
    daily: List<DailyForecastEntry>,
    precipColor: Color,
    trackColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    val globalMin = daily.minOf { it.min }
    val globalMax = daily.maxOf { it.max }
    val span = (globalMax - globalMin).coerceAtLeast(1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        daily.forEach { day ->
            val leftFraction = ((day.min - globalMin) / span).toFloat().coerceIn(0f, 1f)
            val widthFraction = ((day.max - day.min) / span).toFloat().coerceAtLeast(0.05f)
                .coerceAtMost(1f - leftFraction)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = day.label,
                    modifier = Modifier.width(44.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    WeatherVectorIcon(kind = day.icon, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = day.precip?.let { "${it.roundToInt()}%" }.orEmpty(),
                    modifier = Modifier.width(34.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = precipColor,
                )
                Text(
                    text = "${day.min.roundToInt()}°",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp),
                ) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = corner,
                    )
                    val startX = size.width * leftFraction
                    val fillWidth = (size.width * widthFraction).coerceAtLeast(size.height)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = RangeGradient,
                            startX = 0f,
                            endX = size.width,
                        ),
                        topLeft = Offset(startX, 0f),
                        size = Size(fillWidth, size.height),
                        cornerRadius = corner,
                    )
                }
                Text(
                    text = "${day.max.roundToInt()}°",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun resolveWeatherKind(icon: String, condition: String): WeatherKind {
    when (icon.trim().lowercase()) {
        "sun", "clear" -> return WeatherKind.Sun
        "cloud-sun", "partly-cloudy" -> return WeatherKind.CloudSun
        "cloud", "cloudy", "overcast" -> return WeatherKind.Cloud
        "fog", "mist", "haze" -> return WeatherKind.Fog
        "drizzle", "light-rain" -> return WeatherKind.Drizzle
        "rain", "showers" -> return WeatherKind.Rain
        "snow", "sleet" -> return WeatherKind.Snow
        "storm", "thunderstorm" -> return WeatherKind.Storm
    }
    val normalized = condition.lowercase()
    return when {
        "雷" in normalized || "storm" in normalized || "thunder" in normalized -> WeatherKind.Storm
        "雪" in normalized || "snow" in normalized -> WeatherKind.Snow
        "毛毛雨" in normalized || "小雨" in normalized || "drizzle" in normalized -> WeatherKind.Drizzle
        "雨" in normalized || "rain" in normalized || "shower" in normalized -> WeatherKind.Rain
        "雾" in normalized || "霾" in normalized || "fog" in normalized || "mist" in normalized -> WeatherKind.Fog
        "多云" in normalized || "partly" in normalized -> WeatherKind.CloudSun
        "阴" in normalized || "云" in normalized || "cloud" in normalized || "overcast" in normalized -> WeatherKind.Cloud
        "晴" in normalized || "sun" in normalized || "clear" in normalized -> WeatherKind.Sun
        else -> WeatherKind.CloudSun
    }
}

@Composable
private fun WeatherVectorIcon(kind: WeatherKind, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.08f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            WeatherKind.Sun -> drawSun(stroke)
            WeatherKind.CloudSun -> drawCloudSun(stroke)
            WeatherKind.Cloud -> drawCloud(CloudSlate, stroke)
            WeatherKind.Fog -> drawFog(stroke)
            WeatherKind.Drizzle -> drawDrizzle(stroke)
            WeatherKind.Rain -> drawRain(stroke)
            WeatherKind.Snow -> drawSnow(stroke)
            WeatherKind.Storm -> drawStorm(stroke)
        }
    }
}

private fun DrawScope.drawSun(stroke: Stroke) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = size.minDimension * 0.17f
    drawCircle(color = SunAmber, radius = r, center = Offset(cx, cy), style = stroke)
    val inner = size.minDimension * 0.30f
    val outer = size.minDimension * 0.40f
    for (i in 0 until 8) {
        val angle = i * (Math.PI / 4.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = SunAmber,
            start = Offset(cx + dx * inner, cy + dy * inner),
            end = Offset(cx + dx * outer, cy + dy * outer),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCloudSun(stroke: Stroke) {
    val w = size.width
    val h = size.height
    val sunCenter = Offset(w * 0.58f, h * 0.34f)
    val sunR = size.minDimension * 0.15f
    drawArc(
        color = SunYellow,
        startAngle = 210f,
        sweepAngle = 195f,
        useCenter = false,
        topLeft = Offset(sunCenter.x - sunR, sunCenter.y - sunR),
        size = Size(sunR * 2f, sunR * 2f),
        style = stroke,
    )
    val rayAngles = listOf(-90.0, -45.0, 0.0, -135.0)
    val inner = size.minDimension * 0.25f
    val outer = size.minDimension * 0.32f
    rayAngles.forEach { deg ->
        val rad = Math.toRadians(deg)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        drawLine(
            color = SunYellow,
            start = Offset(sunCenter.x + dx * inner, sunCenter.y + dy * inner),
            end = Offset(sunCenter.x + dx * outer, sunCenter.y + dy * outer),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
    drawCloudShape(
        color = SunAmber,
        stroke = stroke,
        topFraction = 0.45f,
        bottomFraction = 0.86f,
        leftFraction = 0.14f,
        rightFraction = 0.78f,
    )
}

private fun DrawScope.drawCloud(color: Color, stroke: Stroke) {
    drawCloudShape(
        color = color,
        stroke = stroke,
        topFraction = 0.28f,
        bottomFraction = 0.76f,
        leftFraction = 0.14f,
        rightFraction = 0.86f,
    )
}

private fun DrawScope.drawFog(stroke: Stroke) {
    drawCloudShape(
        color = CloudSlate,
        stroke = stroke,
        topFraction = 0.18f,
        bottomFraction = 0.62f,
        leftFraction = 0.16f,
        rightFraction = 0.84f,
    )
    val w = size.width
    val h = size.height
    drawLine(CloudSlate, Offset(w * 0.25f, h * 0.74f), Offset(w * 0.75f, h * 0.74f), stroke.width, StrokeCap.Round)
    drawLine(CloudSlate, Offset(w * 0.32f, h * 0.86f), Offset(w * 0.68f, h * 0.86f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawRain(stroke: Stroke) {
    drawCloudShape(
        color = CloudSlate,
        stroke = stroke,
        topFraction = 0.16f,
        bottomFraction = 0.60f,
        leftFraction = 0.16f,
        rightFraction = 0.84f,
    )
    val w = size.width
    val h = size.height
    listOf(0.34f to 0.70f, 0.50f to 0.76f, 0.66f to 0.70f).forEach { (x, y) ->
        drawLine(
            color = RainSky,
            start = Offset(w * x, h * y),
            end = Offset(w * (x - 0.04f), h * (y + 0.16f)),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawDrizzle(stroke: Stroke) {
    drawCloudShape(
        color = CloudSlate,
        stroke = stroke,
        topFraction = 0.16f,
        bottomFraction = 0.60f,
        leftFraction = 0.16f,
        rightFraction = 0.84f,
    )
    val w = size.width
    val h = size.height
    listOf(0.34f to 0.72f, 0.50f to 0.79f, 0.66f to 0.72f).forEach { (x, y) ->
        drawLine(
            color = RainSky,
            start = Offset(w * x, h * y),
            end = Offset(w * (x - 0.02f), h * (y + 0.08f)),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(stroke: Stroke) {
    drawCloudShape(
        color = CloudSlate,
        stroke = stroke,
        topFraction = 0.16f,
        bottomFraction = 0.60f,
        leftFraction = 0.16f,
        rightFraction = 0.84f,
    )
    val w = size.width
    val h = size.height
    val dotR = stroke.width * 0.65f
    listOf(
        0.34f to 0.72f, 0.50f to 0.79f, 0.66f to 0.72f,
        0.40f to 0.86f, 0.60f to 0.86f,
    ).forEach { (x, y) ->
        drawCircle(color = SnowIce, radius = dotR, center = Offset(w * x, h * y))
    }
}

private fun DrawScope.drawStorm(stroke: Stroke) {
    drawCloudShape(
        color = CloudSlate,
        stroke = stroke,
        topFraction = 0.16f,
        bottomFraction = 0.58f,
        leftFraction = 0.16f,
        rightFraction = 0.84f,
    )
    val w = size.width
    val h = size.height
    val bolt = Path().apply {
        moveTo(w * 0.54f, h * 0.56f)
        lineTo(w * 0.41f, h * 0.74f)
        lineTo(w * 0.53f, h * 0.74f)
        lineTo(w * 0.43f, h * 0.90f)
    }
    drawPath(bolt, color = StormViolet, style = stroke)
}

private fun DrawScope.drawCloudShape(
    color: Color,
    stroke: Stroke,
    topFraction: Float,
    bottomFraction: Float,
    leftFraction: Float,
    rightFraction: Float,
) {
    val w = size.width
    val h = size.height
    val left = w * leftFraction
    val right = w * rightFraction
    val top = h * topFraction
    val bottom = h * bottomFraction
    val spanW = right - left
    val spanH = bottom - top

    val path = Path().apply {
        moveTo(left + spanW * 0.22f, bottom)
        lineTo(right - spanW * 0.18f, bottom)
        cubicTo(
            right + spanW * 0.06f, bottom,
            right + spanW * 0.06f, bottom - spanH * 0.48f,
            right - spanW * 0.20f, bottom - spanH * 0.48f,
        )
        cubicTo(
            right - spanW * 0.24f, top - spanH * 0.08f,
            left + spanW * 0.24f, top - spanH * 0.06f,
            left + spanW * 0.22f, bottom - spanH * 0.42f,
        )
        cubicTo(
            left - spanW * 0.05f, bottom - spanH * 0.42f,
            left - spanW * 0.05f, bottom,
            left + spanW * 0.22f, bottom,
        )
        close()
    }
    drawPath(path, color = color, style = stroke)
}
