package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.feature.chat.R

private val VisibleNewsHeight = 360.dp
private const val VISIBLE_NEWS_COUNT = 5

/** 新闻/资讯原生列表；固定展示 5 条，其余在卡片内部滚动。 */
internal class ChatA2uiNewsList(
    private val openUrl: (String) -> Unit,
) : A2uiComponent {
    override val name = "NewsList"
    override val description =
        "Native news and information digest. Use as the root for news searches, latest headlines, " +
            "or current-event briefings. Shows five articles and scrolls internally for more. " +
            "Set the required news property from tool results; never rebuild this list from Card/Text/List."
    override val properties = listOf(NewsListA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(NewsListA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(NewsListA2uiProperty) ?: return
        NewsListCard(
            data = data,
            onOpenArticle = { url -> openUrl(url) },
            modifier = modifier,
        )
    }
}

@Composable
internal fun NewsListCard(
    data: NewsListData,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = data.items.take(NewsListData.MAX_ITEMS)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NewsListHeader(
                heading = data.heading?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.chat_news_default_heading),
                updatedAt = data.updatedAt,
                count = items.size,
                icon = data.icon,
            )
            ScrollableNewsList(
                items = items,
                onOpenArticle = onOpenArticle,
            )
        }
    }
}

@Composable
private fun NewsListHeader(
    heading: String,
    updatedAt: String?,
    count: Int,
    icon: NewsListIcon,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon.imageVector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            updatedAt?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = stringResource(R.string.chat_news_updated_at, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(R.string.chat_news_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun NewsListIcon.imageVector() = when (this) {
    NewsListIcon.NEWSPAPER -> Icons.Rounded.Newspaper
    NewsListIcon.ARTICLE -> Icons.Rounded.Article
    NewsListIcon.GLOBE -> Icons.Rounded.Public
    NewsListIcon.TRENDING -> Icons.Rounded.TrendingUp
    NewsListIcon.CAMPAIGN -> Icons.Rounded.Campaign
    NewsListIcon.BOLT -> Icons.Rounded.Bolt
}

@Composable
private fun ScrollableNewsList(
    items: List<NewsArticle>,
    onOpenArticle: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val listHeight = if (items.size > VISIBLE_NEWS_COUNT) {
        Modifier.height(VisibleNewsHeight)
    } else {
        Modifier
    }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = listHeight
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = if (items.size > VISIBLE_NEWS_COUNT) 5.dp else 0.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp, end = 2.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    )
                }
                NewsRow(
                    item = item,
                    onOpenArticle = onOpenArticle,
                )
            }
        }
        if (items.size > VISIBLE_NEWS_COUNT) {
            NewsScrollIndicator(
                scrollState = scrollState,
                visibleFraction = VISIBLE_NEWS_COUNT.toFloat() / items.size.toFloat(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.88f)
                    .width(3.dp),
            )
        }
    }
}

@Composable
private fun NewsRow(
    item: NewsArticle,
    onOpenArticle: (String) -> Unit,
) {
    val url = item.url?.takeIf(String::isNotBlank)
    val openLabel = stringResource(R.string.chat_news_open_article, item.title)
    val baseRowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
    val rowShape = RoundedCornerShape(13.dp)
    val rowColor = Color.Transparent
    if (url != null) {
        Surface(
            onClick = { onOpenArticle(url) },
            modifier = baseRowModifier.semantics { contentDescription = openLabel },
            shape = rowShape,
            color = rowColor,
        ) {
            NewsRowContent(
                item = item,
                canOpen = true,
                openLabel = openLabel,
                onOpenArticle = { onOpenArticle(url) },
            )
        }
    } else {
        Surface(
            modifier = baseRowModifier,
            shape = rowShape,
            color = rowColor,
        ) {
            NewsRowContent(
                item = item,
                canOpen = false,
                openLabel = openLabel,
                onOpenArticle = {},
            )
        }
    }
}

@Composable
private fun NewsRowContent(
    item: NewsArticle,
    canOpen: Boolean,
    openLabel: String,
    onOpenArticle: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewsThumbnail(
            item = item,
            modifier = Modifier.size(52.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            NewsMeta(item = item)
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onOpenArticle,
            enabled = canOpen,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = openLabel,
                tint = if (canOpen) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun NewsMeta(item: NewsArticle) {
    val parts = listOfNotNull(
        item.category?.takeIf(String::isNotBlank),
        item.source?.takeIf(String::isNotBlank),
        item.timeLabel?.takeIf(String::isNotBlank),
    )
    if (parts.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(50),
                ),
        )
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                letterSpacing = 0.1.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewsScrollIndicator(
    scrollState: androidx.compose.foundation.ScrollState,
    visibleFraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    val thumbFraction = visibleFraction.coerceIn(0.18f, 0.72f)
    val scrollProgress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    } else {
        0f
    }
    Canvas(modifier) {
        val x = size.width / 2f
        drawLine(
            color = trackColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val thumbHeight = size.height * thumbFraction
        val thumbTop = (size.height - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
        drawLine(
            color = thumbColor,
            start = Offset(x, thumbTop),
            end = Offset(x, thumbTop + thumbHeight),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NewsThumbnail(item: NewsArticle, modifier: Modifier = Modifier) {
    val fallbackBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(fallbackBrush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Newspaper,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(21.dp),
        )
        item.imageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.chat_news_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
