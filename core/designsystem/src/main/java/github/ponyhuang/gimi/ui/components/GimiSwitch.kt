package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * M3 规范化的紧凑开关：44×26dp 轨道保留 48dp 触控区域；整行处理点击时传入空回调，避免重复语义。
 *
 * - 未选中态：`surfaceContainerHighest` 轨道 + `1.5dp outline` 描边（>= 3:1 对比度）+ `14dp outline` 拇指；
 * - 选中态：`primary` 轨道（无外描边）+ `18dp onPrimary` 拇指（带 M3 拇指尺寸缩放过渡）。
 */
@Composable
fun GimiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "switchPosition")
    val thumbRadiusDp by animateDpAsState(if (checked) 9.dp else 7.dp, label = "switchThumbRadius")
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> scheme.onSurface.copy(alpha = 0.12f)
            !enabled -> scheme.surfaceContainerHighest.copy(alpha = 0.12f)
            checked -> scheme.primary
            else -> scheme.surfaceContainerHighest
        },
        label = "switchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            checked -> Color.Transparent
            !enabled -> scheme.onSurface.copy(alpha = 0.12f)
            else -> scheme.outline
        },
        label = "switchBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> scheme.surface
            !enabled -> scheme.onSurface.copy(alpha = 0.38f)
            checked -> scheme.onPrimary
            else -> scheme.outline
        },
        label = "switchThumb",
    )
    Box(
        modifier = modifier.size(48.dp).then(
            if (onCheckedChange == null) Modifier else Modifier.toggleable(
                value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp, 26.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            if (borderColor.alpha > 0f) {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = radius,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            val progress = if (layoutDirection == LayoutDirection.Rtl) 1f - position else position
            val startX = 13.dp.toPx()
            val endX = size.width - 13.dp.toPx()
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusDp.toPx(),
                center = Offset(startX + (endX - startX) * progress, size.height / 2),
            )
        }
    }
}

@Preview(name = "紧凑开关 · 浅色", showBackground = true)
@Preview(name = "紧凑开关 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GimiSwitchPreview() {
    GimiTheme {
        androidx.compose.foundation.layout.Row {
            GimiSwitch(true, {})
            GimiSwitch(false, {})
            GimiSwitch(true, {}, enabled = false)
            GimiSwitch(false, {}, enabled = false)
        }
    }
}
