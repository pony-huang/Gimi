package github.ponyhuang.gimi.feature.a2a

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun RemoteAgentListScreen(
    state: A2aSettingsUiState,
    onAction: (A2aSettingsAction) -> Unit,
    onCreateAgent: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.a2a_agent_mutation_blocked),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.agents.isEmpty()) {
                RemoteAgentEmptyState(
                    onCreateAgent = onCreateAgent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.agents, key = RemoteAgentConfig::id) { agent ->
                        PreferenceGroupCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            RemoteAgentCard(
                                agent = agent,
                                mutationEnabled = !state.isMutationBlocked,
                                menuExpanded = state.menuServerId == agent.id,
                                onMenuChanged = { onAction(A2aSettingsAction.AgentMenuChanged(if (it) agent.id else null)) },
                                onEditClick = { onNavigateToEditor(agent.id) },
                                onToggleEnabled = {
                                    onAction(A2aSettingsAction.ToggleAgent(agent, it))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 空态是首次用户的入口：说明远程 agent 的用途并引导新建。 */
@Composable
private fun RemoteAgentEmptyState(
    onCreateAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            stringResource(R.string.a2a_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.a2a_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onCreateAgent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        ) {
            Text(stringResource(R.string.a2a_empty_action_new))
        }
    }
}

@Composable
private fun RemoteAgentCard(
    agent: RemoteAgentConfig,
    mutationEnabled: Boolean,
    menuExpanded: Boolean,
    onMenuChanged: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = GimiListDefaults.VerticalPadding),
    ) {
        RemoteAgentListRow(
            icon = Icons.Default.Hub,
            title = agent.name,
            // 优先展示卡片官方描述；从未成功探测过时回退到服务地址。
            description = agent.cardSnapshot?.description?.takeIf { it.isNotBlank() }
                ?: agent.endpointUrl,
        ) {
            Box {
                IconButton(onClick = { onMenuChanged(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.a2a_more_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChanged(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.a2a_edit_action)) },
                        onClick = { onMenuChanged(false); onEditClick() },
                    )
                }
            }
            GimiSwitch(
                checked = agent.isEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = mutationEnabled,
            )
        }
    }
}

/** 远程 agent 卡片的两行列表外观；尾部操作仅用于配置卡片。 */
@Composable
private fun RemoteAgentListRow(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent()
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentListScreenEmptyPreview() {
    AsssistantaiTheme {
        RemoteAgentListScreen(
            state = A2aSettingsUiState(),
            onAction = {},
            onCreateAgent = {},
            onNavigateToEditor = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentListScreenWithAgentsPreview() {
    AsssistantaiTheme {
        RemoteAgentListScreen(
            state = A2aSettingsUiState(
                agents = listOf(
                    RemoteAgentConfig(
                        id = "agent-1",
                        name = "weather_agent",
                        endpointUrl = "http://192.168.1.8:8001/a2a/weather",
                        isEnabled = true,
                    ),
                    RemoteAgentConfig(
                        id = "agent-2",
                        name = "calendar_agent",
                        endpointUrl = "https://a2a.example.com",
                        isEnabled = false,
                    ),
                ),
                menuServerId = "agent-1",
            ),
            onAction = {},
            onCreateAgent = {},
            onNavigateToEditor = {},
        )
    }
}
