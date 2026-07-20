package github.ponyhuang.asssistantai.feature.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.ui.settings.SettingsCard
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun McpServerEditorScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.editor
    if (draft == null) {
        SettingsPageContainer(modifier) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    SettingsPageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionTitle(text = "基本信息")
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(name = it)))
                        },
                        label = { Text("名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(description = it)))
                        },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = state.isTransportMenuExpanded,
                        onExpandedChange = {
                            onAction(McpSettingsAction.TransportMenuChanged(it))
                        },
                    ) {
                        OutlinedTextField(
                            value = draft.transport.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("类型 *") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    state.isTransportMenuExpanded,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = state.isTransportMenuExpanded,
                            onDismissRequest = {
                                onAction(McpSettingsAction.TransportMenuChanged(false))
                            },
                        ) {
                            McpTransport.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onAction(McpSettingsAction.TransportSelected(option))
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.endpointUrl,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(endpointUrl = it)))
                        },
                        label = { Text("服务器端点 *") },
                        placeholder = { Text("https://example.com/mcp") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.bearerToken,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(bearerToken = it)))
                        },
                        label = { Text("Bearer Token（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.headers,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(headers = it)))
                        },
                        label = { Text("请求头参数（可选）") },
                        placeholder = { Text("X-Api-Key=your-key\nX-Client=assistant") },
                        supportingText = { Text("每行一个，格式：Header-Name=value") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListItem(
                        headlineContent = { Text("启用此服务器") },
                        trailingContent = {
                            Switch(
                                checked = draft.isEnabled,
                                onCheckedChange = {
                                    onAction(
                                        McpSettingsAction.EditorChanged(
                                            draft.copy(isEnabled = it),
                                        ),
                                    )
                                },
                            )
                        },
                    )
                    Text(
                        "支持 SSE 与 Streamable HTTP。stdio 命令模式需要本机子进程，不能在 Android 应用中安全运行。",
                    )
                }
            }
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onAction(McpSettingsAction.SaveEditor) },
                        enabled = draft.name.isNotBlank() && draft.endpointUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存")
                    }
                    if (!draft.isNew) {
                        TextButton(
                            onClick = { onAction(McpSettingsAction.DeleteEditor) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("删除服务器")
                        }
                    }
                }
            }
        }
    }
}
