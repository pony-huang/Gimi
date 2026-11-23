package github.ponyhuang.gimi.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 搜索胶囊收起时的直径，也是标题行尾「搜索图标位」的宽度。 */
private val SearchCapsuleCollapsedSize = 40.dp

/** 搜索胶囊展开后的高度；与会话抽屉 DrawerSearchHeader 的展开输入框（56dp）同规格。 */
private val SearchCapsuleExpandedHeight = 56.dp

/** 标题行尾 ✕（关闭/退出搜索）占的宽度；胶囊展开后从它内部右侧收口。 */
private val SearchCloseSlot = 40.dp

/** 胶囊展开后输入文字与 ✕ 之间的留白。 */
private val SearchCloseGap = 4.dp

/**
 * 标题行与搜索胶囊共享的槽位高度。
 *
 * 72dp 让三者的中心天然重合：[GimiSheetTitleRow] 顶部对齐（自然高 60dp，内容带
 * 上 16/下 4 的不对称内边距，中心在 36dp），胶囊与 ✕ 在整槽位居中也是 36dp；
 * 同时给 56dp 的展开胶囊留出上下余量。
 */
private val SearchHeaderHeight = 72.dp

/**
 * 可搜索选择页骨架：标题行与搜索胶囊在同一槽位「原位变形」。
 *
 * 这是**抽屉内的一页**，不自带 [GimiSheetBase] 外壳；宿主把本骨架放进抽屉的
 * `AnimatedContent` 翻页里，与其它页面共用同一个模态抽屉。收起态（列表显示）是
 * 「返回 + 居中标题 + 搜索图标」的标题行，行尾不设关闭 ✕，由外壳遮罩/下拉/返回键统一关闭；
 * 点搜索图标后标题整行淡出、胶囊从图标位拉宽成整行输入框（内嵌退出 ✕），抽屉
 * 升高到主体上限、列表收起，搜索结果替换列表位置。胶囊内 ✕ 的语义是**退出
 * 搜索回到列表**。胶囊不渲染占位文案——
 * 「引导用户输入」由搜索态内容区的空态文案承担。
 *
 * 高度契约：搜索态主体撑满 [LocalGimiSheetBodyMaxHeight] 上限（结果为空也不塌），
 * 收起态贴内容高度；上限本身由宿主的 `height` 参数决定（配合 0.9f 分页上限即
 * 达到「展开到抽屉最大高度」）。抽屉外（预览）按不限高处理。
 *
 * 状态提升：`isSearching`/`query` 全部由调用方持有，本组件不保存业务状态；
 * 系统返回键「先收搜索再关抽屉」的 BackHandler 也由宿主接线。
 *
 * @param title 收起态标题行的标题。
 * @param isSearching 是否处于搜索态；由调用方提升持有。
 * @param query 搜索词，由调用方持有。
 * @param onQueryChange 搜索词变化回调。
 * @param onOpenSearch 点搜索图标请求进入搜索态。
 * @param onExitSearch 点胶囊内 ✕（或宿主返回键）请求退出搜索回到列表态。
 * @param onBack 收起态返回箭头：翻回上一级页面。
 * @param onClose 兼容性保留参数（收起态已移除关闭 ✕，抽屉由遮罩/下拉/返回键关闭）。
 * @param listContent 列表态内容；滚动容器自带 `heightIn(max)` 上限即可，骨架会再收一层。
 * @param searchContent 搜索态内容；骨架给它确定的固定高度，滚动容器撑满即可。
 */
