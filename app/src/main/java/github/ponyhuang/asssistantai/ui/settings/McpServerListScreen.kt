package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.McpServerConfig
import github.ponyhuang.asssistantai.data.McpServerRepository
import github.ponyhuang.asssistantai.data.McpTransport
import javax.inject.Inject

@HiltViewModel
class McpServerListViewModel @Inject constructor(
    private val repository: McpServerRepository,
) : ViewModel() {
    val servers = repository.servers
    fun save(server: McpServerConfig) = repository.save(server)
    fun delete(id: String) = repository.delete(id)
    fun importJson(json: String) = repository.importJson(json)
}

@Composable
fun McpServerListScreen(
    onNavigateToEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerListViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    SettingsPageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSectionTitle(text = "MCP 服务", modifier = Modifier.padding(top = 12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (servers.isEmpty()) item {
                    Text(
                        "尚未配置服务器",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
                    )
                }
                items(servers, key = { it.id }) { server ->
                    McpServerCard(
                        server = server,
                        onClick = { onNavigateToEditor(server.id) },
                        onToggleEnabled = { viewModel.save(server.copy(isEnabled = it)) },
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
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerListViewModel = hiltViewModel(),
) {
    var json by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    SettingsPageContainer(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionTitle(text = "导入 MCP 配置")
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("粘贴包含 mcpServers 的 JSON 配置。支持 SSE 和 Streamable HTTP。")
                    OutlinedTextField(
                        value = json,
                        onValueChange = { json = it; result = null },
                        label = { Text("MCP JSON") },
                        placeholder = { Text("{\n  \"mcpServers\": { ... }\n}") },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    result?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(
                        onClick = {
                            val importResult = viewModel.importJson(json)
                            result = importResult.message
                            if (importResult.error == null && importResult.imported > 0) onBack()
                        },
                        enabled = json.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("导入 MCP 服务") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("取消") }
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
    server: McpServerConfig,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
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
        Switch(
            checked = server.isEnabled,
            onCheckedChange = onToggleEnabled,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun McpServerEditorScreen(
    serverId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerListViewModel = hiltViewModel(),
) {
    val initial = serverId?.let { viewModel.servers.value.firstOrNull { server -> server.id == it } }
        ?: McpServerConfig()
    val isNew = serverId == null
    var name by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.name) }
    var description by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.description) }
    var endpoint by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.endpointUrl) }
    var transport by remember(initial.id) { mutableStateOf(initial.transport) }
    var token by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.bearerToken) }
    var headers by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.headers) }
    var enabled by androidx.compose.runtime.remember(initial.id) { androidx.compose.runtime.mutableStateOf(initial.isEnabled) }
    SettingsPageContainer(modifier) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionTitle(text = "基本信息")
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
            OutlinedTextField(name, { name = it }, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
            var transportMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = transportMenuExpanded,
                onExpandedChange = { transportMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = transport.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("类型 *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(transportMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(transportMenuExpanded, { transportMenuExpanded = false }) {
                    McpTransport.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { transport = option; transportMenuExpanded = false },
                        )
                    }
                }
            }
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("服务器端点 *") }, placeholder = { Text("https://example.com/mcp") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Bearer Token（可选）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                headers,
                { headers = it },
                label = { Text("请求头参数（可选）") },
                placeholder = { Text("X-Api-Key=your-key\nX-Client=assistant") },
                supportingText = { Text("每行一个，格式：Header-Name=value") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(headlineContent = { Text("启用此服务器") }, trailingContent = { Switch(enabled, { enabled = it }) })
            Text("支持 SSE 与 Streamable HTTP。stdio 命令模式需要本机子进程，不能在 Android 应用中安全运行。")
                }
            }
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.save(initial.copy(name = name.trim(), description = description.trim(), endpointUrl = endpoint.trim(), transport = transport, bearerToken = token.trim(), headers = headers.trim(), isEnabled = enabled))
                    onBack()
                },
                enabled = name.isNotBlank() && endpoint.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            if (!isNew) {
                TextButton(onClick = { viewModel.delete(initial.id); onBack() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null)
                    Text("删除服务器")
                }
            }
                }
            }
        }
    }
}
