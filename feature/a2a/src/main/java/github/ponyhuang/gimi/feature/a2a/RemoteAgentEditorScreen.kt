package github.ponyhuang.gimi.feature.a2a

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentSkillSnapshot
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSaveBar
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun RemoteAgentEditorScreen(
    state: A2aSettingsUiState,
    onAction: (A2aSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.editor
    if (draft == null) {
        PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isMutationBlocked) {
                PreferenceBanner(text = stringResource(R.string.a2a_agent_mutation_blocked))
            }
            // 启用开关是状态控制而非表单字段，置顶作主开关；
            // 样式与其它设置页条目的 PreferenceListItem + 尾部 Switch 保持一致。
            PreferenceGroupCard {
                PreferenceListItem(
                    icon = Icons.Default.Hub,
                    title = stringResource(R.string.a2a_field_enable_agent),
                    subtitle = stringResource(R.string.a2a_field_enable_subtitle),
                    onClick = {
                        if (!state.isMutationBlocked) {
                            onAction(
                                A2aSettingsAction.EditorChanged(
                                    draft.copy(isEnabled = !draft.isEnabled),
                                ),
                            )
                        }
                    },
                    trailingContent = {
                        GimiSwitch(
                            checked = draft.isEnabled,
                            enabled = !state.isMutationBlocked,
                            onCheckedChange = {
                                onAction(
                                    A2aSettingsAction.EditorChanged(
                                        draft.copy(isEnabled = it),
                                    ),
                                )
                            },
                        )
                    },
                )
            }
            PreferenceSectionTitle(text = stringResource(R.string.a2a_section_basic_info))
            PreferenceGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PreferenceTextField(
                        value = draft.name,
                        onValueChange = {
                            onAction(A2aSettingsAction.EditorChanged(draft.copy(name = it)))
                        },
                        label = { Text(stringResource(R.string.a2a_field_name_required)) },
                        placeholder = { Text(stringResource(R.string.a2a_field_name_placeholder)) },
                        enabled = !state.isMutationBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PreferenceTextField(
                        value = draft.endpointUrl,
                        onValueChange = {
                            onAction(A2aSettingsAction.EditorChanged(draft.copy(endpointUrl = it)))
                        },
                        label = { Text(stringResource(R.string.a2a_field_endpoint_required)) },
                        enabled = !state.isMutationBlocked,
                        placeholder = { Text(stringResource(R.string.a2a_field_endpoint_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    var tokenVisible by remember { mutableStateOf(false) }
                    PreferenceTextField(
                        value = draft.bearerToken,
                        onValueChange = {
                            onAction(A2aSettingsAction.EditorChanged(draft.copy(bearerToken = it)))
                        },
                        label = { Text(stringResource(R.string.a2a_field_bearer_token_optional)) },
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
                                        if (tokenVisible) R.string.a2a_token_hide
                                        else R.string.a2a_token_show,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 主动测试连接：探测成功后编辑器内展示最新卡片详情，不必先保存。
                TextButton(
                    onClick = { onAction(A2aSettingsAction.TestConnection) },
                    enabled = !state.isMutationBlocked && !state.isTestingConnection &&
                        draft.endpointUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.a2a_card_test_action))
                }
            }
            state.editorCard?.let { card ->
                RemoteAgentCardDetailSection(snapshot = card)
            }
            if (state.connectionError != null || !draft.isNew) {
                PreferenceGroupCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.connectionError?.let { error ->
                            Text(
                                text = localizeA2aError(error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                                Text(stringResource(R.string.a2a_delete_agent))
                            }
                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text(stringResource(R.string.a2a_delete_confirm_title)) },
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.a2a_delete_confirm_message,
                                                draft.name,
                                            ),
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showDeleteDialog = false
                                                onAction(A2aSettingsAction.DeleteEditor)
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Text(stringResource(R.string.a2a_delete_confirm_action))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteDialog = false }) {
                                            Text(stringResource(R.string.a2a_cancel))
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
}

/** 编辑页主操作固定在页面底部，连接测试与提交仍使用既有状态和意图。 */
@Composable
internal fun RemoteAgentEditorSaveBar(
    state: A2aSettingsUiState,
    onAction: (A2aSettingsAction) -> Unit,
) {
    val draft = state.editor ?: return
    PreferenceSaveBar(
        label = stringResource(
            if (state.isTestingConnection) R.string.a2a_connection_testing else R.string.a2a_save,
        ),
        onSave = { onAction(A2aSettingsAction.SaveEditor) },
        enabled = !state.isMutationBlocked && !state.isTestingConnection &&
            draft.name.isNotBlank() && draft.endpointUrl.isNotBlank(),
        buttonModifier = Modifier.testTag("a2a_save_action"),
    )
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentEditorScreenLoadingPreview() {
    AsssistantaiTheme {
        RemoteAgentEditorScreen(
            state = A2aSettingsUiState(editor = null),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentEditorScreenNewPreview() {
    AsssistantaiTheme {
        RemoteAgentEditorScreen(
            state = A2aSettingsUiState(
                editor = RemoteAgentEditorDraft(
                    id = "new",
                    isNew = true,
                    name = "weather_agent",
                    endpointUrl = "http://192.168.1.8:8001/a2a/weather",
                    bearerToken = "",
                    isEnabled = true,
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentEditorScreenExistingWithErrorPreview() {
    AsssistantaiTheme {
        RemoteAgentEditorScreen(
            state = A2aSettingsUiState(
                editor = RemoteAgentEditorDraft(
                    id = "agent-1",
                    isNew = false,
                    name = "weather_agent",
                    endpointUrl = "http://192.168.1.8:8001/a2a/weather",
                    bearerToken = "token",
                    isEnabled = true,
                ),
                connectionError = "a2a.connection_error",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemoteAgentEditorScreenWithCardPreview() {
    AsssistantaiTheme {
        RemoteAgentEditorScreen(
            state = A2aSettingsUiState(
                editor = RemoteAgentEditorDraft(
                    id = "agent-1",
                    isNew = false,
                    name = "weather_agent",
                    endpointUrl = "http://192.168.1.8:8001/a2a/weather",
                    bearerToken = "",
                    isEnabled = true,
                ),
                editorCard = RemoteAgentCardSnapshot(
                    name = "weather-assistant",
                    version = "1.2.0",
                    protocolVersion = "1.0",
                    description = "查询全球城市实时天气、未来七日预报与气象预警信息。",
                    providerOrganization = "ExampleAI",
                    providerUrl = "https://agents.example.ai",
                    supportsStreaming = true,
                    supportsExtendedAgentCard = false,
                    skills = listOf(
                        RemoteAgentSkillSnapshot(
                            id = "query_weather",
                            name = "query_weather",
                            description = "查询指定城市当前的温度、湿度与空气质量。",
                            tags = listOf("weather", "realtime"),
                        ),
                    ),
                    defaultInputModes = listOf("text/plain"),
                    defaultOutputModes = listOf("text/plain"),
                ),
            ),
            onAction = {},
        )
    }
}
