package github.ponyhuang.gimi.feature.chat

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 分组卡片圆角：与设置页分组卡片同值。 */
private val DrawerGroupCornerRadius = 16.dp

/** 分组卡片左右外边距，同时是行内文字与卡片边缘的距离基准。 */
private val DrawerGroupHorizontalPadding = 16.dp

/** 紧凑行最小高度：与设置页 `PreferenceListItem` 一致。 */
private val DrawerRowMinHeight = GimiListDefaults.MinHeight

/** 底部渐隐高度：容纳右下角齿轮按钮及其留白。 */
private val DrawerFooterHeight = 88.dp

/** 会话列表顶端的渐隐高度，柔化滚动内容与固定区头之间的边界。 */
private val DrawerTopFadeHeight = 24.dp

/** 常用功能行几何：图标 + 文字，与设置页列表行同一套语言。 */
private val DrawerFunctionRowMinHeight = 56.dp
private val DrawerFunctionIconSize = 22.dp
private val DrawerFunctionDividerStart =
    DrawerGroupHorizontalPadding + DrawerFunctionIconSize + GimiListDefaults.ContentSpacing

/** 右下角齿轮按钮直径。 */
private val DrawerGearButtonSize = 48.dp

/**
 * 历史对话抽屉 —— 预测性返回进度抽屉（[PredictiveBackDrawer]）的内容编排。
 *
 * 视觉与设置页 / "添加到聊天"面板同一套 One UI 语言：画布灰底承载白色分组卡片。
 * 结构自上而下：顶部搜索入口 → 常用功能入口（新对话 / 语音通话 / 个性化）→ 最近会话 + 管理（多选期间换成
 * 取消 / 已选数 / 全选 / 删除）→ 历史会话分组卡片，右下角是设置齿轮（含更新提醒点）。
 * 夜间模式等外观配置已移至设置的"个性化"分组，抽屉不再直接切换。
 *
 * 作为根级组合包装 [content]，宿主屏幕（如 Scaffold）放在 [content] 内部。
 * 调用方持有 `open` 目标状态并通过 [onOpenChange] 接收手势提交结果；
 * 预测性返回、边缘滑动打开、拖拽关闭、遮罩点击关闭由 [PredictiveBackDrawer] 统一处理。
 *
 * @param open                 抽屉目标开关状态
 * @param gesturesEnabled      是否启用抽屉手势；全屏页面（如语音通话页）覆盖主内容时应传 `false`，
 *                             避免手势被遮挡层误捕获
 * @param conversations        由 Route/ViewModel 下发的对话列表；组件本身不解析业务依赖。
 * @param currentSessionId     当前正在使用的 session id；与该 id 匹配的 [Conversation] 行 MUST 高亮且不可删除（不允许删自己）。
 * @param onConversationClick  点击某个对话时回调（实现方负责关闭抽屉）
 * @param recentState          搜索、多选和删除进度状态
 * @param onRecentAction       最近会话操作，由 ViewModel 处理并再次校验删除保护
 * @param onNewConversationClick 点击"新对话"入口时回调（实现方负责新建会话并关闭抽屉）
 * @param onVoiceCallClick     点击"语音通话"入口时回调（实现方负责打开语音页并关闭抽屉）
 * @param onPersonalizeClick   点击"个性化"入口时回调（实现方负责跳转设置并关闭抽屉）
 * @param onSettingsClick      点击右下角齿轮时回调（实现方负责关闭抽屉并跳转）
 * @param showUpdateBadge      是否在齿轮上点亮更新提醒点
 * @param onOpenChange         抽屉目标开关状态变化上报（手势提交、遮罩点击、拖拽收尾）
 * @param onExportConversation 长按菜单导出会话；实现方负责弹出系统文件选择器
 * @param onDeleteConversation 长按菜单删除单个会话；实现方负责再次校验删除保护
 * @param content              抽屉背后的主屏幕内容
 */
