package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 上下聊天胶囊共用 M3 中性容器色，浅色接近白色，深色随主题适配。 */
@Composable
internal fun chatCapsuleColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow
