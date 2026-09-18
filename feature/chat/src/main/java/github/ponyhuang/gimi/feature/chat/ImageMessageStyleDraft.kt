package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.ui.theme.LocalUserBubbleColors

/**
 * 图片消息样式设计草稿（仅 @Preview 用，评审通过后合入并删除本文件）。
 *
 * 方向：ChatGPT 式无边框大图 —— 纯图片消息不再套灰色气泡壳，
 * 图片以圆角卡片直接右对齐展示，保持原始宽高比；图文混合时图片贴气泡顶边。
 */

/** 单图卡片最大宽度：约占屏宽一半多，保证细节可辨又不喧宾夺主。 */
private val DraftImageMaxWidth = 220.dp

/** 图片卡片圆角：与消息列表其它圆角元素呼应，比 M3 large(28dp) 收敛。 */
private val DraftImageCorner = 16.dp

/** 多图网格单格尺寸：两列排布，外轮廓统一圆角。 */
private val DraftGridCell = 106.dp

/** 模拟照片色板，仅用于草稿展示宽高比效果。 */
private val DraftPhotoBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFB8C5D6), Color(0xFFE8D9C8), Color(0xFF9AA89B)),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** 纯图片用户消息：无气泡底，图片圆角卡片右对齐，宽高比原样保留。 */
@Composable
private fun DraftImageOnlyMessage(aspectRatioValue: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = DraftImageMaxWidth)
                .aspectRatio(aspectRatioValue)
                .clip(RoundedCornerShape(DraftImageCorner))
                .background(DraftPhotoBrush),
        )
    }
}

/** 多图（≥2）：两列网格缩略，单张仍可点开全屏预览。 */
@Composable
private fun DraftMultiImageMessage(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count.coerceAtMost(2)) {
                Box(
                    modifier = Modifier
                        .size(DraftGridCell)
                        .clip(RoundedCornerShape(DraftImageCorner))
                        .background(DraftPhotoBrush),
                )
            }
        }
    }
}

/** 图文混合：图片贴气泡顶边（无四周留白），文字在下方保留内边距。 */
@Composable
private fun DraftTextImageMessage(text: String, aspectRatioValue: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = LocalUserBubbleColors.current.container,
            contentColor = LocalUserBubbleColors.current.onContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                // Surface 自身已按气泡形状裁剪内容，图片直接铺到顶边即可。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatioValue.coerceAtLeast(1.4f))
                        .background(DraftPhotoBrush),
                )
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DraftImageOnlyPortraitLight() {
    AsssistantaiTheme { DraftImageOnlyMessage(3f / 4f) }
}

@Preview(showBackground = true)
@Composable
private fun DraftImageOnlyPortraitDark() {
    AsssistantaiTheme(darkTheme = true) { DraftImageOnlyMessage(3f / 4f) }
}

@Preview(showBackground = true)
@Composable
private fun DraftImageOnlyLandscapeLight() {
    AsssistantaiTheme { DraftImageOnlyMessage(4f / 3f) }
}

@Preview(showBackground = true)
@Composable
private fun DraftMultiImageLight() {
    AsssistantaiTheme { DraftMultiImageMessage(2) }
}

@Preview(showBackground = true)
@Composable
private fun DraftTextImageLight() {
    AsssistantaiTheme { DraftTextImageMessage("这张桌面照片里有什么？", 16f / 9f) }
}

@Preview(showBackground = true)
@Composable
private fun DraftTextImageDark() {
    AsssistantaiTheme(darkTheme = true) { DraftTextImageMessage("这张桌面照片里有什么？", 16f / 9f) }
}