@Composable
fun ChatDrawer(
    open: Boolean,
    gesturesEnabled: Boolean = true,
    conversations: List<Conversation>,
    currentSessionId: String,
    conversationTaskStatuses: Map<String, ConversationTaskStatus> = emptyMap(),
    onConversationClick: (Conversation) -> Unit,
    recentState: RecentConversationsState = RecentConversationsState(),
    onRecentAction: (RecentConversationsAction) -> Unit = {},
    onNewConversationClick: () -> Unit = {},
    onVoiceCallClick: () -> Unit = {},
    onPersonalizeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    showUpdateBadge: Boolean = false,
    exportState: SessionExportUiState = SessionExportUiState(hasConversations = true),
    onExportConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    onOpenChange: (Boolean) -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    onSearchExpanded: () -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val historyListState = rememberLazyListState()
    LaunchedEffect(open) {
        if (open) {
            // 稳定 key 会保留旧的可见项；打开最近列表时显式回到顶部，避免新会话藏在上方。
            historyListState.scrollToItem(0)
        }
    }
    PredictiveBackDrawer(
        open = open,
        gesturesEnabled = gesturesEnabled,
        // 多选期间返回键应优先结束选择（由调用方的 BackHandler 处理），这里让出返回手势。
        backHandlerEnabled = !recentState.isSelecting && !recentState.isSearching,
        onOpenChange = onOpenChange,
        modifier = modifier,
        drawerContent = {
            // 与设置页同底色：灰画布承载白色分组卡片；面板几何由宿主组件裁剪。
            Box(Modifier.fillMaxSize().background(preferenceCanvasColor())) {
                PreferenceStyleScope(PreferenceStyle.Compact) {
                    HistoryDrawerContent(
                        conversations = conversations,
                        currentSessionId = currentSessionId,
                        conversationTaskStatuses = conversationTaskStatuses,
                        onConversationClick = onConversationClick,
                        recentState = recentState,
                        onRecentAction = onRecentAction,
                        onNewConversationClick = onNewConversationClick,
                        onVoiceCallClick = onVoiceCallClick,
                        onPersonalizeClick = onPersonalizeClick,
                        onSettingsClick = onSettingsClick,
                        showUpdateBadge = showUpdateBadge,
                        listState = historyListState,
                        exportState = exportState,
                        onExportConversation = onExportConversation,
                        onDeleteConversation = onDeleteConversation,
                        searchFocusRequester = searchFocusRequester,
                        onSearchExpanded = onSearchExpanded,
                        onSearchSubmit = onSearchSubmit,
                    )
                }
            }
        },
        content = content,
    )
}

// ── 抽屉内容 ─────────────────────────────────────────────────────

/**
 * 抽屉面板的内容：顶部搜索入口、可收起的功能区、最近会话管理与列表、底部设置。
 */
