package github.ponyhuang.asssistantai.feature.mcp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.feature.mcp.R
import github.ponyhuang.asssistantai.ui.settings.SettingsCard
import github.ponyhuang.asssistantai.ui.settings.SettingsNavigationCard
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun McpServerListScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSectionTitle(
                text = stringResource(R.string.mcp_section_servers),
                modifier = Modifier.padding(top = 12.dp),
            )
            if (state.isMutationBlocked || !state.notice.isNullOrBlank()) {
                Text(
                    text = state.notice?.takeUnless { it.isBlank() }
                        ?: stringResource(R.string.mcp_agent_mutation_blocked),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.servers.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.mcp_no_servers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
                        )
                    }
                }
                items(state.servers, key = McpServer::id) { server ->
                    McpServerCard(
                        server = server,
                        mutationEnabled = !state.isMutationBlocked,
                        onClick = { onNavigateToEditor(server.id) },
                        onToggleEnabled = {
                            onAction(McpSettingsAction.ToggleServer(server, it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun McpServerAddOptionsScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionTitle(text = stringResource(R.string.mcp_section_add_methods))
            SettingsNavigationCard(
                icon = Icons.Default.Add,
                title = stringResource(R.string.mcp_method_new_title),
                subtitle = stringResource(R.string.mcp_method_new_subtitle),
                onClick = onCreate,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsNavigationCard(
                icon = Icons.Default.ContentPaste,
                title = stringResource(R.string.mcp_method_import_title),
                subtitle = stringResource(R.string.mcp_method_import_subtitle),
                onClick = onImport,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
fun McpServerImportScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionTitle(text = stringResource(R.string.mcp_section_import_mcp))
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.mcp_import_help))
                    OutlinedTextField(
                        value = state.importJson,
                        onValueChange = { onAction(McpSettingsAction.ImportJsonChanged(it)) },
                        label = { Text("MCP JSON") },
                        placeholder = { Text("{\n  \"mcpServers\": { ... }\n}") },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.importResult?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { onAction(McpSettingsAction.ImportServers) },
                        enabled = state.importJson.isNotBlank() && !state.isMutationBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.mcp_import_action))
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.mcp_cancel))
                    }
                }
            }
            Text(
                stringResource(R.string.mcp_stdio_skip_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    mutationEnabled: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                server.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 完整 URL 截断后毫无信息量，只保留 host；完整地址在编辑页可见。
                "${server.transport.displayName()} · ${server.endpointHost()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = server.isEnabled,
            onCheckedChange = onToggleEnabled,
            enabled = mutationEnabled,
        )
    }
}

@Composable
internal fun McpTransport.displayName(): String = when (this) {
    McpTransport.SSE -> stringResource(R.string.mcp_transport_sse)
    McpTransport.STREAMABLE_HTTP -> stringResource(R.string.mcp_transport_streamable_http)
}

/** 从 endpoint URL 提取 host 用于列表副标题；解析失败时回退到原始 URL。 */
private fun McpServer.endpointHost(): String =
    runCatching { java.net.URI(endpointUrl).host }
        .getOrNull()
        ?.takeUnless { it.isNullOrBlank() }
        ?: endpointUrl
