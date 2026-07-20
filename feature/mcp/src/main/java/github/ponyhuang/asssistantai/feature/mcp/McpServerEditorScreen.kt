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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.feature.mcp.R
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
            if (state.isMutationBlocked || !state.notice.isNullOrBlank()) {
                Text(
                    text = state.notice?.takeUnless { it.isBlank() }
                        ?: stringResource(R.string.mcp_agent_mutation_blocked),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            SettingsSectionTitle(text = stringResource(R.string.mcp_section_basic_info))
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
                        label = { Text(stringResource(R.string.mcp_field_name_required)) },
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
                        placeholder = { Text("https://example.com/mcp") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.bearerToken,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(bearerToken = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_bearer_token_optional)) },
                        enabled = !state.isMutationBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.headers,
                        onValueChange = {
                            onAction(McpSettingsAction.EditorChanged(draft.copy(headers = it)))
                        },
                        label = { Text(stringResource(R.string.mcp_field_header_optional)) },
                        enabled = !state.isMutationBlocked,
                        placeholder = { Text("X-Api-Key=your-key\nX-Client=assistant") },
                        supportingText = { Text(stringResource(R.string.mcp_field_header_help)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.mcp_field_enable_server)) },
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
                    Text(
                        stringResource(R.string.mcp_sse_help),
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
                        enabled = !state.isMutationBlocked &&
                            draft.name.isNotBlank() && draft.endpointUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.mcp_save))
                    }
                    if (!draft.isNew) {
                        TextButton(
                            onClick = { onAction(McpSettingsAction.DeleteEditor) },
                            enabled = !state.isMutationBlocked,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(stringResource(R.string.mcp_delete_server))
                        }
                    }
                }
            }
        }
    }
}
