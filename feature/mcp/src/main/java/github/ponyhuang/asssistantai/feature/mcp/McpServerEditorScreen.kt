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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.feature.mcp.R
import github.ponyhuang.asssistantai.ui.preference.PreferenceCard
import github.ponyhuang.asssistantai.ui.preference.PreferenceListItem
import github.ponyhuang.asssistantai.ui.preference.PreferencePageContainer
import github.ponyhuang.asssistantai.ui.preference.PreferenceSectionTitle

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun McpServerEditorScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.editor
    if (draft == null) {
        PreferencePageContainer(modifier) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    PreferencePageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isMutationBlocked) {
                Text(
                    text = stringResource(R.string.mcp_agent_mutation_blocked),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            // 启用开关是状态控制而非表单字段，置顶作主开关；
            // 样式与语音唤醒等设置页的 PreferenceListItem + 尾部 Switch 保持一致。
            PreferenceListItem(
                icon = Icons.Default.Extension,
                title = stringResource(R.string.mcp_field_enable_server),
                subtitle = stringResource(R.string.mcp_field_enable_subtitle),
                onClick = {
                    if (!state.isMutationBlocked) {
                        onAction(
                            McpSettingsAction.EditorChanged(
                                draft.copy(isEnabled = !draft.isEnabled),
                            ),
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = draft.isEnabled,
                        enabled = !state.isMutationBlocked,
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
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_basic_info))
            PreferenceCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(name = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_name_required)) },
                        placeholder = { Text(stringResource(R.string.mcp_field_name_placeholder)) },
                        enabled = !state.isMutationBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(description = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_description)) },
                        enabled = !state.isMutationBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = state.isTransportMenuExpanded,
                        onExpandedChange = {
                            if (!state.isMutationBlocked) {
                                onAction(McpSettingsAction.TransportMenuChanged(it))
                            }
                        },
                    ) {
                        OutlinedTextField(
                            value = draft.transport.displayName(),
                            onValueChange = {},
                            readOnly = true,
                            enabled = !state.isMutationBlocked,
                            label = { Text(stringResource(R.string.mcp_field_type_required)) },
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
                                    text = { Text(option.displayName()) },
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
                        label = { Text(stringResource(R.string.mcp_field_endpoint_required)) },
                        enabled = !state.isMutationBlocked,
                        placeholder = { Text(stringResource(R.string.mcp_field_endpoint_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    var tokenVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = draft.bearerToken,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(bearerToken = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_bearer_token_optional)) },
                        enabled = !state.isMutationBlocked,
                        visualTransformation = if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (tokenVisible) R.string.mcp_token_hide
                                        else R.string.mcp_token_show,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.headers,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(headers = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_header_optional)) },
                        enabled = !state.isMutationBlocked,
                        placeholder = { Text(stringResource(R.string.mcp_field_header_placeholder)) },
                        supportingText = { Text(stringResource(R.string.mcp_field_header_help)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.mcp_sse_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PreferenceCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onAction(McpSettingsAction.SaveEditor) },
                        enabled = !state.isMutationBlocked &&
                            draft.name.isNotBlank() && draft.endpointUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.mcp_save))
                    }
                    if (!draft.isNew) {
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !state.isMutationBlocked,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(stringResource(R.string.mcp_delete_server))
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text(stringResource(R.string.mcp_delete_confirm_title)) },
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.mcp_delete_confirm_message,
                                            draft.name,
                                        ),
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteDialog = false
                                            onAction(McpSettingsAction.DeleteEditor)
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.mcp_delete_confirm_action))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text(stringResource(R.string.mcp_cancel))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
