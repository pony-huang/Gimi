package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal fun composerActionContainerColor(): Color = Color.Transparent

internal fun composerActionIconColor(
    colorScheme: ColorScheme,
    enabled: Boolean,
): Color = if (enabled) {
    colorScheme.onSurface
} else {
    colorScheme.onSurface.copy(alpha = 0.38f)
}
