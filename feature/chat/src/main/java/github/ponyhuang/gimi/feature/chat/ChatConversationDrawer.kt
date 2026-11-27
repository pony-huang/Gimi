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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ChatMode
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

/** 会话行最小高度：单行精简胶囊高度。 */
private val DrawerRowMinHeight = 48.dp

/** 独立胶囊行圆角：方案 A 独立圆角胶囊。 */
private val DrawerRowCornerRadius = 12.dp

/** 独立胶囊行上下垂直微间距。 */
private val DrawerRowItemSpacing = 4.dp

/** 底部渐隐高度：容纳右下角齿轮按钮及其留白。 */
private val DrawerFooterHeight = 88.dp

/** 会话列表顶端的渐隐高度，柔化滚动内容与固定区头之间的边界。 */
private val DrawerTopFadeHeight = 24.dp

/** 常用功能行几何：图标 + 文字，比设置页列表行更紧凑（行高、字号、图标同步缩小）。 */
private val DrawerFunctionRowMinHeight = 48.dp
private val DrawerFunctionIconSize = 20.dp
private val DrawerFunctionDividerStart =
    DrawerGroupHorizontalPadding + DrawerFunctionIconSize + GimiListDefaults.ContentSpacing

/** 右下角齿轮按钮直径。 */
private val DrawerGearButtonSize = 48.dp

/**
 * 历史列表的助手/Chatbot 筛选 tab。
 *
 * 语义上是"列表筛选"而非页面模式切换，用原生 TabRow：指示条自带滑动动画，
 * 与列表 animateItem 同节奏；顶栏/语音页的胶囊开关保持不变。
 */