@Composable
fun GimiSearchableSheetPage(
    title: String,
    isSearching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onExitSearch: () -> Unit,
    onBack: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    listContent: @Composable () -> Unit = {},
    searchContent: @Composable () -> Unit = {},
) {
    // 原位变形用 updateTransition 手动驱动：标题淡出、胶囊宽高与位移共享同一 progress，
    // 280ms 与会话抽屉 DrawerSearchHeader 同规格（FastOutSlowIn）。
    val transition = updateTransition(targetState = isSearching, label = "gimi-sheet-search-expand")
    val progress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 280, easing = FastOutSlowInEasing) },
        label = "gimi-sheet-search-progress",
    ) { searching -> if (searching) 1f else 0f }

    // 与会话抽屉 DrawerSearchHeader 同时序：过渡真正结算后才聚焦并弹键盘，
    // 中途收起不会聚焦；收起结算时收起键盘，避免键盘在退出后迟到或残留。
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(transition.currentState, transition.targetState, transition.isRunning) {
        when {
            !transition.isRunning && transition.targetState -> {
                inputFocus.requestFocus()
                keyboard?.show()
            }

            !transition.isRunning && !transition.targetState -> keyboard?.hide()
        }
    }

    val bodyMax = LocalGimiSheetBodyMaxHeight.current
    // 搜索态主体撑满上限（抽屉外预览不限高）；列表态贴内容，上限收住滚动容器。
    val contentMax = when {
        bodyMax.isSpecified && bodyMax >= SearchHeaderHeight -> bodyMax - SearchHeaderHeight
        else -> bodyMax
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SearchExpandHeader(
            title = title,
            isSearching = isSearching,
            query = query,
            onQueryChange = onQueryChange,
            onOpenSearch = onOpenSearch,
            onExitSearch = onExitSearch,
            onBack = onBack,
            progress = progress,
            inputFocus = inputFocus,
        )
        AnimatedContent(
            targetState = isSearching,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(durationMillis = 200)),
                    initialContentExit = fadeOut(tween(durationMillis = 140)),
                    sizeTransform = SizeTransform(clip = true),
                )
            },
            label = "gimi-sheet-search-body",
            modifier = Modifier.fillMaxWidth(),
        ) { searching ->
            if (searching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                contentMax.isSpecified -> Modifier.height(contentMax)
                                else -> Modifier.fillMaxHeight()
                            },
                        ),
                ) {
                    searchContent()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (contentMax.isSpecified) Modifier.heightIn(max = contentMax) else Modifier,
                        ),
                ) {
                    listContent()
                }
            }
        }
    }
}

/**
 * 标题行 / 搜索胶囊共用的 72dp 槽位。
 *
 * 列表显示态：返回箭头 + 居中标题 + 尾部搜索图标；最右侧移除关闭 ✕，抽屉关闭
 * 由外壳遮罩、下拉把手或系统返回键统一处理。
 *
 * 搜索态：点击搜索图标后，胶囊由右向左展开至整行输入框（56dp 高），输入文字与胶囊内嵌
 * 右端的退出搜索 ✕ 渐入；点击胶囊内 ✕（或系统返回键）退出搜索回到列表态。
 *
 * 过渡结算后才请求焦点并弹键盘；收起结算时收起键盘，避免键盘在退出后迟到或残留。
 */
