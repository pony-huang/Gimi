package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.DocumentInputCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.VisionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.GimiSearchField
import github.ponyhuang.gimi.ui.preference.PreferenceFormContent
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import github.ponyhuang.gimi.ui.preference.PreferenceSaveBar
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.domain.modelcatalog.model.FILE_UPLOAD_TEMPLATE_IDS
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchRow
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 判断子页切换方向：从详情页进入子页为前进，返回详情页为后退。 */
private fun isForwardTransition(from: ServiceEditorPage, to: ServiceEditorPage): Boolean = when {
    from == ServiceEditorPage.Detail -> true
    to == ServiceEditorPage.Detail -> false
    else -> to.ordinal > from.ordinal
}

/** 服务详情的无状态渲染：页面切换、各子页与确认对话框。 */
@Composable
fun ServiceDetailScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                PreferenceScaffold(
                    style = PreferenceStyle.Compact,
                    title = stringResource(R.string.modelsettings_list_title),
                    onBack = { onAction(ModelServiceDetailAction.Back) },
                ) { scaffoldModifier ->
                    PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            state.draft == null -> {
                PreferenceScaffold(
                    style = PreferenceStyle.Compact,
                    title = stringResource(R.string.modelsettings_list_title),
                    onBack = { onAction(ModelServiceDetailAction.Back) },
                ) { scaffoldModifier ->
                    PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
                        Text(
                            stringResource(R.string.modelsettings_notice_instance_missing),
                            Modifier.padding(24.dp),
                        )
                    }
                }
            }
            else -> {
                // 与全局导航一致的全屏水平平移切页动效：进入下一级子页自右向左滑入，返回上一级自左向右滑回。
                AnimatedContent(
                    targetState = state.page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val isForward = isForwardTransition(initialState, targetState)
                        if (isForward) {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { -it })
                        } else {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { it })
                        }
                    },
                    label = "service-editor-page",
                ) { page ->
                    when (page) {
                        ServiceEditorPage.Detail -> DetailScreen(state, onAction, onOpenUrl)
                        ServiceEditorPage.Models -> ModelsScreen(state, onAction)
                        ServiceEditorPage.Connections -> ConnectionsScreen(state, onAction)
                        ServiceEditorPage.OfficialTools -> OfficialToolsScreen(state, onAction)
                        ServiceEditorPage.Suggestions -> SuggestionsScreen(state, onAction)
                    }
                }
            }
        }
        state.deletionImpact?.let { impact ->
            DeleteServiceDialog(state, impact, onAction)
        }
        if (state.isAddDialogVisible) {
            AddModelDialog(state, onAction)
        }
        if (state.isDiscardDialogVisible) {
            DiscardChangesDialog(onAction)
        }
    }
}

@Composable
private fun DetailScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val saveLabel = if (state.isNewInstance) {
        stringResource(R.string.modelsettings_save_new_service)
    } else {
        stringResource(R.string.modelsettings_save_changes)
    }
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = state.draft?.name?.ifBlank {
            stringResource(
                if (state.isNewInstance) R.string.modelsettings_add_service else R.string.modelsettings_list_title,
            )
        } ?: stringResource(R.string.modelsettings_list_title),
        onBack = { onAction(ModelServiceDetailAction.Back) },
        bottomBar = {
            PreferenceSaveBarHost(
                label = saveLabel,
                enabled = state.canEdit,
                enabledState = state,
                onSave = { onAction(ModelServiceDetailAction.Save) },
            )
        },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                state.errorRes?.let { error ->
                    ErrorBanner(stringResource(error))
                }
                DetailPage(state, onAction, onOpenUrl)
            }
        }
    }
}

@Composable
private fun ConnectionsScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
) {
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_connection_settings),
        onBack = { onAction(ModelServiceDetailAction.Back) },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                state.errorRes?.let { error ->
                    ErrorBanner(stringResource(error))
                }
                ConnectionsPage(state, onAction)
            }
        }
    }
}