@Composable
private fun HistoryDrawerContent(
    conversations: List<Conversation>,
    currentSessionId: String,
    conversationTaskStatuses: Map<String, ConversationTaskStatus>,
    onConversationClick: (Conversation) -> Unit,
    recentState: RecentConversationsState,
    onRecentAction: (RecentConversationsAction) -> Unit,
    onNewConversationClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onPersonalizeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showUpdateBadge: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    exportState: SessionExportUiState = SessionExportUiState(hasConversations = true),
    onExportConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    onSearchExpanded: () -> Unit = {},
    onSearchSubmit: () -> Unit = {},
) {
    val visible = recentState.visibleConversations(conversations)
    val eligible = deletableConversationIds(conversations, currentSessionId, conversationTaskStatuses)
    val selectedIds = recentState.selectedIds.intersect(eligible)
    val visibleEligible = visible.map { it.id }.toSet().intersect(eligible)
    val allSelected = visibleEligible.isNotEmpty() && selectedIds.containsAll(visibleEligible)
    val canvas = preferenceCanvasColor()
    val topFadeBrush = remember(canvas) {
        Brush.verticalGradient(listOf(canvas, Color.Transparent))
    }
    // 与聊天页底部 composer 渐隐同源：列表末尾在齿轮区后方柔和消失。
    val bottomFadeBrush = remember(canvas) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.333f to canvas.copy(alpha = 0.7f),
                0.667f to canvas.copy(alpha = 0.9f),
                1.0f to canvas,
            ),
        )
    }

    Column(Modifier.fillMaxHeight().statusBarsPadding()) {
        // 隐形焦点承接点：抽屉打开后初始焦点落在这里，避免进入搜索框唤起输入法。
        Box(Modifier.size(1.dp).focusable())
        DrawerSearchHeader(
            expanded = recentState.isSearching,
            query = recentState.query,
            enabled = !recentState.isDeleting,
            selecting = recentState.isSelecting,
            focusRequester = searchFocusRequester,
            onOpen = { onRecentAction(RecentConversationsAction.OpenSearch) },
            onClose = { onRecentAction(RecentConversationsAction.CloseSearch) },
            onQueryChange = { onRecentAction(RecentConversationsAction.Search(it)) },
            onExpanded = onSearchExpanded,
            onSearch = onSearchSubmit,
        )
        AnimatedVisibility(
            visible = !recentState.isSearching,
            enter = fadeIn(tween(180)) + expandVertically(tween(280), expandFrom = Alignment.Top),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(280), shrinkTowards = Alignment.Top),
        ) {
            Column {
                // 常用功能：图标 + 文字的紧凑入口行，免滚动直达高频操作。
                DrawerFunctionEntry(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.chat_drawer_new_conversation),
                    isFirst = true,
                    isLast = false,
                    onClick = onNewConversationClick,
                )
                DrawerFunctionEntry(
                    icon = Icons.Default.Call,
                    label = stringResource(R.string.chat_drawer_voice_call),
                    isFirst = false,
                    isLast = false,
                    onClick = onVoiceCallClick,
                )
                DrawerFunctionEntry(
                    icon = Icons.Default.Palette,
                    label = stringResource(R.string.chat_drawer_personalize),
                    isFirst = false,
                    isLast = true,
                    onClick = onPersonalizeClick,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        // 历史区头：最近会话 + 管理；搜索期间同步收起，多选时显示选择工具栏。
        if (recentState.isSelecting) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !recentState.isDeleting,
                    onClick = { onRecentAction(RecentConversationsAction.FinishSelection) },
                ) {
                    Text(stringResource(R.string.chat_drawer_cancel))
                }
                Text(
                    text = stringResource(R.string.chat_drawer_selected_count, selectedIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = visibleEligible.isNotEmpty() && !recentState.isDeleting,
                    onClick = { onRecentAction(RecentConversationsAction.ToggleSelectAll) },
                ) {
                    Text(stringResource(if (allSelected) R.string.chat_drawer_deselect_all else R.string.chat_drawer_select_all))
                }
                // 批量删除入口：先弹确认框（RequestDeletion 固化待删集合），确认后才真正删除。
                // 红色跟随可用态：未勾选或删除中时置灰，避免禁用态看起来仍可点击。
                val deleteEnabled = selectedIds.isNotEmpty() && !recentState.isDeleting
                TextButton(
                    enabled = deleteEnabled,
                    onClick = { onRecentAction(RecentConversationsAction.RequestDeletion) },
                ) {
                    Text(
                        text = stringResource(
                            if (recentState.isDeleting) R.string.chat_drawer_deleting
                            else R.string.chat_drawer_delete_selected,
                        ),
                        color = when {
                            recentState.isDeleting -> MaterialTheme.colorScheme.onSurfaceVariant
                            deleteEnabled -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = !recentState.isSearching,
                enter = fadeIn(tween(180)) + expandVertically(tween(280), expandFrom = Alignment.Top),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(280), shrinkTowards = Alignment.Top),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_drawer_recent_conversations),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        enabled = !recentState.isDeleting && conversations.isNotEmpty(),
                        onClick = { onRecentAction(RecentConversationsAction.StartSelection()) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(stringResource(R.string.chat_drawer_manage))
                    }
                }
            }
        }
        Box(Modifier.weight(1f).navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = if (recentState.isSearching) 16.dp else DrawerFooterHeight),
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(if (recentState.query.isBlank()) R.string.chat_drawer_empty_history else R.string.chat_drawer_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp),
                        )
                    }
                }
                items(items = visible, key = { it.id }) { conversation ->
                    ConversationListItem(
                        conversation = conversation,
                        isCurrent = conversation.id == currentSessionId,
                        taskStatus = conversationTaskStatuses[conversation.id],
                        enabled = !recentState.isDeleting && (!recentState.isSelecting || conversation.id in eligible),
                        isSelecting = recentState.isSelecting,
                        isSelected = conversation.id in selectedIds,
                        isFirst = conversation.id == visible.firstOrNull()?.id,
                        isLast = conversation.id == visible.lastOrNull()?.id,
                        onClick = {
                            if (recentState.isSelecting) onRecentAction(RecentConversationsAction.ToggleSelection(conversation.id))
                            else onConversationClick(conversation)
                        },
                        canExport = exportState.canExport(conversation.id),
                        // 删除沿用批量删除的同一套保护：当前会话与任务进行中的会话不可删。
                        canDelete = conversation.id in eligible,
                        onExport = { onExportConversation(conversation.id) },
                        onDelete = { onDeleteConversation(conversation.id) },
                        onLongClick = if (recentState.isSearching) {
                            { onRecentAction(RecentConversationsAction.StartSelection(conversation.id)) }
                        } else null,
                    )
                }
            }
            // 遮罩只绘制背景渐变，不接管触摸；向上滚动的会话在区头下方自然淡出。
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(DrawerTopFadeHeight)
                    .background(topFadeBrush),
            )
            if (!recentState.isSearching) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DrawerFooterHeight)
                        .background(bottomFadeBrush),
                )
                // 右下角齿轮：唯一的设置入口；有未发现的新版本时点亮提醒点。
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = preferenceGroupCardColor(),
                        shadowElevation = 2.dp,
                    ) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(DrawerGearButtonSize),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.chat_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showUpdateBadge) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                    }
                }
            }
        }
    }
    if (recentState.pendingDeletionIds.isNotEmpty() && !recentState.isDeleting) {
        AlertDialog(
            onDismissRequest = { onRecentAction(RecentConversationsAction.CancelDeletion) },
            title = { Text(stringResource(R.string.chat_drawer_delete_confirm_title, recentState.pendingDeletionIds.size)) },
            text = { Text(stringResource(R.string.chat_drawer_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onRecentAction(RecentConversationsAction.ConfirmDeletion) }) {
                    Text(stringResource(R.string.chat_drawer_delete_conversation))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRecentAction(RecentConversationsAction.CancelDeletion) }) {
                    Text(stringResource(R.string.chat_drawer_cancel))
                }
            },
        )
    }
}

