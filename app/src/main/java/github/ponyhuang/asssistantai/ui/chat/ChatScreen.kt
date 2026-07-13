package github.ponyhuang.asssistantai.ui.chat

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import github.ponyhuang.asssistantai.model.Message
import github.ponyhuang.asssistantai.model.MessageRole
import github.ponyhuang.asssistantai.ui.history.ChatDrawer
import github.ponyhuang.asssistantai.ui.model.detail.ModelServiceDetailRoute
import github.ponyhuang.asssistantai.ui.model.list.ModelServiceListRoute
import github.ponyhuang.asssistantai.ui.navigation.AppRoute
import github.ponyhuang.asssistantai.ui.navigation.SettingsScaffold
import github.ponyhuang.asssistantai.ui.settings.SettingsScreen
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 聊天界面 Scaffold：
 * - TopAppBar：模型选择器（[ModelTitleAndPicker]）+ 抽屉按钮（[onOpenDrawer]）+ 新建对话（[onNewConversation]）
 * - LazyColumn：消息流 + 流式输入自动跟随滚动 + 用户离开底部时显示「回到最新」FAB
 * - ChatInputBar：草稿由输入组件管理，发送按钮在流式期间被禁用。
 *
 * ## 滚动 / FAB 自洽
 * 列表滚动状态、流式跟随信号、FAB 可见性、`didInitialScroll` 首次守卫都内化在本 Composable 内，
 * 宿主不直接持有 `LazyListState`，避免不相关的状态变化触发整屏重组。
 *
 * ## [isStreaming] 的两个用途
 * - **"新建对话"按钮** — 流式输出期间保留旧 runner 不动；[onNewConversation] 由宿主自行实现
 *   "recreate runner + 重置会话"，但调用时机由本 Composable 决定。
 * - **"发送"按钮** — 流式期间保持禁用，实际发送由 [ChatComposer] 触发。
 *
 * ## 宿主契约
 * 宿主负责：草稿随 session 重置（`remember(currentSessionId)`）、抽屉开合、session 切换 /
 * 删除、`viewModel.send` 实际调用、模型服务切换时的 runner 重建。本 Composable 不持有这些
 * 副作用。
 */
@SuppressLint("FrequentlyChangingValue")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ChatScaffold(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
) {

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = state.messages
    val isStreaming = state.isStreaming
    val pendingToolConfirmation by viewModel.pendingToolConfirmation.collectAsStateWithLifecycle()
    // 只要用户仍停留在底部，就让流式内容增长持续跟随；用户向上浏览历史时则停止抢占滚动。
    var shouldFollowLatest by remember { mutableStateOf(true) }
    val latestItemIndex by rememberUpdatedState(messages.size)

    LaunchedEffect(messages.size) {
        if (state.getCurrentUserMessage() != null) {
            delay(100.milliseconds)
            listState.animateScrollToItem(messages.size)
        }
    }

    // `TextContent` 从 channel 接收增量后只会改变气泡尺寸，不会改变 messages 引用。
    // 监听可见项的布局边界，确保每次气泡增长到新行时都能把底部锚点带回视口。
    LaunchedEffect(listState, isStreaming) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { item ->
                item.index to (item.offset + item.size)
            }
        }.collect {
            if (isStreaming && shouldFollowLatest) {
                listState.scrollToItem(latestItemIndex)
            }
        }
    }

    // 仅在一次实际滚动结束时更新“跟随最新”意图，避免流式内容自身增长时误判为用户离开底部。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    shouldFollowLatest = !listState.canScrollForward
                }
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ChatTopBar(
                viewModel = viewModel,
                isStreaming = isStreaming,
                onOpenDrawer = onOpenDrawer,
                onOpenSettings = onOpenSettings,
                onNewConversation = onNewConversation,
            )
        },
        bottomBar = {
            val background = MaterialTheme.colorScheme.background
            val fadeBrush = remember(background) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        background.copy(alpha = 0.8f),
                        background.copy(alpha = 0.95f),
                        background,
                    ),
                )
            }
            ChatComposer(
                modifier = Modifier.background(fadeBrush),
                onSendClick = { data ->
                    viewModel.send(data.text, data.attachments)
                },
                onStopClick = viewModel::stopStreaming,
                isGenerating = isStreaming,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = listState.canScrollForward,
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        shouldFollowLatest = true
                        scope.launch {
                            listState.animateScrollToItem(messages.size)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "回到最新",
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.isInitializing,
        ) { isInitializing ->
            if (isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 消息列表
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                        contentType = { msg ->
                            when {
                                msg.error != null -> "error"
                                msg.role == MessageRole.User -> "user"
                                msg.partial -> "assistant_streaming"
                                else -> "assistant_complete"
                            }
                        },
                    ) { msg ->
                        MessageRow(
                            message = msg,
                            partChannelProvider = viewModel::partChannelFor
                        )
                    }
                    item(
                        key = "chat-bottom-anchor",
                        contentType = "bottom_anchor",
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

        }

    }
    pendingToolConfirmation?.let { request ->
        IntentConfirmationDialog(
            title = request.title,
            summary = request.summary,
            confirmLabel = "Execute",
            onConfirm = { viewModel.respondToToolConfirmation(confirmed = true) },
            onDismiss = { viewModel.respondToToolConfirmation(confirmed = false) },
        )
    }
}

