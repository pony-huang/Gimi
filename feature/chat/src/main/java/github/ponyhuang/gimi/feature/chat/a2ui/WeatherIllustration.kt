package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.Yellow80
import kotlin.math.cos
import kotlin.math.sin

/** 以不透明色块叠绘太阳与云朵，避免半透明圆形交叠产生接缝；天气图形与文案一致。 */
@Composable
internal fun WeatherIllustration(condition: WeatherConditionCode, modifier: Modifier = Modifier) {
    val rainy = condition == WeatherConditionCode.RAIN || condition == WeatherConditionCode.THUNDERSTORM
    val partlyCloudy = condition == WeatherConditionCode.PARTLY_CLOUDY
    val cloudy = condition == WeatherConditionCode.CLOUDY
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    if (condition in setOf(WeatherConditionCode.SNOW, WeatherConditionCode.FOG, WeatherConditionCode.WIND, WeatherConditionCode.OTHER)) {
        val icon = when (condition) {
            WeatherConditionCode.SNOW -> Icons.Rounded.AcUnit
            WeatherConditionCode.FOG -> Icons.Rounded.Visibility
            WeatherConditionCode.WIND -> Icons.Rounded.Air
            else -> Icons.Rounded.Cloud
        }
        Icon(icon, null, modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
        return
    }
    val sun = Yellow80
    val cloud = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest
    val rain = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        if (!rainy && !cloudy) {
            val center = if (partlyCloudy) Offset(65f * unit, 32f * unit) else Offset(50f * unit, 48f * unit)
            drawCircle(sun, 21f * unit, center)
            repeat(8) { index ->
                val angle = index * Math.PI / 4
                drawLine(
                    sun,
                    Offset(center.x + cos(angle).toFloat() * 29f * unit, center.y + sin(angle).toFloat() * 29f * unit),
                    Offset(center.x + cos(angle).toFloat() * 34f * unit, center.y + sin(angle).toFloat() * 34f * unit),
                    3f * unit,
                    StrokeCap.Round,
                )
            }
        }
        if (rainy || partlyCloudy || cloudy) {
            drawCircle(cloud, 20f * unit, Offset(48f * unit, 35f * unit))
            drawCircle(cloud, 15f * unit, Offset(68f * unit, 47f * unit))
            drawCircle(cloud, 14f * unit, Offset(28f * unit, 49f * unit))
            drawRoundRect(cloud, Offset(24f * unit, 43f * unit), Size(49f * unit, 20f * unit), androidx.compose.ui.geometry.CornerRadius(10f * unit))
        }
        if (rainy) {
            if (condition == WeatherConditionCode.THUNDERSTORM) {
                val lightning = Path().apply {
                    moveTo(53f * unit, 57f * unit)
                    lineTo(42f * unit, 74f * unit)
                    lineTo(51f * unit, 74f * unit)
                    lineTo(44f * unit, 89f * unit)
                    lineTo(64f * unit, 68f * unit)
                    lineTo(54f * unit, 68f * unit)
                    close()
                }
                drawPath(lightning, sun)
            }
            repeat(2) { index ->
                val x = (29f + index * 45f) * unit
                drawLine(rain, Offset(x, 72f * unit), Offset(x - 5f * unit, 83f * unit), 4f * unit, StrokeCap.Round)
            }
        }
    }
}
