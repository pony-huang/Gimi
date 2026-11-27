package github.ponyhuang.gimi.feature.memory

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.memory.model.ManagedMemory
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.GimiTheme
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** 展示 Mem0 当前云端记忆；展开单项后提供删除和反馈操作的无状态页面。 */
@Composable
fun MemoryHistoryScreen(
    state: MemoryHistoryUiState,
    onAction: (MemoryHistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var negativeFeedbackMemory by remember { mutableStateOf<ManagedMemory?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.memories.size, state.hasNextPage, state.loadingNextPage) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull { it.index < state.memories.size }
                ?.index ?: -1
        }
            .map { lastVisibleMemoryIndex ->
                shouldLoadNextMemoryPage(
                    lastVisibleMemoryIndex = lastVisibleMemoryIndex,
                    memoryCount = state.memories.size,
                    hasNextPage = state.hasNextPage,
                    loadingNextPage = state.loadingNextPage,
                )
            }
            .distinctUntilChanged()
            .filter { it }
            .collect { onAction(MemoryHistoryAction.LoadNextPage) }
    }
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        // 下拉刷新；isRefreshing 由 ViewModel 在 refresh() 期间置位。
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onAction(MemoryHistoryAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.memories.isNotEmpty()) {
                PreferenceGroupCard(modifier = Modifier.padding(top = 8.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(state.memories, key = { _, memory -> memory.id }) { index, memory ->
                            MemoryHistoryItem(
                                memory = memory,
                                expanded = memory.id in state.expandedMemoryIds,
                                operating = state.operatingMemoryId == memory.id,
                                showDivider = index != state.memories.lastIndex,
                                onClick = { onAction(MemoryHistoryAction.ToggleExpanded(memory.id)) },
                                onPositiveFeedback = {
                                    onAction(MemoryHistoryAction.SubmitFeedback(memory, ManagedMemoryFeedback.POSITIVE, null))
                                },
                                onNegativeFeedback = { negativeFeedbackMemory = memory },
                                onDelete = { onAction(MemoryHistoryAction.RequestDelete(memory)) },
                            )
                        }
                        if (state.loadingNextPage) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            } else if (!state.refreshing) {
                Text(
                    text = stringResource(R.string.memory_history_empty),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    state.pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { onAction(MemoryHistoryAction.DismissDelete) },
            title = { Text(stringResource(R.string.memory_history_delete_title)) },
            text = { Text(stringResource(R.string.memory_history_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(MemoryHistoryAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.memory_history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MemoryHistoryAction.DismissDelete) }) {
                    Text(stringResource(R.string.memory_history_cancel))
                }
            },
        )
    }
    negativeFeedbackMemory?.let { memory ->
        NegativeFeedbackDialog(
            onDismiss = { negativeFeedbackMemory = null },
            onSubmit = { reason ->
                onAction(MemoryHistoryAction.SubmitFeedback(memory, ManagedMemoryFeedback.NEGATIVE, reason))
                negativeFeedbackMemory = null
            },
        )
    }
}

@Composable
private fun MemoryHistoryItem(
    memory: ManagedMemory,
    expanded: Boolean,
    operating: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onPositiveFeedback: () -> Unit,
    onNegativeFeedback: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 收起态保持单行，展开后才展示完整记忆与操作。
            Text(
                text = memory.text,
                style = if (expanded) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.memory_history_updated_at, memory.updatedAt?.toString().orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = onPositiveFeedback, enabled = !operating) {
                        Icon(Icons.Default.ThumbUp, stringResource(R.string.memory_history_positive_feedback))
                    }
                    FilledTonalIconButton(onClick = onNegativeFeedback, enabled = !operating) {
                        Icon(Icons.Default.ThumbDown, stringResource(R.string.memory_history_negative_feedback))
                    }
                    FilledTonalIconButton(onClick = onDelete, enabled = !operating) {
                        Icon(Icons.Default.Delete, stringResource(R.string.memory_history_delete))
                    }
                }
            }
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** 当用户已滚动到末尾附近且仍有下一页时，触发续载。 */
internal fun shouldLoadNextMemoryPage(
    lastVisibleMemoryIndex: Int,
    memoryCount: Int,
    hasNextPage: Boolean,
    loadingNextPage: Boolean,
): Boolean = hasNextPage &&
    !loadingNextPage &&
    memoryCount > 0 &&
    lastVisibleMemoryIndex >= memoryCount - LOAD_NEXT_PAGE_THRESHOLD

private const val LOAD_NEXT_PAGE_THRESHOLD = 2

@Composable
private fun NegativeFeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (String?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_history_negative_feedback)) },
        text = {
            PreferenceTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.memory_history_feedback_reason)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason.trim().takeIf(String::isNotEmpty)) }) {
                Text(stringResource(R.string.memory_history_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_history_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryHistoryScreenEmptyPreview() {
    GimiTheme {
        MemoryHistoryScreen(
            state = MemoryHistoryUiState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryHistoryScreenWithMemoriesPreview() {
    GimiTheme {
        MemoryHistoryScreen(
            state = MemoryHistoryUiState(
                memories = listOf(
                    ManagedMemory(
                        id = "memory-1",
                        text = "用户偏好使用 Kotlin 开发 Android 项目，并遵循模块化架构。",
                        createdAt = Instant.parse("2026-01-01T08:00:00Z"),
                        updatedAt = Instant.parse("2026-01-02T08:00:00Z"),
                    ),
                    ManagedMemory(
                        id = "memory-2",
                        text = "用户所在时区为东八区，常用中文交流。",
                        createdAt = Instant.parse("2026-01-03T08:00:00Z"),
                        updatedAt = null,
                    ),
                ),
                expandedMemoryIds = setOf("memory-1"),
            ),
            onAction = {},
        )
    }
}