@Composable
private fun ModelsScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
) {
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_section_available_models),
        onBack = { onAction(ModelServiceDetailAction.Back) },
        actions = {
            if (state.template?.supportsConversation != false) {
                FetchModelsAction(
                    isRefreshing = state.isRefreshing,
                    onClick = { onAction(ModelServiceDetailAction.FetchModels) },
                )
            }
        },
        bottomBar = {
            PreferenceSaveBarHost(
                label = stringResource(R.string.modelsettings_confirm_models, state.pendingSelectionCount),
                enabled = state.canEdit && state.pendingSelectionCount > 0,
                enabledState = state,
                onSave = { onAction(ModelServiceDetailAction.ConfirmModels) },
            )
        },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                state.errorRes?.let { error ->
                    ErrorBanner(stringResource(error))
                }
                ModelsPage(state, onAction)
            }
        }
    }
}

@Composable
private fun OfficialToolsScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
) {
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_official_tools),
        onBack = { onAction(ModelServiceDetailAction.Back) },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                state.errorRes?.let { error ->
                    ErrorBanner(stringResource(error))
                }
                OfficialToolsPage(state, onAction)
            }
        }
    }
}

@Composable
private fun SuggestionsScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
) {
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_suggestions_title),
        onBack = { onAction(ModelServiceDetailAction.Back) },
        bottomBar = {
            PreferenceSaveBarHost(
                label = stringResource(R.string.modelsettings_save_with_defaults),
                enabled = state.canEdit,
                enabledState = state,
                onSave = { onAction(ModelServiceDetailAction.ConfirmSuggestions) },
            )
        },
    ) { scaffoldModifier ->
        PreferencePageContainer(scaffoldModifier, style = PreferenceStyle.Compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                state.errorRes?.let { error ->
                    ErrorBanner(stringResource(error))
                }
                SuggestionsPage(state, onAction)
            }
        }
    }
}

@Composable
private fun FetchModelsAction(isRefreshing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.size(18.dp))
        } else {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.modelsettings_fetch_models),
            )
        }
    }
}

@Composable
private fun PreferenceSaveBarHost(
    label: String,
    enabled: Boolean,
    enabledState: ModelServiceDetailUiState,
    onSave: () -> Unit,
) {
    PreferenceSaveBar(
        label = label,
        onSave = onSave,
        enabled = enabled,
        notice = if (!enabledState.isEnabled) {
            stringResource(R.string.modelsettings_save_notice_paused)
        } else {
            null
        },
    )
}

@Composable
private fun DetailPage(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val draft = state.draft ?: return
    if (!state.isNewInstance) {
        ServiceEnabledGroup(state.isEnabled, onAction)
    }
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_service_setup))
    PreferenceGroupCard {
        PreferenceFormContent {
            PreferenceTextField(
                value = draft.name,
                onValueChange = { onAction(ModelServiceDetailAction.NameChanged(it)) },
                label = { Text(stringResource(R.string.modelsettings_field_service_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            PreferenceTextField(
                value = draft.sharedApiKey,
                onValueChange = { onAction(ModelServiceDetailAction.SharedApiKeyChanged(it)) },
                label = { Text(stringResource(R.string.modelsettings_field_shared_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (state.isApiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onAction(ModelServiceDetailAction.ToggleApiKeyVisibility) },
                    ) {
                        Icon(
                            Icons.Outlined.VisibilityOff,
                            contentDescription = stringResource(R.string.modelsettings_toggle_key_visibility),
                        )
                    }
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.modelsettings_shared_credential_hint),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val keyHelpUrl = state.template?.keyHelpUrl.orEmpty()
            if (keyHelpUrl.isNotBlank()) {
                TextButton(onClick = { onOpenUrl(keyHelpUrl) }) {
                    Text(stringResource(R.string.modelsettings_get_api_key))
                }
            }
        }
    }
    PreferenceGroupCard(modifier = Modifier.padding(top = 12.dp)) {
        PreferenceListItem(
            icon = Icons.Outlined.SettingsEthernet,
            title = stringResource(R.string.modelsettings_connection_settings),
            subtitle = stringResource(R.string.modelsettings_connection_settings_subtitle),
            onClick = { onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Connections)) },
            showDivider = state.supportsTools || state.template?.id in FILE_UPLOAD_TEMPLATE_IDS,
        )
        if (state.supportsTools) {
            PreferenceListItem(
                // SVG 官方字形走 drawable 资源；leadingIcon 槽自管 tint，与 Compact 行默认灰一致
                leadingIcon = { modifier ->
                    Icon(
                        painterResource(R.drawable.ic_service_toolbox),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = modifier,
                    )
                },
                title = stringResource(R.string.modelsettings_official_tools),
                subtitle = stringResource(R.string.modelsettings_official_tools_subtitle),
                onClick = { onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.OfficialTools)) },
                showDivider = state.template?.id in FILE_UPLOAD_TEMPLATE_IDS,
            )
        }
        // 文件上传是实例级开关（仅在支持文件上传的厂商模板展示）：
        // 新建草稿阶段随草稿落盘，落库后直接写仓库并由仓库观察回流同步。
        if (state.template?.id in FILE_UPLOAD_TEMPLATE_IDS) {
            PreferenceSwitchRow(
                leadingIcon = { modifier ->
                    Icon(
                        painterResource(R.drawable.ic_cloud_upload),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = modifier,
                    )
                },
                title = stringResource(R.string.modelsettings_file_upload),
                description = stringResource(R.string.modelsettings_file_upload_subtitle),
                checked = state.isFileUploadEnabled,
                onCheckedChange = { onAction(ModelServiceDetailAction.FileUploadChanged(it)) },
            )
        }
    }
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_available_models))
    PreferenceGroupCard {
        draft.connections.forEachIndexed { index, connection ->
            CapabilityRow(
                capability = connection.capability,
                count = connection.models.size,
                showDivider = index < draft.connections.size - 1,
                onClick = { onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Models)) },
            )
        }
        if (draft.connections.isEmpty()) {
            PreferenceSectionNote(stringResource(R.string.modelsettings_no_capabilities))
        }
    }
    if (state.canDelete) {
        DeleteServiceEntry(onAction)
    }
}

