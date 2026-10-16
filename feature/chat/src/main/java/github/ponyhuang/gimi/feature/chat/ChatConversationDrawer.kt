package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 分组卡片圆角：与设置页分组卡片同值。 */
private val DrawerGroupCornerRadius = 16.dp

/** 分组卡片左右外边距，同时是行内文字与卡片边缘的距离基准。 */
private val DrawerGroupHorizontalPadding = 16.dp

/** 紧凑行最小高度：与设置页 `PreferenceListItem` 一致。 */
private val DrawerRowMinHeight = 64.dp

/** 底部渐隐高度：留出列表末尾滚到操作区下方淡出的空间。 */
private val DrawerFooterHeight = 112.dp

/**
 * 历史对话抽屉 —— 基于 Material3 [ModalNavigationDrawer] 的侧边栏。
 *
 * 视觉与设置页 / "添加到聊天"面板同一套 One UI 语言：画布灰底承载白色分组卡片，
 * 列表行是紧凑设置行（标题 + 时间/末条消息，内缩分隔线），底部"设置 / 夜间模式"
 * 拼成一枚居中的胶囊操作组，列表末端用渐隐遮罩过渡到操作区。
 *
 * 作为根级组合包装 [content]，宿主屏幕（如 Scaffold）放在 [content] 内部。
 * 调用方持有 [drawerState] 与一个 [kotlinx.coroutines.CoroutineScope]，通过
 * `scope.launch { drawerState.open() }` 打开 / `drawerState.close()` 关闭抽屉。
 *
 * @param drawerState         控制抽屉开关状态
 * @param conversations       由 Route/ViewModel 下发的对话列表；组件本身不解析业务依赖。
 * @param currentSessionId    当前正在使用的 session id；与该 id 匹配的 [Conversation] 行 MUST 高亮且不可删除（不允许删自己）。
 * @param onConversationClick 点击某个对话时回调（实现方负责关闭抽屉）
 * @param recentState         搜索、多选和删除进度状态
 * @param onRecentAction      最近会话操作，由 ViewModel 处理并再次校验删除保护
 * @param onSettingsClick     点击底部"设置"按钮时回调（实现方负责关闭抽屉并跳转）
 * @param themeMode           当前夜间模式偏好（跟随系统/浅色/深色）
 * @param onThemeModeChange   点击夜间模式按钮循环到下一模式时回调（实现方负责持久化并应用主题）
 * @param modifier            修饰符
 * @param content             抽屉背后的主屏幕内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDrawer(
    drawerState: DrawerState,
    conversations: List<Conversation>,
    currentSessionId: String,
    conversationTaskStatuses: Map<String, ConversationTaskStatus> = emptyMap(),
    onConversationClick: (Conversation) -> Unit,
    recentState: RecentConversationsState = RecentConversationsState(),
    onRecentAction: (RecentConversationsAction) -> Unit = {},
    onSettingsClick: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showUpdateBadge: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val historyListState = rememberLazyListState()
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            // 稳定 key 会保留旧的可见项；打开最近列表时显式回到顶部，避免新会话藏在上方。
            historyListState.scrollToItem(0)
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                // 与设置页同底色：灰画布承载白色分组卡片。
                drawerContainerColor = preferenceCanvasColor(),
            ) {
                PreferenceStyleScope(PreferenceStyle.Compact) {
                    HistoryDrawerContent(
                        conversations = conversations,
                        currentSessionId = currentSessionId,
                        conversationTaskStatuses = conversationTaskStatuses,
                        onConversationClick = onConversationClick,
                        recentState = recentState,
                        onRecentAction = onRecentAction,
                        onSettingsClick = onSettingsClick,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        showUpdateBadge = showUpdateBadge,
                        listState = historyListState,
                    )
                }
            }
        },
        content = content
    )
}

// ── 抽屉内容 ─────────────────────────────────────────────────────

/**
 * 抽屉面板的内容：标题行 + 搜索框 + 历史对话分组卡片 + 底部操作区。
 */