@Composable
private fun SearchExpandHeader(
    title: String,
    isSearching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onExitSearch: () -> Unit,
    onBack: () -> Unit,
    progress: Float,
    inputFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchHeaderHeight),
    ) {
        val horizontalPadding = GimiListDefaults.HorizontalPadding
        val capsuleExpandedWidth = maxWidth - horizontalPadding * 2
        val searchDescription = stringResource(R.string.gimi_sheet_action_search)

        // 标题行层：progress 到 1 后不再组合，避免隐形按钮吃掉点击。
        if (progress < 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - progress },
            ) {
                GimiSheetTitleRow(
                    title = title,
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    navigationContentDescription = stringResource(R.string.common_back),
                    onNavigationClick = onBack,
                    trailing = {
                        // 右侧留出与搜索图标等宽的隐形占位，与左侧 40dp 返回键对称，标题居中。
                        Spacer(Modifier.size(SearchCapsuleCollapsedSize))
                    },
                )
            }
        }

        // 搜索胶囊：收起时是 40dp 圆形图标位，落在行尾与标题行尾对齐；展开时整行拉宽（56dp 高）。
        val inputAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
        var inputFocused by remember { mutableStateOf(false) }
        val capsuleWidth = SearchCapsuleCollapsedSize + (capsuleExpandedWidth - SearchCapsuleCollapsedSize) * progress
        val capsuleHeight = SearchCapsuleCollapsedSize + (SearchCapsuleExpandedHeight - SearchCapsuleCollapsedSize) * progress

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = horizontalPadding)
                .width(capsuleWidth)
                .height(capsuleHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = progress))
                .border(
                    width = if (inputFocused) 2.dp else 1.dp,
                    color = (if (inputFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }).copy(alpha = progress),
                    shape = CircleShape,
                )
                .semantics { if (!isSearching) contentDescription = searchDescription }
                .clickable {
                    if (!isSearching) onOpenSearch() else inputFocus.requestFocus()
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (progress > 0f) (SearchCloseSlot + SearchCloseGap) * progress else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(SearchCapsuleCollapsedSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = isSearching,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = inputAlpha }
                        .focusRequester(inputFocus)
                        .onFocusChanged { inputFocused = it.isFocused }
                        .padding(end = 8.dp),
                )
            }

            // 展开搜索态：内嵌右端的 ✕ 仅在 progress > 0 时组合显示，点击退出搜索回列表
            if (progress > 0f) {
                IconButton(
                    onClick = {
                        keyboard?.hide()
                        onExitSearch()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(SearchCloseSlot)
                        .graphicsLayer { alpha = inputAlpha },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.gimi_sheet_search_exit),
                    )
                }
            }
        }
    }
}

// ── 预览 ────────────────────────────────────────────────────

/** 预览按抽屉页面的真实底色渲染，列表内容用占位卡片示意。 */
@Composable
private fun GimiSearchableSheetPagePreviewHost(
    isSearching: Boolean,
    query: String = "",
) {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            GimiSearchableSheetPage(
                title = "选择播放音色",
                isSearching = isSearching,
                query = query,
                onQueryChange = {},
                onOpenSearch = {},
                onExitSearch = {},
                onBack = {},
                onClose = {},
                listContent = {
                    Column {
                        repeat(6) { index ->
                            GimiPickerItemRow(
                                title = "音色选项 $index",
                                subtitle = "女 · 知心温暖 · 社交陪伴",
                                selected = index == 0,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                },
                searchContent = {
                    Column {
                        GimiPickerItemRow(
                            title = "青涩青年音色",
                            subtitle = "男 · 青涩 · 社交陪伴",
                            selected = false,
                            onClick = {},
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        GimiPickerItemRow(
                            title = "精英青年音色",
                            subtitle = "男 · 沉稳 · 社交陪伴",
                            selected = false,
                            onClick = {},
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                },
            )
        }
    }
}

@Preview(name = "可搜索选择页 · 列表态 · 浅色", showBackground = true, widthDp = 390, heightDp = 620)
@Preview(
    name = "可搜索选择页 · 列表态 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 620,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GimiSearchableSheetPageListPreview() {
    GimiSearchableSheetPagePreviewHost(isSearching = false)
}

@Preview(name = "可搜索选择页 · 搜索空态 · 浅色", showBackground = true, widthDp = 390, heightDp = 620)
@Preview(
    name = "可搜索选择页 · 搜索空态 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 620,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GimiSearchableSheetPageSearchEmptyPreview() {
    GimiSearchableSheetPagePreviewHost(isSearching = true)
}

@Preview(name = "可搜索选择页 · 搜索命中 · 浅色", showBackground = true, widthDp = 390, heightDp = 620)
@Composable
private fun GimiSearchableSheetPageSearchHitPreview() {
    GimiSearchableSheetPagePreviewHost(isSearching = true, query = "青年")
}
