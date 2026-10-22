package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无状态的完整编辑器；所有草稿和弹窗状态由 ViewModel 持有。 */
@Composable
fun LLMModelSettingDetailScreen(
    state: LLMModelSettingDetailUiState,
    onAction: (LLMModelSettingDetailAction) -> Unit,
    onOpenUrl: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = state.service
    PreferenceScaffold(
        modifier = modifier,
        style = PreferenceStyle.Compact,
        title = when (state.page) {
            ServiceEditorPage.Detail -> service?.name ?: stringResource(R.string.modelsettings_detail_title)
            ServiceEditorPage.Models -> stringResource(R.string.modelsettings_select_models)
            ServiceEditorPage.Extensions -> stringResource(R.string.modelsettings_extensions)
            ServiceEditorPage.Advanced -> stringResource(R.string.modelsettings_advanced_connection)
        },
        onBack = { onAction(LLMModelSettingDetailAction.Back) },
        actions = {
            if (state.page == ServiceEditorPage.Detail && service?.isAdded == true) {
                ServiceManagementMenu(state, onAction)
            }
        },
        bottomBar = {
            if (service != null && (state.page == ServiceEditorPage.Detail || state.page == ServiceEditorPage.Models)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { onAction(if (state.page == ServiceEditorPage.Models)
                            LLMModelSettingDetailAction.ConfirmModels else LLMModelSettingDetailAction.Save) },
                        enabled = state.canEdit,
                        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp).height(50.dp),
                    ) {
                        Text(stringResource(when {
                            state.isSaving -> R.string.modelsettings_saving
                            state.page == ServiceEditorPage.Models -> R.string.modelsettings_confirm_models
                            service.isAdded -> R.string.modelsettings_save_changes
                            else -> R.string.modelsettings_add_service
                        }, *if (state.page == ServiceEditorPage.Models) arrayOf(state.pendingModelKeys.size) else emptyArray()))
                    }
                }
            }
        },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            val editorScroll = key(state.page) { rememberScrollState() }
            LaunchedEffect(state.errorRes, state.page) {
                if (state.errorRes != null) editorScroll.animateScrollTo(0)
            }
            if (state.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            } else Column(Modifier.fillMaxSize().verticalScroll(editorScroll).padding(top = 8.dp, bottom = 24.dp)) {
                if (state.isMutationBlocked) PreferenceBanner(stringResource(R.string.modelsettings_agent_mutation_blocked))
                state.errorRes?.let { PreferenceBanner(stringResource(it), tone = PreferenceBannerTone.Error) }
                if (service != null) when (state.page) {
                    ServiceEditorPage.Detail -> ServiceConnectionOverview(state, onAction, onOpenUrl)
                    ServiceEditorPage.Models -> LLMModelManagementSection(state, onAction)
                    ServiceEditorPage.Extensions -> OfficialToolsSection(service, state.supportedTools, state.canEdit) {
                        onAction(LLMModelSettingDetailAction.OfficialToolsEnabledChanged(it))
                    }
                    ServiceEditorPage.Advanced -> PreferenceGroupCard {
                        ApiBaseUrlSection(
                            service = service,
                            isMenuExpanded = state.isProtocolMenuExpanded,
                            onToggleMenu = { onAction(LLMModelSettingDetailAction.ToggleProtocolMenu) },
                            onDismissMenu = { onAction(LLMModelSettingDetailAction.DismissProtocolMenu) },
                            onProtocolChange = { onAction(LLMModelSettingDetailAction.ApiProtocolChanged(it)) },
                            onBaseUrlChange = { onAction(LLMModelSettingDetailAction.ApiBaseUrlChanged(it)) },
                            enabled = state.canEdit,
                        )
                    }
                }
            }
        }
    }
    ServiceEditorDialogs(state, onAction)
}

@Composable
private fun ServiceConnectionOverview(
    state: LLMModelSettingDetailUiState,
    onAction: (LLMModelSettingDetailAction) -> Unit,
    onOpenUrl: (String, String) -> Unit,
) {
    val service = state.service ?: return
    Row(Modifier.padding(horizontal = 32.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(when {
            !service.isAdded -> R.string.modelsettings_new_service_hint
            !service.isEnabled -> R.string.modelsettings_paused_hint
            else -> R.string.modelsettings_saved_hint
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_connection))
    PreferenceGroupCard {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = service.apiKey,
                onValueChange = { onAction(LLMModelSettingDetailAction.ApiKeyChanged(it)) },
                label = { Text(stringResource(R.string.modelsettings_api_key_label)) },
                singleLine = true,
                enabled = state.canEdit,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (state.isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { onAction(LLMModelSettingDetailAction.ToggleApiKeyVisibility) }) {
                        Icon(if (state.isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(if (state.isApiKeyVisible) R.string.modelsettings_api_key_hide else R.string.modelsettings_api_key_show))
                    }
                },
            )
            val missingLink = stringResource(R.string.modelsettings_key_url_missing)
            TextButton(onClick = { onOpenUrl(service.keyHelpUrl, missingLink) }) {
                Text(stringResource(R.string.modelsettings_api_key_get_key))
            }
        }
        PreferenceListItem(icon = Icons.Outlined.Settings,
            title = stringResource(R.string.modelsettings_advanced_connection),
            subtitle = protocolLabel(service.apiProtocol),
            onClick = { onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Advanced)) })
        OutlinedButton(onClick = { onAction(LLMModelSettingDetailAction.TestConnection) },
            enabled = state.canEdit && !state.isTestingKey && service.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(if (state.isTestingKey) R.string.modelsettings_key_testing else R.string.modelsettings_api_key_test))
        }
        state.connectionResult?.let { success ->
            Text(stringResource(if (success) R.string.modelsettings_notice_connection_succeeded
                else R.string.modelsettings_notice_connection_failed),
                style = MaterialTheme.typography.bodySmall,
                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.modelsettings_section_models), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models)) }) {
            Text(stringResource(R.string.modelsettings_manage_models))
        }
    }
    PreferenceGroupCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.rows.isEmpty()) Text(stringResource(R.string.modelsettings_no_selected_models),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                state.rows.take(3).forEach { Text(it.model.name, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.modelsettings_selected_model_count, state.rows.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (state.supportedTools.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        PreferenceGroupCard {
            PreferenceListItem(icon = Icons.Outlined.Extension,
                title = stringResource(R.string.modelsettings_extensions),
                subtitle = stringResource(R.string.modelsettings_extensions_summary),
                onClick = { onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Extensions)) })
        }
    }
}