@Composable
internal fun ServiceEnabledGroup(enabled: Boolean, onAction: (ModelServiceDetailAction) -> Unit) {
    PreferenceGroupCard(modifier = Modifier.padding(top = 12.dp)) {
        PreferenceSwitchRow(
            icon = Icons.Outlined.PowerSettingsNew,
            title = stringResource(R.string.modelsettings_enable_service),
            description = stringResource(
                if (enabled) R.string.modelsettings_enabled_label else R.string.modelsettings_disabled_label,
            ),
            checked = enabled,
            onCheckedChange = { onAction(ModelServiceDetailAction.EnabledChanged(it)) },
        )
    }
}

@Composable
private fun CapabilityRow(
    capability: ServiceCapability,
    count: Int,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(capabilityLabelRes(capability))
    PreferenceListItem(
        icon = capabilityIcon(capability),
        title = label,
        subtitle = stringResource(R.string.modelsettings_selected_count, count),
        showDivider = showDivider,
        onClick = onClick,
        trailingContent = {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
private fun DeleteServiceEntry(onAction: (ModelServiceDetailAction) -> Unit) {
    TextButton(
        onClick = { onAction(ModelServiceDetailAction.RequestDelete) },
        modifier = Modifier.padding(start = 20.dp, top = 16.dp),
    ) {
        Text(stringResource(R.string.modelsettings_delete_service), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DeleteServiceDialog(
    state: ModelServiceDetailUiState,
    impact: DeletionImpact,
    onAction: (ModelServiceDetailAction) -> Unit,
) {
    val serviceName = state.draft?.name.orEmpty()
    AlertDialog(
        onDismissRequest = { onAction(ModelServiceDetailAction.DismissDelete) },
        title = { Text(stringResource(R.string.modelsettings_delete_dialog_title, serviceName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.modelsettings_delete_dialog_body))
                if (impact.roles.isNotEmpty()) {
                    Text(stringResource(R.string.modelsettings_delete_dialog_roles))
                    Text(
                        // stringResource 不能在非组合 lambda 里调用；先在组合期解析各角色标签。
                        impact.roles.map { role -> "• " + stringResource(role.labelRes) }.joinToString("\n"),
                    )
                }
                if (impact.hasTtsVoiceBinding) {
                    Text("• " + stringResource(R.string.modelsettings_delete_voice_binding))
                }
                if (impact.hasRealtimeVoiceBinding) {
                    Text("• " + stringResource(R.string.modelsettings_delete_realtime_voice_binding))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.ConfirmDelete) }) {
                Text(stringResource(R.string.modelsettings_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.DismissDelete) }) {
                Text(stringResource(R.string.modelsettings_cancel))
            }
        },
    )
}

@Composable
private fun AddModelDialog(state: ModelServiceDetailUiState, onAction: (ModelServiceDetailAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ModelServiceDetailAction.DismissAddDialog) },
        title = { Text(stringResource(R.string.modelsettings_add_model_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreferenceTextField(
                    value = state.newModelId,
                    onValueChange = { onAction(ModelServiceDetailAction.NewModelIdChanged(it)) },
                    label = { Text(stringResource(R.string.modelsettings_add_model_id_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (state.template?.supportsConversation != false) {
                    PreferenceTextField(
                        value = stringResource(state.newModelKind.labelRes),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.modelsettings_add_model_kind_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.modelsettings_add_model_kind_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.ConfirmAddModel) }) {
                Text(stringResource(R.string.modelsettings_add))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.DismissAddDialog) }) {
                Text(stringResource(R.string.modelsettings_cancel))
            }
        },
    )
}

@Composable
private fun DiscardChangesDialog(onAction: (ModelServiceDetailAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ModelServiceDetailAction.ContinueEditing) },
        title = { Text(stringResource(R.string.modelsettings_discard_title)) },
        text = { Text(stringResource(R.string.modelsettings_discard_body)) },
        confirmButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.Discard) }) {
                Text(stringResource(R.string.modelsettings_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ModelServiceDetailAction.ContinueEditing) }) {
                Text(stringResource(R.string.modelsettings_discard_continue))
            }
        },
    )
}

