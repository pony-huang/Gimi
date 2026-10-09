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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import github.ponyhuang.gimi.feature.chat.ConversationTaskStatus
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 历史对话抽屉 —— 基于 Material3 [ModalNavigationDrawer] 的侧边栏。
 *
 * 作为根级组合包装 [content]，宿主屏幕（如 Scaffold）放在 [content] 内部。
 * 调用方持有 [drawerState] 与一个 [kotlinx.coroutines.CoroutineScope]，通过
 * `scope.launch { drawerState.open() }` 打开 / `drawerState.close()` 关闭抽屉。
 *
 * @param drawerState         控制抽屉开关状态
 * @param conversations       由 Route/ViewModel 下发的对话列表；组件本身不解析业务依赖。
 * @param currentSessionId    当前正在使用的 session id；与该 id 匹配的 [Conversation] 行 MUST 显示一个禁用的删除按钮（不允许删自己）。
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
        drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
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
        },
        content = content
    )
}

// ── 抽屉内容 ─────────────────────────────────────────────────────

/**
 * 抽屉面板的内容：历史对话列表 + 底部"设置"入口。
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
    val surface = MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxHeight().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_history_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
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
        OutlinedTextField(
            value = recentState.query,
            onValueChange = { onRecentAction(RecentConversationsAction.Search(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            enabled = !recentState.isDeleting,
            placeholder = { Text(stringResource(R.string.chat_drawer_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (recentState.query.isNotEmpty()) {
                    IconButton(onClick = { onRecentAction(RecentConversationsAction.Search("")) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_drawer_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
        )
        if (recentState.isSelecting) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_drawer_selected_count, selectedIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = visibleEligible.isNotEmpty() && !recentState.isDeleting,
                    onClick = { onRecentAction(RecentConversationsAction.ToggleSelectAll) },
                ) {
                    Text(stringResource(if (allSelected) R.string.chat_drawer_deselect_all else R.string.chat_drawer_select_all))
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.weight(1f).navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 116.dp),
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(if (recentState.query.isBlank()) R.string.chat_drawer_empty_history else R.string.chat_drawer_search_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp),
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
                        onClick = {
                            if (recentState.isSelecting) onRecentAction(RecentConversationsAction.ToggleSelection(conversation.id))
                            else onConversationClick(conversation)
                        },
                        onLongClick = { onRecentAction(RecentConversationsAction.StartSelection(conversation.id)) },
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            // 渐变覆盖列表末端而非插入分隔线；底部留白保证最后一项可滚到按钮上方。
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(112.dp)
                    .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface, surface))),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recentState.isSelecting) {
                    Button(
                        enabled = selectedIds.isNotEmpty() && !recentState.isDeleting,
                        onClick = { onRecentAction(RecentConversationsAction.RequestDeletion) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
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
                    Surface(
                        onClick = onSettingsClick,
                        shape = CircleShape,
                        color = chatCapsuleColor(),
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.height(48.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
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
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 两枚胶囊独立消费点击；主题入口循环 跟随系统→浅色→深色。
                    ThemeModeButton(
                        mode = themeMode,
                        onClick = { onThemeModeChange(themeMode.next()) },
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

// ── 夜间模式循环按钮 ────────────────────────────────────────────

/**
 * 抽屉底部夜间模式按钮：单枚按钮点击循环 跟随系统→浅色→深色。图标与文案用
 * [AnimatedContent] 做上滑淡入淡出过渡，切换连贯不生硬；按钮始终反映当前模式，
 * 点回"跟随系统"即恢复自动跟随。
 */
@Composable
private fun ThemeModeButton(
    mode: ThemeMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(themeModeLabelRes(mode))
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(percent = 50),
        color = chatCapsuleColor(),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { themeModeSlideSpec() },
                label = "themeModeIcon",
            ) { m ->
                Icon(
                    imageVector = themeModeIcon(m),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedContent(
                targetState = mode,
                transitionSpec = { themeModeSlideSpec() },
                label = "themeModeLabel",
            ) { m ->
                Text(
                    text = stringResource(themeModeLabelRes(m)),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
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
 * 单个紧凑会话行：点击切换会话，长按进入多选；多选时点击整行切换勾选。
 *
 * @param isCurrent 当此行是当前正在使用的会话时 MUST 禁用删除按钮（防止用户把自己正在用的会话删掉）。
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中态用品牌 primaryContainer：深/浅主题下都与抽屉 surface 拉开明度差，
    // secondaryContainer 在深色模式 (#383838 on #2F2F2F) 几乎看不出哪条是当前会话。
    val highlighted = if (isSelecting) isSelected else isCurrent
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .then(
                if (isSelecting) Modifier.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
            )
            // 副标题是辨识度的来源：标题可能都是"新对话"，时间 + 末条消息才能区分开。
            Text(
                text = if (isCurrent) stringResource(R.string.chat_drawer_current_session_time, formatRelativeTime(conversation.timestamp)) else conversation.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = (if (enabled) contentColor else contentColor.copy(alpha = 0.45f))
                    .copy(alpha = 0.6f),
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
        Surface {
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
            )
        }
    }
}
