package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 上下聊天胶囊共用 M3 中性容器色，浅色接近白色，深色随主题适配。 */
@Composable
internal fun chatCapsuleColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

/**
 * 胶囊描边：容器色在浅色主题下接近页面背景，需要 1dp 中性描边才能勾出边界。
 * 取 outline（浅色 #D4D4D4 / 深色 #3C3C41）半透明，浮卡观感更接近纸面细线。
 * 顶栏操作胶囊、输入胶囊、待发送面板与回到底部按钮共用，避免上下边界表现不一致。
 */
@Composable
internal fun chatCapsuleBorder(): BorderStroke = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
)
