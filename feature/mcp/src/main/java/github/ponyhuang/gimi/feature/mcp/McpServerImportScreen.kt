package github.ponyhuang.gimi.feature.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mcp.model.McpImportError
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun McpServerImportScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    PreferencePageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_import_mcp))
            PreferenceGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.importJson,
                        onValueChange = { onAction(McpSettingsAction.ImportJsonChanged(it)) },
                        label = { Text(stringResource(R.string.mcp_field_json_label)) },
                        placeholder = { Text(stringResource(R.string.mcp_field_json_placeholder)) },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.importResult?.let { result ->
                        val resultText = result.errorCode?.let { importErrorText(it) } ?: if (result.skipped > 0) {
                            stringResource(
                                R.string.mcp_import_result_with_skipped,
                                result.created,
                                result.updated,
                                result.skipped,
                            )
                        } else {
                            stringResource(
                                R.string.mcp_import_result,
                                result.created,
                                result.updated,
                            )
                        }
                        Text(resultText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.getText()?.text?.let {
                                    onAction(McpSettingsAction.ImportJsonChanged(it))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.mcp_paste),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Button(
                            onClick = { onAction(McpSettingsAction.ImportServers) },
                            enabled = state.importJson.isNotBlank() && !state.isMutationBlocked,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.mcp_import_action))
                        }
                    }
                    Text(
                        stringResource(R.string.mcp_stdio_skip_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun importErrorText(error: McpImportError): String = stringResource(
    when (error) {
        McpImportError.CONTENT_TOO_LARGE -> R.string.mcp_import_error_content_too_large
        McpImportError.INVALID_JSON -> R.string.mcp_import_error_invalid_json
        McpImportError.MCP_SERVERS_NOT_OBJECT -> R.string.mcp_import_error_mcp_servers_not_object
        McpImportError.INVALID_CURL -> R.string.mcp_import_error_invalid_curl
        McpImportError.STORAGE_FAILURE -> R.string.mcp_import_error_storage_failure
    },
)

@Preview(showBackground = true)
@Composable
private fun McpServerImportScreenEmptyPreview() {
    AsssistantaiTheme {
        McpServerImportScreen(
            state = McpSettingsUiState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerImportScreenWithJsonPreview() {
    AsssistantaiTheme {
        McpServerImportScreen(
            state = McpSettingsUiState(
                importJson = "{\"mcpServers\":{\"context7\":{\"url\":\"https://mcp.example.com/mcp\"}}}",
            ),
            onAction = {},
        )
    }
}