@Composable
private fun ErrorBanner(message: String) {
    github.ponyhuang.gimi.ui.preference.PreferenceBanner(
        message,
        tone = github.ponyhuang.gimi.ui.preference.PreferenceBannerTone.Error,
        modifier = Modifier.padding(16.dp),
    )
}

/** 能力类别的展示标签资源。 */
@Composable
fun capabilityLabelRes(capability: ServiceCapability): Int = when (capability) {
    ServiceCapability.Conversation -> R.string.modelsettings_capability_chat
    ServiceCapability.Recognition -> R.string.modelsettings_capability_recognition
    ServiceCapability.Synthesis -> R.string.modelsettings_capability_synthesis
    ServiceCapability.Realtime -> R.string.modelsettings_capability_realtime
}

fun capabilityIcon(capability: ServiceCapability): ImageVector = when (capability) {
    ServiceCapability.Conversation -> Icons.AutoMirrored.Outlined.Chat
    ServiceCapability.Recognition -> Icons.Outlined.MicNone
    ServiceCapability.Synthesis -> Icons.AutoMirrored.Outlined.VolumeUp
    ServiceCapability.Realtime -> Icons.Outlined.GraphicEq
}

@Composable
internal fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, content = content)
}

// ---- Previews ----

/** 列表与详情预览共用的实例 fixture。 */
internal fun ServiceListPreviewFixtures(): List<ModelServiceInstance> {
    fun instance(
        id: String,
        name: String,
        templateId: String?,
        vararg connections: Pair<ServiceCapability, Int>,
        isEnabled: Boolean = true,
    ): ModelServiceInstance {
        val template = templateId?.let { PreviewTemplates.firstOrNull { it.id == templateId } }
        return ModelServiceInstance(
            id = id,
            name = name,
            templateId = templateId,
            isEnabled = isEnabled,
            isOfficialToolsEnabled = true,
            sharedApiKey = "sk-preview",
            connections = connections.map { (capability, count) ->
                val templateConnection = template?.connection(capability)
                ServiceConnection(
                    id = "$id-${capability.name.lowercase()}",
                    capability = capability,
                    protocol = templateConnection?.protocols?.firstOrNull() ?: ApiProtocol.OpenaiChatcompletions,
                    endpoint = templateConnection?.defaultEndpoint(templateConnection.protocols.first()) ?: "",
                    models = (1..count).map { index ->
                        Model("model-$index", "model-$index")
                    },
                )
            },
        )
    }
    return listOf(
        instance(
            "instance-openai", "OpenAI 个人", "openai",
            ServiceCapability.Conversation to 3,
            ServiceCapability.Recognition to 1,
            ServiceCapability.Synthesis to 1,
            ServiceCapability.Realtime to 1,
        ),
        instance(
            "instance-minimax", "MiniMax", "minimax",
            ServiceCapability.Conversation to 2,
            ServiceCapability.Synthesis to 2,
        ),
        ModelServiceInstance(
            id = "instance-custom",
            name = "我的中转服务",
            templateId = null,
            isEnabled = true,
            isOfficialToolsEnabled = false,
            sharedApiKey = "sk-preview",
            connections = listOf(
                ServiceConnection(
                    id = "instance-custom-conversation",
                    capability = ServiceCapability.Conversation,
                    protocol = ApiProtocol.Anthropic,
                    endpoint = "https://relay.example.com",
                    models = listOf(Model("chat-model-v1", "chat-model-v1"), Model("fast-model-v1", "fast-model-v1")),
                ),
            ),
        ),
        instance(
            "instance-paused", "OpenAI 工作", "openai",
            ServiceCapability.Conversation to 2,
            isEnabled = false,
        ),
    )
}