@Composable
private fun HistoryDrawerContent(
    conversations: List<Conversation>,
    currentSessionId: String,
    conversationTaskStatuses: Map<String, ConversationTaskStatus>,
    onConversationClick: (Conversation) -> Unit,
    recentState: RecentConversationsState,
    onRecentAction: (RecentConversationsAction) -> Unit,
    onSettingsClick: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showUpdateBadge: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
) {
    val visible = recentState.visibleConversations(conversations)
    val eligible = deletableConversationIds(conversations, currentSessionId, conversationTaskStatuses)
    val selectedIds = recentState.selectedIds.intersect(eligible)
    val visibleEligible = visible.map { it.id }.toSet().intersect(eligible)
    val allSelected = visibleEligible.isNotEmpty() && selectedIds.containsAll(visibleEligible)
    val canvas = preferenceCanvasColor()
    // 与聊天页底部 composer 渐隐同源：列表末尾在操作区后方柔和消失。
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
        // 标题行与设置页 TopAppBar 同语：titleLarge 标题 + 右侧文字操作。
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = !recentState.isDeleting && (conversations.isNotEmpty() || recentState.isSelecting),
                onClick = {
                    onRecentAction(if (recentState.isSelecting) RecentConversationsAction.FinishSelection else RecentConversationsAction.StartSelection())
                },
            ) {
                Text(stringResource(if (recentState.isSelecting) R.string.chat_drawer_done else R.string.chat_drawer_manage))
            }
        }
        if (recentState.isSelecting) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            }
        }
        // 搜索框与分组卡片同底色，去掉描边与指示线。
        TextField(
            value = recentState.query,
            onValueChange = { onRecentAction(RecentConversationsAction.Search(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = !recentState.isDeleting,
            placeholder = { Text(stringResource(R.string.chat_drawer_search)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (recentState.query.isNotEmpty()) {
                    IconButton(onClick = { onRecentAction(RecentConversationsAction.Search("")) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_drawer_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(DrawerGroupCornerRadius),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = preferenceGroupCardColor(),
                unfocusedContainerColor = preferenceGroupCardColor(),
                disabledContainerColor = preferenceGroupCardColor(),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        Box(Modifier.weight(1f).navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = DrawerFooterHeight),
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
                        onLongClick = { onRecentAction(RecentConversationsAction.StartSelection(conversation.id)) },
                    )
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DrawerFooterHeight)
                    .background(bottomFadeBrush),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                // 操作组水平居中：与列表卡片左右留白对称。
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recentState.isSelecting) {
                    Button(
                        enabled = selectedIds.isNotEmpty() && !recentState.isDeleting,
                        onClick = { onRecentAction(RecentConversationsAction.RequestDeletion) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (recentState.isDeleting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (recentState.isDeleting) R.string.chat_drawer_deleting else R.string.chat_drawer_delete_count, selectedIds.size))
                    }
                } else {
                    DrawerFooterActionGroup(
                        themeMode = themeMode,
                        showUpdateBadge = showUpdateBadge,
                        onSettingsClick = onSettingsClick,
                        onThemeModeClick = { onThemeModeChange(themeMode.next()) },
                    )
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

// ── 底部操作组：设置 + 夜间模式 ────────────────────────────────

/**
 * 底部操作组：设置与夜间模式拼成同一枚胶囊（两段之间没有间隙，只用一条内缩分隔线区分），
 * 整组由调用方居中摆放。两段各自消费点击；夜间模式段点击循环 跟随系统→浅色→深色，
 * 图标与文案用 [AnimatedContent] 做上滑淡入淡出过渡，切换连贯不生硬。
 */
@Composable
private fun DrawerFooterActionGroup(
    themeMode: ThemeMode,
    showUpdateBadge: Boolean,
    onSettingsClick: () -> Unit,
    onThemeModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = preferenceGroupCardColor(),
    ) {
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onSettingsClick)
                    .padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.chat_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (showUpdateBadge) {
                    // 有未发现的新版本时在"设置"入口旁点亮提醒点。
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onThemeModeClick)
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val label = stringResource(themeModeLabelRes(themeMode))
                AnimatedContent(
                    targetState = themeMode,
                    transitionSpec = { themeModeSlideSpec() },
                    label = "themeModeIcon",
                    modifier = Modifier.semantics { contentDescription = label },
                ) { mode ->
                    Icon(
                        imageVector = themeModeIcon(mode),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedContent(
                    targetState = themeMode,
                    transitionSpec = { themeModeSlideSpec() },
                    label = "themeModeLabel",
                ) { mode ->
                    Text(
                        text = stringResource(themeModeLabelRes(mode)),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// 新内容从下方半格滑入并淡入、旧内容向上半格滑出并淡出，切换连贯不生硬。
private fun themeModeSlideSpec(): ContentTransform =
    slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)) togetherWith
        slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160))

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private fun themeModeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.chat_theme_system
    ThemeMode.LIGHT -> R.string.chat_theme_light
    ThemeMode.DARK -> R.string.chat_theme_dark
}

// ── 对话列表项 ──────────────────────────────────────────────────

/**
 * 分组卡片中的单个会话行：点击切换会话，长按进入多选；多选时点击整行切换勾选。
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    else Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true, name = "打开")
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

    AsssistantaiTheme {
        ChatDrawer(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
            conversations = sampleConversations,
            currentSessionId = "1",
            onConversationClick = { },
            onSettingsClick = { },
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = { },
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true, name = "打开（空状态）")
@Composable
private fun ChatDrawerEmptyPreview() {
    AsssistantaiTheme {
        ChatDrawer(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
            conversations = emptyList(),
            currentSessionId = "",
            onConversationClick = { },
            onSettingsClick = { },
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = { },
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true, name = "关闭")
@Composable
private fun ChatDrawerClosedPreview() {
    AsssistantaiTheme {
        ChatDrawer(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
            conversations = emptyList(),
            currentSessionId = "",
            onConversationClick = { },
            onSettingsClick = { },
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = { },
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

@Preview(name = "最近 · 紧凑多选", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun RecentSelectionPreview() = RecentContentPreview(
    recentState = RecentConversationsState(isSelecting = true, selectedIds = setOf("1", "3", "4")),
)

@Preview(name = "最近 · 搜索", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun RecentSearchPreview() = RecentContentPreview(recentState = RecentConversationsState(query = "相机"))

@Preview(name = "最近 · 深色", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun RecentDarkPreview() = RecentContentPreview(darkTheme = true)

@Preview(name = "最近 · 搜索无结果", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun RecentNoResultsPreview() = RecentContentPreview(recentState = RecentConversationsState(query = "不存在"))

@Composable
private fun RecentContentPreview(
    recentState: RecentConversationsState = RecentConversationsState(),
    darkTheme: Boolean = false,
) {
    val fixtures = listOf(
        "帮我开空调", "播放周杰伦夜曲", "微信小程序斗地主挂机未完成", "广州早茶推荐清单",
        "索尼 A7C 夜景参数推荐", "松下相机屏幕偏橙排查", "白鸽潭点向去", "配置高德地图 MCP Server",
        "周末散步路线", "整理本周工作笔记",
    ).mapIndexed { index, title ->
        Conversation(id = index.toString(), title = title, timestamp = System.currentTimeMillis() - (index + 9) * 3_600_000L)
    }
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                HistoryDrawerContent(
                    conversations = fixtures,
                    currentSessionId = "0",
                    conversationTaskStatuses = emptyMap(),
                    onConversationClick = {},
                    recentState = recentState,
                    onRecentAction = {},
                    onSettingsClick = {},
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    showUpdateBadge = true,
                )
            }
        }
    }
}