// ── 常用功能入口 ────────────────────────────────────────────────

/**
 * 常用功能入口行：图标 + 文字，与设置页列表行同一套几何（行高、内缩分隔线、分组圆角）。
 */
@Composable
private fun DrawerFunctionEntry(
    icon: ImageVector,
    label: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(
        topStart = if (isFirst) DrawerGroupCornerRadius else 0.dp,
        topEnd = if (isFirst) DrawerGroupCornerRadius else 0.dp,
        bottomStart = if (isLast) DrawerGroupCornerRadius else 0.dp,
        bottomEnd = if (isLast) DrawerGroupCornerRadius else 0.dp,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerGroupHorizontalPadding)
            .clip(shape)
            .background(preferenceGroupCardColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DrawerFunctionRowMinHeight)
                .clickable(onClickLabel = label, onClick = onClick)
                .padding(horizontal = DrawerGroupHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GimiListDefaults.ContentSpacing),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DrawerFunctionIconSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = DrawerFunctionDividerStart, end = DrawerGroupHorizontalPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

// ── 对话列表项 ──────────────────────────────────────────────────

/**
 * 分组卡片中的单个会话行：点击切换，长按显示导出/删除操作；多选时点击整行切换勾选。
 *
 * 行内只有标题与副标题，不额外加图标——会话行靠卡片、分隔线与排版区分层次，
 * 逐行重复同一个图标只会增加视觉噪声。
 *
 * @param isCurrent 当此行是当前正在使用的会话时 MUST 高亮且不可删除（防止用户把自己正在用的会话删掉）。
 * @param isFirst/isLast 首尾行落下圆角，相邻行拼成一张连续卡片。
 */
@Composable
private fun ConversationListItem(
    conversation: Conversation,
    isCurrent: Boolean,
    taskStatus: ConversationTaskStatus?,
    enabled: Boolean,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
    canExport: Boolean = true,
    canDelete: Boolean = true,
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showActions by remember(conversation.id) { mutableStateOf(false) }
    // 选中态用品牌 primaryContainer：深/浅主题下都与白色卡片拉开明度差，
    // secondaryContainer 在深色模式 (#26262A on #1E1E23) 几乎看不出哪条是当前会话。
    val highlighted = if (isSelecting) isSelected else isCurrent
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else preferenceGroupCardColor()
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val rowAlpha = if (enabled) 1f else 0.45f
    val shape = RoundedCornerShape(
        topStart = if (isFirst) DrawerGroupCornerRadius else 0.dp,
        topEnd = if (isFirst) DrawerGroupCornerRadius else 0.dp,
        bottomStart = if (isLast) DrawerGroupCornerRadius else 0.dp,
        bottomEnd = if (isLast) DrawerGroupCornerRadius else 0.dp,
    )
    val taskContentDescription = when (taskStatus) {
        is ConversationTaskStatus.Running -> stringResource(R.string.chat_task_running)
        is ConversationTaskStatus.WaitingForConfirmation -> stringResource(
            R.string.chat_task_waiting_confirmation,
            taskStatus.count,
        )
        ConversationTaskStatus.WaitingForInput ->
            stringResource(R.string.chat_task_waiting_input)
        ConversationTaskStatus.Completed -> stringResource(R.string.chat_task_completed)
        ConversationTaskStatus.Failed -> stringResource(R.string.chat_task_failed)
        null -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerGroupHorizontalPadding)
            .clip(shape)
            .background(containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DrawerRowMinHeight)
                .then(
                    if (isSelecting) Modifier.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
                    else Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClickLabel = stringResource(if (onLongClick != null) R.string.chat_drawer_manage else R.string.session_export_actions),
                        onLongClick = { if (onLongClick != null) onLongClick() else showActions = true },
                    ),
                )
                .padding(horizontal = DrawerGroupHorizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = rowAlpha),
                )
                // 副标题是辨识度的来源：标题可能都是"新对话"，时间 + 末条消息才能区分开。
                Text(
                    text = if (isCurrent) stringResource(R.string.chat_drawer_current_session_time, formatRelativeTime(conversation.timestamp)) else conversation.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = rowAlpha).copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when (taskStatus) {
                is ConversationTaskStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = taskContentDescription },
                    strokeWidth = 2.dp,
                )
                is ConversationTaskStatus.WaitingForConfirmation -> Text(
                    text = taskStatus.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .semantics { contentDescription = taskContentDescription },
                )
                ConversationTaskStatus.WaitingForInput -> Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = taskContentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp),
                )
                ConversationTaskStatus.Completed -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.chat_task_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                ConversationTaskStatus.Failed -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.chat_task_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                null -> Unit
            }
        }
        ConversationActionsMenu(
            expanded = showActions && !isSelecting && enabled,
            onDismiss = { showActions = false },
            canExport = canExport && taskStatus !is ConversationTaskStatus.Running &&
                taskStatus !is ConversationTaskStatus.WaitingForConfirmation &&
                taskStatus != ConversationTaskStatus.WaitingForInput,
            canDelete = canDelete,
            onExport = onExport,
            onDelete = onDelete,
        )
        if (!isLast) {
            // 分隔线内缩到文字左缘：多选时让开勾选框（24 + 12），与设置页分组一致。
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = DrawerGroupHorizontalPadding + if (isSelecting) 36.dp else 0.dp,
                    end = DrawerGroupHorizontalPadding,
                ),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

