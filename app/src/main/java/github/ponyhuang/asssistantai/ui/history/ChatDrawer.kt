package github.ponyhuang.asssistantai.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.model.Conversation
import github.ponyhuang.asssistantai.ui.chat.ChatViewModel
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme

/**
 * 历史对话抽屉 —— 基于 Material3 [ModalNavigationDrawer] 的侧边栏。
 *
 * 作为根级组合包装 [content]，宿主屏幕（如 Scaffold）放在 [content] 内部。
 * 调用方持有 [drawerState] 与一个 [kotlinx.coroutines.CoroutineScope]，通过
 * `scope.launch { drawerState.open() }` 打开 / `drawerState.close()` 关闭抽屉。
 *
 * @param drawerState         控制抽屉开关状态
 * @param conversations       对话列表。传 `null` 时由组件内部通过 `hiltViewModel()` 拿到 [ChatViewModel]
 *                            并订阅 `conversations` StateFlow —— 这样订阅只活在抽屉子树上，宿主屏幕
 *                            无需订阅，会话列表增删不会触发整屏重组。预览 / 单元测试场景可显式传入固定列表。
 * @param currentSessionId    当前正在使用的 session id；与该 id 匹配的 [Conversation] 行 MUST 显示一个禁用的删除按钮（不允许删自己）。
 * @param onConversationClick 点击某个对话时回调（实现方负责关闭抽屉）
 * @param onDeleteClick       删除某个对话时回调（实现方应再次校验不是 currentSessionId）
 * @param onSettingsClick     点击底部"设置"按钮时回调（实现方负责关闭抽屉并跳转）
 * @param modifier            修饰符
 * @param content             抽屉背后的主屏幕内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDrawer(
    drawerState: DrawerState,
    conversations: List<Conversation>? = null,
    currentSessionId: String,
    onConversationClick: (Conversation) -> Unit,
    onDeleteClick: (Conversation) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 把 conversations 订阅下沉到抽屉自己：宿主屏幕无需订阅，会话列表增删不会触发整屏重组。
    // 当抽屉关闭时，ModalNavigationDrawer 内部对 drawerContent 是 frozen 状态，订阅不会持续触发。
    // 仅在 caller 未传 `conversations` 时才解析 hiltViewModel，预览场景不会触发 Hilt 调用。
    val resolvedConversations = if (conversations != null) {
        conversations
    } else {
        val vm: ChatViewModel = hiltViewModel()
        vm.uiState.collectAsStateWithLifecycle().value.conversations
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                HistoryDrawerContent(
                    conversations = resolvedConversations,
                    currentSessionId = currentSessionId,
                    onConversationClick = onConversationClick,
                    onDeleteClick = onDeleteClick,
                    onSettingsClick = onSettingsClick,
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
    onConversationClick: (Conversation) -> Unit,
    onDeleteClick: (Conversation) -> Unit,
    onSettingsClick: () -> Unit,
) {
    var menuConversation by remember { mutableStateOf<Conversation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding(),
    ) {
        Text(
            text = "AsssistantAI",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 24.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        ) {
            if (conversations.isEmpty()) {
                item {
                    Text(
                        text = "暂无历史对话",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                }
            }
            items(
                items = conversations,
                key = { it.id }
            ) { conversation ->
                ConversationListItem(
                    conversation = conversation,
                    isCurrent = conversation.id == currentSessionId,
                    onClick = { onConversationClick(conversation) },
                    onLongClick = { menuConversation = conversation },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // ── 底部固定 - 设置入口 ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onSettingsClick)
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 20.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp),
            )
        }

        menuConversation?.let { conversation ->
            ConversationActionSheet(
                isCurrent = conversation.id == currentSessionId,
                onDismiss = { menuConversation = null },
                onDeleteClick = {
                    onDeleteClick(conversation)
                    menuConversation = null
                },
            )
        }
    }
}

// ── 对话列表项 ──────────────────────────────────────────────────

/**
 * 单个对话列表行：点击切换会话，长按打开操作面板。
 *
 * @param isCurrent 当此行是当前正在使用的会话时 MUST 禁用删除按钮（防止用户把自己正在用的会话删掉）。
 */
@Composable
private fun ConversationListItem(
    conversation: Conversation,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conversation.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationActionSheet(
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(if (isCurrent) "当前会话不可删除" else "删除对话") },
            supportingContent = {
                if (isCurrent) Text("请先切换到其他会话")
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            modifier = Modifier.combinedClickable(
                enabled = !isCurrent,
                onClick = onDeleteClick,
            ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
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
            onDeleteClick = { },
            onSettingsClick = { }
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
            onDeleteClick = { },
            onSettingsClick = { }
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
            onDeleteClick = { },
            onSettingsClick = { }
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