private val PreviewTemplates: List<github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate> = listOf(
    github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate(
        id = "openai",
        name = "OpenAI",
        connections = listOf(
            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
            ),
            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                capability = ServiceCapability.Recognition,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                capability = ServiceCapability.Synthesis,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                capability = ServiceCapability.Realtime,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "wss://api.openai.com/v1/realtime"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("wss"),
            ),
        ),
    ),
    github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate(
        id = "minimax",
        name = "MiniMax",
        connections = listOf(
            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.minimaxi.com/v1"),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
            ),
        ),
    ),
)

@Preview(name = "详情骨架 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(
    name = "详情骨架 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServiceDetailEmptyStatePreview() {
    GimiTheme {
        ServiceDetailScreen(
            state = ModelServiceDetailUiState(isLoading = false),
            onAction = {},
            onOpenUrl = {},
        )
    }
}

@Preview(name = "服务详情 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(
    name = "服务详情 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServiceDetailPopulatedPreview() {
    GimiTheme {
        ServiceDetailScreen(
            state = ModelServiceDetailUiState(
                isLoading = false,
                template = PreviewDrafts.builtInTemplate(),
                draft = PreviewDrafts.builtIn(),
            ),
            onAction = {},
            onOpenUrl = {},
        )
    }
}

@Preview(name = "模型 · 浅色", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh")
@Preview(
    name = "模型 · 深色", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServiceDetailModelsSubpagePreview() {
    val draft = PreviewDrafts.builtIn()
    val connection = draft.connections.first()
    val candidates = listOf(
        // 带多模态输入能力：覆盖名称/ID 两行与视觉/文档/音频徽标形态。
        Model(
            "gpt-4.1",
            "GPT-4.1",
            multimodal = MultimodalCapabilities(
                vision = VisionCapability(),
                documentInput = DocumentInputCapability(),
            ),
        ),
        Model("gpt-4.1-mini", "GPT-4.1 mini"),
    ).map { ModelCandidateRow(connection.id, connection.capability, it) }
    GimiTheme {
        ModelsScreen(
            state = ModelServiceDetailUiState(
                draft = draft,
                candidates = candidates,
                pendingModelKeys = setOf(candidates.first().key),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "模型 · 搜索无结果", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh")
@Composable
private fun ServiceDetailModelsEmptyQueryPreview() {
    val draft = PreviewDrafts.builtIn()
    val connection = draft.connections.first()
    val candidates = listOf(Model("gpt-4.1", "GPT-4.1"))
        .map { ModelCandidateRow(connection.id, connection.capability, it) }
    GimiTheme {
        ModelsScreen(
            state = ModelServiceDetailUiState(
                draft = draft,
                candidates = candidates,
                modelQuery = "zzz",
                pendingModelKeys = setOf(candidates.first().key),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "接口配置子页 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(
    name = "接口配置子页 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServiceDetailConnectionsSubpagePreview() {
    GimiTheme {
        ServiceDetailScreen(
            state = ModelServiceDetailUiState(
                isLoading = false,
                page = ServiceEditorPage.Connections,
                template = PreviewDrafts.builtInTemplate(),
                draft = PreviewDrafts.builtIn(),
                connections = PreviewDrafts.connectionEditors(),
            ),
            onAction = {},
            onOpenUrl = {},
        )
    }
}
