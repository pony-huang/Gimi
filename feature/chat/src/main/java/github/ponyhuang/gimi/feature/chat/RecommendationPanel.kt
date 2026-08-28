package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** 空会话中的全局推荐列表，每项都是可直接发起任务的扩展浮动操作按钮。 */
@Composable
internal fun RecommendationPanel(
    recommendations: List<AgentRecommendation>,
    onRecommendationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

@Composable
private fun RecommendationAction(
    recommendation: AgentRecommendation,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .semantics { contentDescription = recommendation.prompt }
            .testTag("recommendation-${recommendation.id}"),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = {
            Icon(
                imageVector = recommendation.category.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        text = {
            Text(
                text = recommendation.prompt,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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
