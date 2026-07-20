package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer

@Composable
fun ToolAuthorizationRoute(
    modifier: Modifier = Modifier,
    viewModel: ToolAuthorizationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ToolAuthorizationScreen(state, viewModel::onAction, modifier)
}

@Composable
fun ToolAuthorizationScreen(
    state: ToolAuthorizationUiState,
    onAction: (ToolAuthorizationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "已启用 ${state.enabledCount} / ${state.tools.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.isMutationBlocked || state.notice != null) {
                        Text(
                            text = state.notice ?: "Agent 任务进行中，请先停止任务后再修改。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onAction(ToolAuthorizationAction.SetAllEnabled(true)) },
                            enabled = !state.isMutationBlocked,
                            modifier = Modifier.weight(1f),
                        ) { Text("全部启用") }
                        Button(
                            onClick = { onAction(ToolAuthorizationAction.SetAllEnabled(false)) },
                            enabled = !state.isMutationBlocked,
                            modifier = Modifier.weight(1f),
                        ) { Text("全部关闭") }
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { onAction(ToolAuthorizationAction.Search(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索工具") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                }
            }
            if (state.visibleTools.isEmpty()) {
                item {
                    Text(
                        text = if (state.query.isBlank()) "暂无可配置的本地工具" else "没有匹配的工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(state.visibleTools, key = ToolDescriptor::id) { tool ->
                    ToolAuthorizationRow(tool, enabled = !state.isMutationBlocked) { enabled ->
                        onAction(ToolAuthorizationAction.SetEnabled(tool.id, enabled))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolAuthorizationRow(
    tool: ToolDescriptor,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onEnabledChange(!tool.isEnabled) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(
            checked = tool.isEnabled,
            onCheckedChange = onEnabledChange,
            enabled = enabled,
        )
    }
}
