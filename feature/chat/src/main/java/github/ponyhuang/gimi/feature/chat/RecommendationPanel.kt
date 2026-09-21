package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.ui.theme.LocalUserBubbleColors

/**
 * 空会话中的全局推荐列表：单列右对齐的紧凑小气泡，点击即把 prompt 作为用户消息发出。
 *
 * 条目与 ChatMessageBubble 的 USER 气泡同构（LocalUserBubbleColors + shapes.large +
 * 320dp 宽度上限），用"发出这条消息"的视觉语义替代旧版满宽大药丸。
 *
 * 面板本身不带外边距：横向内缩由调用方给出（跟随胶囊的收放动画对齐边缘），
 * 纵向留白由列表的 contentPadding 负责，这样面板可以贴着输入胶囊向上堆叠。
 */
@Composable
internal fun RecommendationPanel(
    recommendations: List<AgentRecommendation>,
    onRecommendationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp), // 与 ChatMessageBubble 用户气泡的横向内缩对齐
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_recommendations_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recommendations.forEach { recommendation ->
            RecommendationAction(
                recommendation = recommendation,
                onClick = { onRecommendationClick(recommendation.prompt) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationAction(
    recommendation: AgentRecommendation,
    onClick: () -> Unit,
) {
    // 气泡正文截断到两行，长按弹浮层补全。TooltipBox 在 PointerEventPass.Initial
    // 里消费长按事件，抬手不会连带触发按钮的点击发送。
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        // 用 PlainTooltip 而不是 RichTooltip：后者的容器色和卡片几乎同色，浮层会糊在列表里。
        // 反色气泡能一眼分清层级；默认 200dp 太窄，放宽到能容下整段 prompt。
        tooltip = {
            PlainTooltip(maxWidth = 320.dp) {
                Text(text = recommendation.prompt)
            }
        },
        state = rememberTooltipState(isPersistent = true),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        // 与用户消息气泡同构的紧凑右对齐小气泡：宽度随文字收缩而非铺满一行，
        // 配色/圆角直接复用 LocalUserBubbleColors + shapes.large，保证"点击即发送"的语义一致。
        Surface(
            onClick = onClick,
            modifier = Modifier
                .semantics { contentDescription = recommendation.prompt }
                .testTag("recommendation-${recommendation.id}"),
            shape = MaterialTheme.shapes.large,
            color = LocalUserBubbleColors.current.container,
            contentColor = LocalUserBubbleColors.current.onContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = recommendation.category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = recommendation.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val RecommendationCategory.icon: ImageVector
    get() = when (this) {
        RecommendationCategory.REASONING -> Icons.Default.Psychology
        RecommendationCategory.VISION -> Icons.Default.ImageSearch
        RecommendationCategory.RESEARCH -> Icons.Default.Search
        RecommendationCategory.WRITING -> Icons.Default.Create
        RecommendationCategory.DEVICE -> Icons.Default.Devices
        RecommendationCategory.PRODUCTIVITY -> Icons.Default.TaskAlt
        RecommendationCategory.GENERAL -> Icons.Default.AutoAwesome
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RecommendationPanelPreview() {
    AsssistantaiTheme {
        RecommendationPanel(
            recommendations = listOf(
                AgentRecommendation(
                    id = "rec-1",
                    prompt = "查询今晚上海的天气情况",
                    category = RecommendationCategory.DEVICE,
                ),
                AgentRecommendation(
                    id = "rec-2",
                    prompt = "帮我规划从当前位置到上海虹桥火车站的驾车路线",
                    category = RecommendationCategory.RESEARCH,
                ),
                AgentRecommendation(
                    id = "rec-3",
                    prompt = "把这份会议记录整理成按负责人分组的待办事项清单并标注优先级",
                    category = RecommendationCategory.PRODUCTIVITY,
                ),
            ),
            onRecommendationClick = {},
        )
    }
}