// ── 副标题：相对时间 + 末条消息 ─────────────────────────────────

/** 单个会话操作不复用删除勾选规则，因此当前会话也能在空闲时导出；删除则沿用同一套保护条件。 */
@Composable
private fun ConversationActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canExport: Boolean,
    canDelete: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.session_export_single)) },
            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            enabled = canExport,
            onClick = {
                onDismiss()
                onExport()
            },
        )
        // 删除项在当前会话/任务进行中被禁用；红色必须跟随置灰，否则禁用态看起来仍可点击。
        val deleteColor = if (canDelete) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.session_delete_single), color = deleteColor)
            },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = deleteColor)
            },
            enabled = canDelete,
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/** 列表副标题：`2 小时前 · 末条消息…`；无消息时只有时间。 */
@Composable
private fun Conversation.subtitle(): String {
    val time = formatRelativeTime(timestamp)
    val snippet = lastMessage.lineSequence().firstOrNull()?.trim().orEmpty()
    return if (snippet.isEmpty()) time else "$time · $snippet"
}

@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1L -> stringResource(R.string.chat_time_just_now)
        minutes < 60L -> stringResource(R.string.chat_time_minutes_ago, minutes)
        hours < 24L -> stringResource(R.string.chat_time_hours_ago, hours)
        days < 7L -> stringResource(R.string.chat_time_days_ago, days)
        else -> {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            stringResource(
                R.string.chat_time_date,
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }
}

