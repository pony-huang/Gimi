package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** 上下聊天胶囊共用中性色：浅色使用纯白表面，深色保持现有表面层次。 */
@Composable
internal fun chatCapsuleColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() > 0.5f) {
        colors.surfaceContainerLowest
    } else {
        colors.surface
    }
}

/** 细描边让白色胶囊在聊天背景上仍有清晰边界。 */
@Composable
internal fun chatCapsuleBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)
