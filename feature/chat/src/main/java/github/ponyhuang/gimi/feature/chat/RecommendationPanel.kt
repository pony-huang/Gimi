package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory

/** 空会话中的全局推荐列表，整卡是单一可访问点击目标。 */
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
            RecommendationCard(
                recommendation = recommendation,
                onClick = { onRecommendationClick(recommendation.prompt) },
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: AgentRecommendation,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = recommendation.category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = recommendation.category.label(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = recommendation.prompt,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun RecommendationCategory.label(): String = stringResource(
    when (this) {
        RecommendationCategory.REASONING -> R.string.chat_recommendation_category_reasoning
        RecommendationCategory.VISION -> R.string.chat_recommendation_category_vision
        RecommendationCategory.RESEARCH -> R.string.chat_recommendation_category_research
        RecommendationCategory.WRITING -> R.string.chat_recommendation_category_writing
        RecommendationCategory.DEVICE -> R.string.chat_recommendation_category_device
        RecommendationCategory.PRODUCTIVITY -> R.string.chat_recommendation_category_productivity
        RecommendationCategory.GENERAL -> R.string.chat_recommendation_category_general
    },
)

