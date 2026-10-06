package github.ponyhuang.gimi.feature.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun McpServerListScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToTools: (String) -> Unit,
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.mcp_agent_mutation_blocked),
                    tone = PreferenceBannerTone.Error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.servers.isEmpty()) {
                McpEmptyState(
                    onCreateServer = onCreateServer,
                    onImportServers = onImportServers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // 服务器列表仅展示配置；工具信息由菜单进入独立页面。
                    items(state.servers, key = McpServer::id) { server ->
                        PreferenceGroupCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            McpServerCard(
                                server = server,
                                mutationEnabled = !state.isMutationBlocked,
                                menuExpanded = state.menuServerId == server.id,
                                capabilityState = state.capabilities[server.id],
                                onMenuChanged = { onAction(McpSettingsAction.ServerMenuChanged(if (it) server.id else null)) },
                                onEditClick = { onNavigateToEditor(server.id) },
                                onToggleEnabled = {
                                    onAction(McpSettingsAction.ToggleServer(server, it))
                                },
                                onViewTools = { onNavigateToTools(server.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 空态是首次用户的入口：直接给出两条路径（新建 / 导入），
 * 让首次配置跳过「添加方式」中转页。
 */
@Composable
private fun McpEmptyState(
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 空态主视觉用 MCP 品牌图标，与设置页入口保持同一标识。
        Icon(
            imageVector = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            stringResource(R.string.mcp_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.mcp_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onCreateServer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        ) {
            Text(stringResource(R.string.mcp_empty_action_new))
        }
        OutlinedButton(
            onClick = onImportServers,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text(stringResource(R.string.mcp_empty_action_import))
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    mutationEnabled: Boolean,
    menuExpanded: Boolean,
    capabilityState: ServerCapabilityState?,
    onMenuChanged: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onViewTools: () -> Unit,
) {
    val probeResult = (capabilityState as? ServerCapabilityState.Loaded)?.result
    // 探测成功后以服务端声明的名称/版本为准；本地配置只在服务端未提供时兜底。
    val displayName = probeResult?.serverName?.takeIf(String::isNotBlank) ?: server.name
    val displayDescription = probeResult?.serverVersion?.takeIf(String::isNotBlank)
        ?: server.description.takeIf(String::isNotBlank)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        McpListRow(
            icon = Icons.Default.Extension,
            title = displayName,
            description = displayDescription,
        ) {
            Box {
                IconButton(onClick = { onMenuChanged(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.mcp_more_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChanged(false) }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.mcp_edit_action)) },
                        onClick = { onMenuChanged(false); onEditClick() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mcp_view_tools)) },
                        onClick = { onMenuChanged(false); onViewTools() })
                }
            }
            Switch(
                checked = server.isEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = mutationEnabled,
            )
        }

    }
}

/** MCP 服务与其工具共用的两行列表外观；尾部操作仅用于服务卡片。 */
@Composable
private fun McpListRow(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 统一的圆形图标底座让服务与工具保持同一信息层级。
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerListScreenEmptyPreview() {
    AsssistantaiTheme {
        McpServerListScreen(
            state = McpSettingsUiState(),
            onAction = {},
            onNavigateToEditor = {},
            onNavigateToTools = {},
            onCreateServer = {},
            onImportServers = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerListScreenWithServersPreview() {
    val server = McpServer(
        id = "server-1",
        name = "GitHub MCP",
        description = "仓库与 Issue 操作",
        endpointUrl = "https://mcp.example.com/mcp",
        isEnabled = true,
    )
    AsssistantaiTheme {
        McpServerListScreen(
            state = McpSettingsUiState(
                servers = listOf(server),
                menuServerId = server.id,
                capabilities = mapOf(
                    server.id to ServerCapabilityState.Loaded(
                        result = McpProbeResult(
                            reachable = true,
                            tools = listOf(
                                McpToolSummary(name = "create_issue", description = "创建 Issue"),
                            ),
                        ),
                        serverSnapshot = server,
                    ),
                ),
            ),
            onAction = {},
            onNavigateToEditor = {},
            onNavigateToTools = {},
            onCreateServer = {},
            onImportServers = {},
        )
    }
}