@Composable
private fun IntentConfirmationDialog(
    title: String,
    summary: String,
    confirmLabel: String = "Continue",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(summary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * 应用主屏幕：聊天界面 + 历史对话抽屉。
 *
 * 状态来源：[ChatViewModel.uiState] — 一次 `collectAsStateWithLifecycle` 拿到完整聊天会话 UI 状态，
 * 再按字段（`messages` / `isStreaming` / `sessionId` / `conversations`）向下传递，确保抽屉
 * 与聊天内容共享同一个 [ChatViewModel] 的会话状态；模型选择（服务列表 / 当前选择）的订阅仍下沉到
 * [ModelTitleAndPicker]。
 *
 * 聊天区复用 [ChatScaffold] — 本 Composable 只剩"路由 + 历史抽屉 + 输入草稿按 session
 * 重置 + 副作用（send / recreate runner / reset / drawer 开关）"这几个宿主契约。
 *
 * 发送动作：调用 `viewModel.send(text)`，取消上一次未完成的发送。
 * 会话切换 / 删除：调 `viewModel.switchSession(id)` / `viewModel.deleteConversation(id)`，由 ViewModel 经 [github.ponyhuang.asssistantai.data.ConversationRepository] 落 Room。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun MainScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    // Chat content and the history drawer use this single source of truth.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId

    // 抽屉状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 可恢复的 Navigation 3 返回栈
    val backStack = rememberNavBackStack(AppRoute.Chat)
    val goBack: () -> Unit = {
        backStack.removeLastOrNull()
    }
    val returnToChat = {
        while (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    // 首屏自动加载会话列表 + 恢复上次会话（首次安装时兜底建空会话）
    LaunchedEffect(Unit) {
        viewModel.refreshConversations()
        viewModel.restoreOrCreateSession()
    }

    ChatDrawer(
        drawerState = drawerState,
        // The drawer and the content must share this exact ChatViewModel state so selecting a
        // history row always updates the message list for that row's session.
        conversations = uiState.conversations,
        currentSessionId = currentSessionId,
        onConversationClick = { conversation ->
            viewModel.switchSession(conversation.id)
            returnToChat()
            scope.launch { drawerState.close() }
        },
        onDeleteClick = { conversation ->
            viewModel.deleteConversation(conversation.id)
        },
        onSettingsClick = {
            returnToChat()
            backStack.add(AppRoute.Settings)
            scope.launch { drawerState.close() }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryProvider = { route ->
                NavEntry(route) {
                    when (route) {
                        AppRoute.Chat -> ViewModelStore(route) {
                            ChatScaffold(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { backStack.add(AppRoute.Settings) },
                                onNewConversation = {
                                    viewModel.recreateRunner()
                                    viewModel.reset()
                                },
                            )
                        }

                        AppRoute.Settings -> SettingsScreen(
                            onBack = goBack,
                            onNavigateToModelService = {
                                backStack.add(AppRoute.ModelServiceList)
                            },
                        )

                        AppRoute.ModelServiceList -> SettingsScaffold(
                            title = "模型服务",
                            onBack = goBack,
                        ) { modifier ->
                            ModelServiceListRoute(
                                onNavigateToDetail = { serviceId ->
                                    backStack.add(AppRoute.ModelServiceDetail(serviceId))
                                },
                                modifier = modifier,
                            )
                        }

                        is AppRoute.ModelServiceDetail -> SettingsScaffold(
                            title = "服务详情",
                            onBack = goBack,
                        ) { modifier ->
                            ModelServiceDetailRoute(
                                serviceId = route.serviceId,
                                onBack = goBack,
                                modifier = modifier,
                            )
                        }
                    }
                }
            },
            // Match Android's conventional navigation direction: forward content enters from
            // the right; back and predictive-back reveal the previous destination from the left.
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            },
            popTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            },
        )
    }
}

/** Floating chat header modelled after the compact ChatGPT mobile controls. */
@Composable
private fun ChatTopBar(
    viewModel: ChatViewModel,
    isStreaming: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenDrawer,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "打开历史对话抽屉",
                )
            }
        }

        ModelTitleAndPicker(
            viewModel = viewModel,
            isStreaming = isStreaming,
            modifier = Modifier.weight(1f),
        )

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "设置",
                            modifier = Modifier.size(24.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                IconButton(
                    onClick = onNewConversation,
                    enabled = !isStreaming,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "新建对话",
                    )
                }
            }
        }
    }
}

/**
 * 单条消息的派发：错误消息走 `ErrorBubble`，其它走 `MessageBubble`。
 *
 * `partChannelProvider` 用于让 `MessageBubble` 订阅 reducer 暴露的文本增量 channel，
 * 从而把每一段 partial 文本作为 chunk 推给 `StreamingMarkdownState.append(...)`，
 * 实现逐字符级流式渲染。
 */
@Composable
private fun MessageRow(
    message: Message,
    partChannelProvider: (partId: String) -> ReceiveChannel<String>?,
    modifier: Modifier = Modifier
) {
    if (message.error != null) {
        ErrorBubble(message = message, modifier = modifier)
    } else {
        MessageBubble(
            message = message,
            partChannelProvider = partChannelProvider,
            modifier = modifier
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AsssistantaiTheme {
        MainScreen()
    }
}
