package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.feature.chat.ZoomableCoilImagePreviewDialog
import github.ponyhuang.gimi.feature.chat.R
import kotlinx.coroutines.launch

/**
 * 原生方案卡组：Agent 的候选方案以单卡视口横向翻阅。
 *
 * 适用场景是任何需要比较多个候选的内容：旅行路线、空间或视觉设计、餐饮、
 * 社媒笔记、地点搜索到的商店与地点、学习课程、购物礼物以及 A/B/C 提案。
 * 组件只提供稳定的接受/拒绝语义；
 * 按钮和链接文案由 Agent 根据当前业务语境提供。
 *
 * 所有决定通过 server event 交给主 Agent：option_accept / option_reject
 * （context 携带 itemId、index、title、actionLabel），翻完自动发 option_exhausted。
 */
internal class ChatA2uiOptionDeck(
    private val openUrl: (String) -> Unit,
) : A2uiComponent {
    override val name = "OptionDeck"
    override val description =
        "Horizontal deck for comparing candidate plans, recommendations, social posts, nearby stores or " +
            "places one at a time, such as travel itineraries, social notes, design directions, dining " +
            "options, map search results, courses, gifts or proposals. The deck provides stable " +
            "accept/reject events while acceptLabel, rejectLabel and each optional item link label " +
            "carry the domain-specific wording. Use as the root and never rebuild it from Card/Text/List."
    override val properties = listOf(OptionDeckA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(
        properties: A2uiComponentProperties,
    ): Boolean = properties.bind(OptionDeckA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(OptionDeckA2uiProperty) ?: return
        OptionDeck(
            data = data,
            onDecision = { item, index, accepted ->
                val actionLabel = if (accepted) data.acceptLabel else data.rejectLabel
                dispatchAction(optionDecisionAction(item, index, accepted, actionLabel))
            },
            onExhausted = { accepted, rejected ->
                dispatchAction(optionExhaustedAction(accepted, rejected, data.items.size))
            },
            onOpenUrl = openUrl,
            modifier = modifier,
        )
    }
}
/** 构造标准 A2UI server event，把单张方案的决定交给主 Agent 路由到对应业务工具。 */
internal fun optionDecisionAction(
    item: OptionDeckItem,
    index: Int,
    accepted: Boolean,
    actionLabel: String,
): Map<String, Any?> = mapOf(
    "event" to mapOf(
        "name" to if (accepted) "option_accept" else "option_reject",
        "context" to mapOf(
            "itemId" to item.id,
            "index" to index,
            "title" to item.title,
            "actionLabel" to actionLabel,
        ),
    ),
)

/** 构造翻完事件，携带本轮统计供 Agent 决定追问、推荐下一轮或执行业务操作。 */
internal fun optionExhaustedAction(
    acceptedCount: Int,
    rejectedCount: Int,
    total: Int,
): Map<String, Any?> = mapOf(
    "event" to mapOf(
        "name" to "option_exhausted",
        "context" to mapOf(
            "accepted" to acceptedCount,
            "rejected" to rejectedCount,
            "total" to total,
        ),
    ),
)

/**
 * 方案卡组：横向分页浏览，按钮负责提交接受/拒绝。
 *
 * 状态以 [data] 为 key：Agent 原位更新卡组（例如下一轮候选）时自动回到第一张。
 * 预览参数仅用于展示已决定与空态，不进入 A2UI 契约。
 */
@Composable
internal fun OptionDeck(
    data: OptionDeckData,
    onDecision: (item: OptionDeckItem, index: Int, accepted: Boolean) -> Unit,
    onExhausted: (acceptedCount: Int, rejectedCount: Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    initiallyAccepted: Set<String> = emptySet(),
    initiallyRejected: Set<String> = emptySet(),
    initiallyExhausted: Boolean = false,
) {
    val items = data.items
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )
    var acceptedCount by remember(data) { mutableIntStateOf(initiallyAccepted.size) }
    var rejectedCount by remember(data) { mutableIntStateOf(initiallyRejected.size) }
    var decidedIds by remember(data) {
        mutableStateOf(initiallyAccepted + initiallyRejected)
    }
    var exhausted by remember(data) { mutableStateOf(initiallyExhausted) }
    var zoomedItem by remember(data) { mutableStateOf<OptionDeckItem?>(null) }
    val scope = rememberCoroutineScope()

    fun decide(accepted: Boolean) {
        if (exhausted || items.isEmpty()) return
        val index = pagerState.currentPage
        val current = items.getOrNull(index) ?: return
        if (current.id in decidedIds) return
        decidedIds = decidedIds + current.id
        if (accepted) acceptedCount += 1 else rejectedCount += 1
        onDecision(current, index, accepted)
        if (index >= items.lastIndex) {
            exhausted = true
            onExhausted(acceptedCount, rejectedCount)
        } else {
            scope.launch { pagerState.animateScrollToPage(index + 1) }
        }
    }

    val progress = if (exhausted || items.isEmpty()) {
        1f
    } else {
        ((pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f) /
            items.size.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.title ?: stringResource(R.string.chat_option_deck_default_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!exhausted) {
                Text(
                    text = stringResource(
                        R.string.chat_option_deck_position,
                        pagerState.currentPage + 1,
                        items.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OptionDeckProgressTrack(fraction = progress)
        if (exhausted) {
            OptionDeckEmptyState(
                acceptedCount = acceptedCount,
                rejectedCount = rejectedCount,
                onRestart = {
                    acceptedCount = 0
                    rejectedCount = 0
                    decidedIds = emptySet()
                    exhausted = false
                    scope.launch { pagerState.scrollToPage(0) }
                },
                modifier = Modifier.heightIn(min = 430.dp),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                key = { index -> items[index].id },
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                val item = items[index]
                OptionDeckCard(
                    item = item,
                    acceptLabel = data.acceptLabel,
                    rejectLabel = data.rejectLabel,
                    onAccept = { decide(accepted = true) },
                    onReject = { decide(accepted = false) },
                    onOpenImage = { zoomedItem = item },
                    onOpenLink = { item.link?.let { link -> onOpenUrl(link.url) } },
                )
            }
            Text(
                text = stringResource(R.string.chat_option_deck_browse_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    zoomedItem?.let { item ->
        if (!item.imageUrl.isNullOrBlank()) {
            ZoomableCoilImagePreviewDialog(
                model = item.imageUrl,
                imageKey = item.id,
                contentDescription = stringResource(R.string.chat_option_deck_item_image),
                onDismiss = { zoomedItem = null },
            )
        } else {
            OptionDeckFallbackImageDialog(
                item = item,
                onDismiss = { zoomedItem = null },
            )
        }
    }
}

/** 单张方案卡：图片、两行标题、四行摘要、标签、业务链接和场景化决定按钮。 */
@Composable
private fun OptionDeckCard(
    item: OptionDeckItem,
    acceptLabel: String,
    rejectLabel: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OptionDeckArtwork(
                item = item,
                onClick = onOpenImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    item.meta?.let { meta ->
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                item.link?.let { link ->
                    TextButton(
                        onClick = onOpenLink,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 2.dp,
                            vertical = 0.dp,
                        ),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = link.label,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = rejectLabel,
                            modifier = Modifier.padding(start = 7.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = acceptLabel,
                            modifier = Modifier.padding(start = 7.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 卡面图片区域；图片和占位图均可点击进入放大预览。 */
@Composable
private fun OptionDeckArtwork(
    item: OptionDeckItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canPreview = !item.imageUrl.isNullOrBlank() || !item.emoji.isNullOrBlank()
    val openImageLabel = stringResource(R.string.chat_option_deck_open_image)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            )
            .clickable(
                enabled = canPreview,
                onClickLabel = openImageLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = stringResource(R.string.chat_option_deck_item_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!item.emoji.isNullOrBlank()) {
            Text(text = item.emoji, fontSize = 78.sp)
        } else {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Fullscreen,
                contentDescription = openImageLabel,
                modifier = Modifier.padding(9.dp).size(19.dp),
            )
        }
    }
}

/** Emoji/通用占位图没有远程位图，使用同一全屏遮罩提供大图反馈。 */
@Composable
private fun OptionDeckFallbackImageDialog(
    item: OptionDeckItem,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.emoji ?: "✦",
                fontSize = 176.sp,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_option_deck_close_image),
                )
            }
        }
    }
}

/** 卡组顶部细进度条，随横向翻页连续推进。 */
@Composable
private fun OptionDeckProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = fraction.coerceIn(0.04f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(color = MaterialTheme.colorScheme.primary),
        )
    }
}

/** 翻完的空态：统计本轮决定并允许重开一轮（纯本地行为，不回传事件）。 */
@Composable
private fun OptionDeckEmptyState(
    acceptedCount: Int,
    rejectedCount: Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🍀", fontSize = 44.sp)
        Text(
            text = stringResource(R.string.chat_option_deck_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.chat_option_deck_empty_summary,
                acceptedCount,
                rejectedCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRestart) {
            Text(text = stringResource(R.string.chat_option_deck_empty_restart))
        }
    }
}
