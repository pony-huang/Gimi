package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory

/**
 * 空会话中的全局推荐列表，每项都是可直接发起任务的药丸按钮。
 *
 * 面板本身不带内边距：横向内缩由调用方给出（跟随胶囊的收放动画对齐边缘），
 * 纵向留白由列表的 contentPadding 负责，这样面板可以贴着输入胶囊向上堆叠。
 */
@Composable
internal fun RecommendationPanel(
    recommendations: List<AgentRecommendation>,
    onRecommendationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_recommendations_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
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
    // 卡片正文截断到两行，长按弹浮层补全。TooltipBox 在 PointerEventPass.Initial
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
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
    ) {
        // 手搓药丸而不用 ExtendedFloatingActionButton：后者锁死 56dp 高和 20dp 内边距，
        // 六张卡片叠起来太占版面。这里只保留它的配色与圆角。
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = recommendation.prompt }
                .testTag("recommendation-${recommendation.id}"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = recommendation.category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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