@Composable
private fun ChatHistoryModeTabs(mode: ChatMode, onModeChange: (ChatMode) -> Unit, modifier: Modifier = Modifier) {
    val selected = ChatMode.entries.indexOf(mode).coerceIn(0, ChatMode.entries.lastIndex)
    SecondaryTabRow(
        selectedTabIndex = selected,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(selected)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(percent = 50)),
                height = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        ChatMode.entries.forEach { value ->
            Tab(
                selected = mode == value,
                onClick = { onModeChange(value) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        text = stringResource(if (value == ChatMode.ASSISTANT) R.string.chat_mode_assistant else R.string.chat_mode_chatbot),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

/**
 * 历史对话抽屉 —— 预测性返回进度抽屉（[PredictiveBackDrawer]）的内容编排。
 *
 * 视觉与设置页 / "添加到聊天"面板同一套 One UI 语言：画布灰底承载白色分组卡片。
 * 结构自上而下：顶部搜索入口 → 常用功能入口（模型服务 / 插件 / 本地环境 / 个性化 / 自动播报开关）
 * → 多选工具栏（取消 / 已选数 / 删除，从会话长按菜单的「多选」进入）→ 历史会话分组卡片，右下角是设置齿轮（含更新提醒点）。
 * 自动播报从聊天顶栏下沉到抽屉，作为开关类设置与跳转类入口分层呈现。
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
 * @param onModelServicesClick 点击"模型服务"入口时回调（实现方负责跳转模型服务配置并关闭抽屉）
 * @param onPluginsClick       点击"插件"入口时回调（实现方负责跳转插件列表并关闭抽屉）
 * @param onLocalEnvironmentClick 点击"本地环境"入口时回调（实现方负责直达本地环境页并关闭抽屉）
 * @param onPersonalizeClick   点击"个性化"入口时回调（实现方负责跳转设置并关闭抽屉）
 * @param onSettingsClick      点击右下角齿轮时回调（实现方负责关闭抽屉并跳转）
 * @param autoSpeakEnabled     自动播报全局开关当前值，驱动抽屉内开关卡片
 * @param onToggleAutoSpeak    切换自动播报开关；由 ViewModel 写回并持久化
 * @param showUpdateBadge      是否在齿轮上点亮更新提醒点
 * @param onOpenChange         抽屉目标开关状态变化上报（手势提交、遮罩点击、拖拽收尾）
 * @param onExportConversation 长按菜单导出会话；实现方按档位解析分区并弹出系统文件选择器
 * @param onDeleteConversation 长按菜单删除单个会话；实现方负责再次校验删除保护
 * @param historyMode          当前历史档位；非空时展示助手/Chatbot 筛选 Tab。
 *                             `null` 只用于预览等不展示 Tab 的场合。
 * @param onHistoryModeChange  历史档位切换回调（Tab 点击）
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
    onModelServicesClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onLocalEnvironmentClick: () -> Unit = {},
    onPersonalizeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    autoSpeakEnabled: Boolean = false,
    onToggleAutoSpeak: () -> Unit = {},
    showUpdateBadge: Boolean = false,
    exportState: SessionExportUiState = SessionExportUiState(hasConversations = true),
    onExportConversation: (String, ChatMode) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    onOpenChange: (Boolean) -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    onSearchExpanded: () -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    historyMode: ChatMode? = null,
    onHistoryModeChange: (ChatMode) -> Unit = {},
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
                        onModelServicesClick = onModelServicesClick,
                        onPluginsClick = onPluginsClick,
                        onLocalEnvironmentClick = onLocalEnvironmentClick,
                        onPersonalizeClick = onPersonalizeClick,
                        onSettingsClick = onSettingsClick,
                        autoSpeakEnabled = autoSpeakEnabled,
                        onToggleAutoSpeak = onToggleAutoSpeak,
                        showUpdateBadge = showUpdateBadge,
                        listState = historyListState,
                        exportState = exportState,
                        onExportConversation = onExportConversation,
                        onDeleteConversation = onDeleteConversation,
                        searchFocusRequester = searchFocusRequester,
                        onSearchExpanded = onSearchExpanded,
                        onSearchSubmit = onSearchSubmit,
                        historyMode = historyMode,
                        onHistoryModeChange = onHistoryModeChange,
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
    onModelServicesClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onLocalEnvironmentClick: () -> Unit,
    onPersonalizeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    autoSpeakEnabled: Boolean = false,
    onToggleAutoSpeak: () -> Unit = {},
    showUpdateBadge: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    exportState: SessionExportUiState = SessionExportUiState(hasConversations = true),
    onExportConversation: (String, ChatMode) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    onSearchExpanded: () -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    historyMode: ChatMode? = null,
    onHistoryModeChange: (ChatMode) -> Unit = {},
) {
    val visible = recentState.visibleConversations(conversations)
    val eligible = deletableConversationIds(conversations, currentSessionId, conversationTaskStatuses)
    val selectedIds = recentState.selectedIds.intersect(eligible)
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
                // 模型服务/插件/本地环境一组（跳转类入口），个性化独立成卡，自动播报
                // 开关卡沉到常用功能最底部——开关类设置与跳转类入口视觉分层；两档 tab 共用同一套入口。
                DrawerFunctionEntry(
                    icon = Icons.Default.SmartToy,
                    label = stringResource(R.string.chatbot_model_services),
                    isFirst = true,
                    isLast = false,
                    onClick = onModelServicesClick,
                )
                DrawerFunctionEntry(
                    icon = Icons.Default.Extension,
                    label = stringResource(R.string.chat_drawer_plugins),
                    isFirst = false,
                    isLast = false,
                    onClick = onPluginsClick,
                )
                DrawerFunctionEntry(
                    icon = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_linux),
                    label = stringResource(R.string.chat_drawer_local_environment),
                    isFirst = false,
                    isLast = false,
                    onClick = onLocalEnvironmentClick,
                )
                DrawerFunctionEntry(
                    icon = Icons.Default.AutoAwesome,
                    label = stringResource(R.string.chat_drawer_personalize),
                    isFirst = false,
                    isLast = true,
                    onClick = onPersonalizeClick,
                )
                Spacer(Modifier.height(12.dp))
                DrawerAutoSpeakCard(
                    enabled = autoSpeakEnabled,
                    onToggle = onToggleAutoSpeak,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        // 历史区头：多选时显示选择工具栏；多选入口统一放在会话长按菜单里。
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
        }
        if (historyMode != null) {
            ChatHistoryModeTabs(historyMode, onHistoryModeChange, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        Box(Modifier.weight(1f).navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(DrawerRowItemSpacing),
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
                        // 助手/Chatbot 切档时两个数据源的条目增减用淡入淡出+位移过渡，
                        // 与历史筛选 tab 指示条动画形成同一节奏。
                        modifier = Modifier.animateItem(),
                        isCurrent = conversation.id == currentSessionId,
                        taskStatus = conversationTaskStatuses[conversation.id],
                        enabled = !recentState.isDeleting && (!recentState.isSelecting || conversation.id in eligible),
                        isSelecting = recentState.isSelecting,
                        isSelected = conversation.id in selectedIds,
                        onClick = {
                            if (recentState.isSelecting) onRecentAction(RecentConversationsAction.ToggleSelection(conversation.id))
                            else onConversationClick(conversation)
                        },
                        canExport = exportState.canExport(conversation.id),
                        // 删除沿用批量删除的同一套保护：当前会话与任务进行中的会话不可删。
                        canDelete = conversation.id in eligible,
                        onExport = { onExportConversation(conversation.id, historyMode ?: ChatMode.ASSISTANT) },
                        onDelete = { onDeleteConversation(conversation.id) },
                        onStartSelection = { onRecentAction(RecentConversationsAction.StartSelection(conversation.id)) },
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
                .padding(horizontal = DrawerGroupHorizontalPadding, vertical = 6.dp),
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
                style = MaterialTheme.typography.bodyMedium,
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

// ── 自动播报开关 ────────────────────────────────────────────────

/**
 * 自动语音播报卡片：开关 + 说明文字，独立于跳转入口行成卡。
 * 从聊天顶栏下沉到抽屉：顶栏只留高频导航，低频持久开关归入抽屉设置区。
 *
 * @param enabled 自动播报全局开关当前值
 * @param onToggle 切换开关；状态由 ViewModel 写回 [enabled]
 */
@Composable
private fun DrawerAutoSpeakCard(enabled: Boolean, onToggle: () -> Unit) {
    val label = stringResource(R.string.chat_drawer_auto_speak)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerGroupHorizontalPadding)
            .clip(RoundedCornerShape(DrawerGroupCornerRadius))
            .background(preferenceGroupCardColor())
            .toggleable(value = enabled, role = Role.Switch, onValueChange = { onToggle() }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DrawerFunctionRowMinHeight)
                .padding(horizontal = DrawerGroupHorizontalPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GimiListDefaults.ContentSpacing),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DrawerFunctionIconSize),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            github.ponyhuang.gimi.ui.components.GimiSwitch(checked = enabled, onCheckedChange = null)
        }
    }
}