// ── 预览 ────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "打开")
@Preview(showBackground = true, showSystemUi = true, name = "打开 · 深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatDrawerPreview() {
    val sampleConversations = listOf(
        Conversation(
            id = "1",
            title = "简历优化（突出活动单独立项）",
            lastMessage = "好的，我已经帮你优化了简历中的活动经历部分…",
            timestamp = System.currentTimeMillis() - 120_000
        ),
        Conversation(
            id = "2",
            title = "Kotlin 协程使用问题",
            lastMessage = "你可以使用 supervisorScope 来处理…",
            timestamp = System.currentTimeMillis() - 3_600_000
        ),
        Conversation(
            id = "3",
            title = "Android 性能优化讨论",
            lastMessage = "建议使用 baseline profile 来提升启动速度",
            timestamp = System.currentTimeMillis() - 86_400_000
        ),
        Conversation(
            id = "4",
            title = "Compose 布局调试",
            lastMessage = "使用 Layout Inspector 可以看到重组次数",
            timestamp = System.currentTimeMillis() - 172_800_000
        )
    )

    GimiTheme {
        ChatDrawer(
            open = true,
            conversations = sampleConversations,
            currentSessionId = "1",
            onConversationClick = { },
            showUpdateBadge = true,
        ) {
            // 预览中作为占位的主屏幕内容
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("主屏幕内容")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "打开（空状态）")
@Composable
private fun ChatDrawerEmptyPreview() {
    GimiTheme {
        ChatDrawer(
            open = true,
            conversations = emptyList(),
            currentSessionId = "",
            onConversationClick = { },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("主屏幕内容")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "关闭")
@Composable
private fun ChatDrawerClosedPreview() {
    GimiTheme {
        ChatDrawer(
            open = false,
            conversations = emptyList(),
            currentSessionId = "",
            onConversationClick = { },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("主屏幕内容（抽屉关闭）")
            }
        }
    }
}

@Preview(name = "最近 · 紧凑多选", widthDp = 320, heightDp = 780, showBackground = true)
@Preview(name = "最近 · 紧凑多选 · 深色", widthDp = 320, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecentSelectionPreview() = RecentContentPreview(
    recentState = RecentConversationsState(isSelecting = true, selectedIds = setOf("1", "3", "4")),
)

@Preview(name = "最近 · 搜索", widthDp = 320, heightDp = 780, showBackground = true)
@Preview(name = "最近 · 搜索 · 深色", widthDp = 320, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecentSearchPreview() = RecentContentPreview(recentState = RecentConversationsState(query = "相机", isSearching = true))

@Preview(name = "最近 · 搜索多选", widthDp = 320, heightDp = 780)
@Preview(name = "最近 · 搜索多选 · 深色", widthDp = 320, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecentSearchSelectionPreview() = RecentContentPreview(
    recentState = RecentConversationsState(query = "相机", isSearching = true, isSelecting = true, selectedIds = setOf("5")),
)

@Preview(name = "最近 · 深色", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun RecentDarkPreview() = RecentContentPreview(darkTheme = true)

@Preview(name = "最近 · 搜索无结果", widthDp = 320, heightDp = 780, showBackground = true)
@Preview(name = "最近 · 搜索无结果 · 深色", widthDp = 320, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecentNoResultsPreview() = RecentContentPreview(recentState = RecentConversationsState(query = "不存在", isSearching = true))

@Composable
private fun RecentContentPreview(
    recentState: RecentConversationsState = RecentConversationsState(),
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val fixtures = listOf(
        "帮我开空调", "播放周杰伦夜曲", "微信小程序斗地主挂机未完成", "广州早茶推荐清单",
        "索尼 A7C 夜景参数推荐", "松下相机屏幕偏橙排查", "白鸽潭点向去", "配置高德地图 MCP Server",
        "周末散步路线", "整理本周工作笔记",
    ).mapIndexed { index, title ->
        Conversation(id = index.toString(), title = title, timestamp = System.currentTimeMillis() - (index + 9) * 3_600_000L)
    }
    GimiTheme(darkTheme = darkTheme) {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                HistoryDrawerContent(
                    conversations = fixtures,
                    currentSessionId = "0",
                    conversationTaskStatuses = emptyMap(),
                    onConversationClick = {},
                    recentState = recentState,
                    onRecentAction = {},
                    onNewConversationClick = {},
                    onVoiceCallClick = {},
                    onPersonalizeClick = {},
                    onSettingsClick = {},
                    showUpdateBadge = true,
                )
            }
        }
    }
}

@Preview(name = "单个会话导出 · 浅色", widthDp = 360, heightDp = 150)
@Preview(name = "单个会话导出 · 深色", widthDp = 360, heightDp = 150, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationExportPreview() {
    GimiTheme {
        ConversationListItem(
            conversation = Conversation(id = "preview", title = "整理本周工作笔记", timestamp = System.currentTimeMillis()),
            isCurrent = true,
            taskStatus = null,
            enabled = true,
            isFirst = true,
            isLast = true,
            onClick = {},
        )
    }
}

@Preview(name = "会话操作菜单 · 可用", widthDp = 260, heightDp = 150)
@Preview(name = "会话操作菜单 · 可用 · 深色", widthDp = 260, heightDp = 150, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationActionsMenuPreview() {
    GimiTheme {
        Box {
            ConversationActionsMenu(expanded = true, onDismiss = {}, canExport = true, canDelete = true, onExport = {}, onDelete = {})
        }
    }
}

@Preview(name = "会话操作菜单 · 当前会话", widthDp = 260, heightDp = 150)
@Preview(name = "会话操作菜单 · 当前会话 · 深色", widthDp = 260, heightDp = 150, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationActionsMenuBlockedPreview() {
    GimiTheme {
        Box {
            ConversationActionsMenu(expanded = true, onDismiss = {}, canExport = true, canDelete = false, onExport = {}, onDelete = {})
        }
    }
}

@Preview(name = "会话操作菜单 · 任务进行中", widthDp = 260, heightDp = 150)
@Preview(name = "会话操作菜单 · 任务进行中 · 深色", widthDp = 260, heightDp = 150, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationActionsMenuRunningPreview() {
    GimiTheme {
        Box {
            ConversationActionsMenu(expanded = true, onDismiss = {}, canExport = false, canDelete = false, onExport = {}, onDelete = {})
        }
    }
}