@Composable
private fun ServiceManagementMenu(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    val service = state.service ?: return
    Box {
        IconButton(onClick = { onAction(LLMModelSettingDetailAction.ToggleServiceMenu) }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.modelsettings_manage_service))
        }
        DropdownMenu(expanded = state.isServiceMenuExpanded,
            onDismissRequest = { onAction(LLMModelSettingDetailAction.DismissServiceMenu) }) {
            DropdownMenuItem(text = { Text(stringResource(if (service.isEnabled) R.string.modelsettings_pause_service
                else R.string.modelsettings_resume_service)) }, enabled = state.canEdit && !state.isDirty,
                onClick = { onAction(LLMModelSettingDetailAction.EnabledChanged(!service.isEnabled)) })
            if (state.isDirty) Text(stringResource(R.string.modelsettings_save_before_manage),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DropdownMenuItem(text = { Text(stringResource(R.string.modelsettings_delete_service), color = MaterialTheme.colorScheme.error) },
                enabled = state.canEdit, onClick = { onAction(LLMModelSettingDetailAction.RequestDelete) })
        }
    }
}

@Composable
private fun ServiceEditorDialogs(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    if (state.isDiscardDialogVisible) AlertDialog(
        onDismissRequest = { onAction(LLMModelSettingDetailAction.ContinueEditing) },
        title = { Text(stringResource(R.string.modelsettings_unsaved_title)) },
        text = { Text(stringResource(R.string.modelsettings_unsaved_summary)) },
        confirmButton = { TextButton(onClick = { onAction(LLMModelSettingDetailAction.Save) }, enabled = state.canEdit) {
            Text(stringResource(R.string.modelsettings_save_changes))
        } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAction(LLMModelSettingDetailAction.Discard) }) { Text(stringResource(R.string.modelsettings_discard)) }
                TextButton(onClick = { onAction(LLMModelSettingDetailAction.ContinueEditing) }) { Text(stringResource(R.string.modelsettings_continue_editing)) }
            }
        },
    )
    if (state.isDeleteDialogVisible) AlertDialog(
        onDismissRequest = { onAction(LLMModelSettingDetailAction.CancelDelete) },
        title = { Text(stringResource(R.string.modelsettings_delete_service)) },
        text = { Text(stringResource(R.string.modelsettings_delete_summary)) },
        confirmButton = { TextButton(onClick = { onAction(LLMModelSettingDetailAction.ConfirmDelete) }, enabled = state.canEdit) {
            Text(stringResource(R.string.modelsettings_delete_service), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { onAction(LLMModelSettingDetailAction.CancelDelete) }) {
            Text(stringResource(R.string.modelsettings_dialog_cancel))
        } },
    )
}

@Composable
internal fun protocolLabel(protocol: ApiProtocol): String = stringResource(when (protocol) {
    ApiProtocol.OpenaiChatcompletions -> R.string.modelsettings_api_protocol_standard
    ApiProtocol.Anthropic -> R.string.modelsettings_api_protocol_anthropic_label
    ApiProtocol.Gemini -> R.string.modelsettings_api_protocol_gemini
})

/** 非敏感的固定预览数据，供设置页的独立 Preview 使用。 */
internal fun modelServicePreviewFixture(): LLMModelSetting = LLMModelSetting(
    id = "minimax", name = "MiniMax", isEnabled = true, apiKey = "preview-key",
    apiBaseUrl = "https://api.minimaxi.com/v1", apiProtocol = ApiProtocol.OpenaiChatcompletions,
    anthropicBaseUrl = "https://api.minimaxi.com/anthropic",
    groups = listOf(ModelGroup("minimax", "MiniMax", listOf(
        Model("MiniMax-M2.5", "MiniMax-M2.5"), Model("MiniMax-M2.5-highspeed", "MiniMax-M2.5-highspeed"),
        Model("speech-2.8-hd", "speech-2.8-hd", isTts = true),
    ))),
)

@Preview(name = "服务详情", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "服务详情 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServiceDetailPreview() {
    val service = modelServicePreviewFixture()
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service, supportedTools = OfficialToolSupport().all.filter { it.serviceId == service.id && service.apiProtocol in it.protocols }), {}, { _, _ -> }) }
}

@Preview(name = "连接失败", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "连接失败 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionFailurePreview() {
    val service = modelServicePreviewFixture()
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service, connectionResult = false), {}, { _, _ -> }) }
}

@Preview(name = "任务运行中", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "任务运行中 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BusyServicePreview() {
    val service = modelServicePreviewFixture()
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service, isMutationBlocked = true), {}, { _, _ -> }) }
}