// ── 对话列表项 ──────────────────────────────────────────────────

/**
 * 历史会话单行胶囊项（方案 A）：点击切换，长按显示多选/导出/删除操作；多选时点击整行切换勾选。
 *
 * 标题单行长文本自动省略（Ellipsis），末尾右对齐展示紧凑相对时间（如 3m, 6h, 2d）。
 * 移除原有的底部末条消息摘要，采用独立圆角胶囊与微间距排版，无内部分隔线。
 *
 * @param isCurrent 当此行是当前正在使用的会话时 MUST 高亮且不可删除（防止用户把自己正在用的会话删掉）。
 */
@Composable
private fun ConversationListItem(
    conversation: Conversation,
    isCurrent: Boolean,
    taskStatus: ConversationTaskStatus?,
    enabled: Boolean,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    canExport: Boolean = true,
    canDelete: Boolean = true,
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onStartSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showActions by remember(conversation.id) { mutableStateOf(false) }
    // 选中态用品牌 primaryContainer：深/浅主题下都与画布拉开明度差；未选中态透明无背景
    val highlighted = if (isSelecting) isSelected else isCurrent
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val timeColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowAlpha = if (enabled) 1f else 0.45f
    val shape = RoundedCornerShape(DrawerRowCornerRadius)
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

    Box(
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
                        onLongClickLabel = stringResource(R.string.session_export_actions),
                        onLongClick = { showActions = true },
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor.copy(alpha = rowAlpha),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            when (taskStatus) {
                is ConversationTaskStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                        .semantics { contentDescription = taskContentDescription },
                    strokeWidth = 2.dp,
                )
                is ConversationTaskStatus.WaitingForConfirmation -> Text(
                    text = taskStatus.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .semantics { contentDescription = taskContentDescription },
                )
                ConversationTaskStatus.WaitingForInput -> Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = taskContentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp),
                )
                ConversationTaskStatus.Completed -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.chat_task_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp),
                )
                ConversationTaskStatus.Failed -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.chat_task_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp),
                )
                null -> Unit
            }
            Text(
                text = formatCompactTime(conversation.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = timeColor.copy(alpha = rowAlpha),
            )
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
            onStartSelection = onStartSelection,
        )
    }
}

