package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 紧凑开关：36×22dp 轨道保留 48dp 触控区域；整行处理点击时传入空回调，避免重复语义。 */
@Composable
fun GimiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "switchPosition")
    val track by animateColorAsState(
        (if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            .copy(alpha = if (enabled) 1f else 0.38f),
        label = "switchTrack",
    )
    val thumb = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.38f)
    Box(
        modifier = modifier.size(48.dp).then(
            if (onCheckedChange == null) Modifier else Modifier.toggleable(
                value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp, 22.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            val progress = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 1f - position else position
            drawCircle(thumb, radius = 8.dp.toPx(), center = Offset(11.dp.toPx() + 14.dp.toPx() * progress, size.height / 2))
        }
    }
}

@Preview(name = "紧凑开关 · 浅色", showBackground = true)
@Preview(name = "紧凑开关 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GimiSwitchPreview() {
    AsssistantaiTheme {
        androidx.compose.foundation.layout.Row {
            GimiSwitch(true, {})
            GimiSwitch(false, {})
            GimiSwitch(true, {}, enabled = false)
            GimiSwitch(false, {}, enabled = false)
        }
    }
}
