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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
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
            SettingsSectionTitle(text = "MCP 服务", modifier = Modifier.padding(top = 12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.servers.isEmpty()) {
                    item {
                        Text(
                            "尚未配置服务器",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
                        )
                    }
                }
                items(state.servers, key = McpServer::id) { server ->
                    McpServerCard(
                        server = server,
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
            SettingsSectionTitle(text = "添加方式")
            SettingsNavigationCard(
                icon = Icons.Default.Add,
                title = "新建",
                subtitle = "手动配置 SSE 或 Streamable HTTP 服务",
                onClick = onCreate,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsNavigationCard(
                icon = Icons.Default.ContentPaste,
                title = "导入",
                subtitle = "粘贴 mcpServers JSON 配置",
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
            SettingsSectionTitle(text = "导入 MCP 配置")
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("粘贴包含 mcpServers 的 JSON 配置。支持 SSE 和 Streamable HTTP。")
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
                        enabled = state.importJson.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("导入 MCP 服务")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消")
                    }
                }
            }
            Text(
                "包含 command / args 的 stdio 配置会被跳过，因为 Android 应用无法安全启动本机命令。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${server.transport.label} · ${server.endpointUrl}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = server.isEnabled, onCheckedChange = onToggleEnabled)
    }
}

internal val McpTransport.label: String
    get() = when (this) {
        McpTransport.SSE -> "服务器发送事件 (SSE)"
        McpTransport.STREAMABLE_HTTP -> "可流式传输的 HTTP"
    }