// ── 紧凑相对时间格式化 ─────────────────────────────────

/**
 * 格式化会话紧凑相对时间：如 `刚刚` (或 `now`), `3m`, `6h`, `2d`, `1mo`, `1y`。
 *
 * 超紧凑纯数字+单字符后缀，适配单行会话行，避免长标题被多字副标题时间挤压。
 */
@Composable
internal fun formatCompactTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    val months = days / 30L
    val years = days / 365L
    return when {
        minutes < 1L -> stringResource(R.string.chat_time_compact_just_now)
        minutes < 60L -> stringResource(R.string.chat_time_compact_minutes, minutes)
        hours < 24L -> stringResource(R.string.chat_time_compact_hours, hours)
        days < 30L -> stringResource(R.string.chat_time_compact_days, days)
        months < 12L -> stringResource(R.string.chat_time_compact_months, months)
        else -> stringResource(R.string.chat_time_compact_years, years)
    }
}

/**
 * 纯计算版本，供 JVM 单元测试在无 Compose/Context 环境下验证时间档位区间。
 */
internal fun formatCompactTimeRaw(
    timestamp: Long,
    now: Long,
    justNow: String = "刚刚",
    minutesFormat: String = "%dm",
    hoursFormat: String = "%dh",
    daysFormat: String = "%dd",
    monthsFormat: String = "%dmo",
    yearsFormat: String = "%dy",
): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    val months = days / 30L
    val years = days / 365L
    return when {
        minutes < 1L -> justNow
        minutes < 60L -> String.format(java.util.Locale.ROOT, minutesFormat, minutes)
        hours < 24L -> String.format(java.util.Locale.ROOT, hoursFormat, hours)
        days < 30L -> String.format(java.util.Locale.ROOT, daysFormat, days)
        months < 12L -> String.format(java.util.Locale.ROOT, monthsFormat, months)
        else -> String.format(java.util.Locale.ROOT, yearsFormat, years)
    }
}

/**
 * 单个会话操作菜单：多选入口始终可用（进入时由 reducer 过滤可删除会话并预选被长按的项），
 * 导出不复用删除勾选规则，当前会话也能在空闲时导出；删除则沿用同一套保护条件。
 */
@Composable
private fun ConversationActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canExport: Boolean,
    canDelete: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onStartSelection: () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // 多选入口固定在首位：长按任意会话都能带预选进入批量操作。
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_drawer_multi_select)) },
            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
            onClick = {
                onDismiss()
                onStartSelection()
            },
        )
        if (canExport) DropdownMenuItem(
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
                    onModelServicesClick = {},
                    onPluginsClick = {},
                    onLocalEnvironmentClick = {},
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
