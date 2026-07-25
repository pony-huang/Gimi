package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.feature.modelsettings.R
import github.ponyhuang.asssistantai.ui.settings.SettingsCard
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun ModelServiceDetailScreen(
    state: ModelServiceDetailUiState,
    onAction: (ModelServiceDetailAction) -> Unit,
    onOpenUrl: (url: String, missingMessage: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = state.service ?: return
    val dispatch: (ModelServiceDetailAction) -> Unit = { action ->
        if (!state.isMutationBlocked || !action.changesAgentConfiguration()) onAction(action)
    }

    val homepageMissing = stringResource(R.string.modelsettings_homepage_missing)
    val keyUrlMissing = stringResource(R.string.modelsettings_key_url_missing)
    val noLinkConfigured = stringResource(R.string.modelsettings_no_link_configured)

    SettingsPageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked) {
                Text(
                    text = stringResource(R.string.modelsettings_agent_mutation_blocked),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            SettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp)
                    .alpha(if (state.isMutationBlocked) 0.6f else 1f),
            ) {
                HeaderSection(
                    service = service,
                    onToggleEnabled = {
                        dispatch(ModelServiceDetailAction.EnabledChanged(it))
                    },
                    onOpenHomepage = {
                        onOpenUrl(service.homepageUrl, homepageMissing)
                    },
                )
            }
            SettingsSectionTitle(
                text = stringResource(R.string.modelsettings_section_connection),
                modifier = Modifier.padding(top = 20.dp),
            )
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                ApiKeySection(
                    apiKey = service.apiKey,
                    keyHelpUrl = service.keyHelpUrl,
                    isVisible = state.isApiKeyVisible,
                    isTesting = state.isTestingKey,
                    onApiKeyChange = {
                        dispatch(ModelServiceDetailAction.ApiKeyChanged(it))
                    },
                    onToggleVisibility = {
                        dispatch(ModelServiceDetailAction.ToggleApiKeyVisibility)
                    },
                    onTest = { dispatch(ModelServiceDetailAction.TestConnection) },
                    onOpenKeyHelp = {
                        onOpenUrl(service.keyHelpUrl, keyUrlMissing)
                    },
                )
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
            ) {
                ApiBaseUrlSection(
                    service = service,
                    isMenuExpanded = state.isProtocolMenuExpanded,
                    onToggleMenu = { dispatch(ModelServiceDetailAction.ToggleProtocolMenu) },
                    onDismissMenu = { dispatch(ModelServiceDetailAction.DismissProtocolMenu) },
                    onProtocolChange = {
                        dispatch(ModelServiceDetailAction.ApiProtocolChanged(it))
                    },
                    onBaseUrlChange = {
                        dispatch(ModelServiceDetailAction.ApiBaseUrlChanged(it))
                    },
                )
            }
            if (service.supportedOfficialTools.isNotEmpty()) {
                SettingsSectionTitle(
                    text = stringResource(R.string.modelsettings_section_official_tools),
                    modifier = Modifier.padding(top = 20.dp),
                )
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OfficialToolsSection(
                        supportedTools = service.supportedOfficialTools,
                        enabledTools = service.enabledOfficialTools,
                        onEnabledChange = { toolId, enabled ->
                            dispatch(
                                ModelServiceDetailAction.OfficialToolEnabledChanged(
                                    toolId,
                                    enabled,
                                ),
                            )
                        },
                    )
                }
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp),
            ) {
                LLMModelManagementSection(
                    service = service,
                    rows = state.rows,
                    isRefreshing = state.isRefreshing,
                    isAddDialogVisible = state.isAddDialogVisible,
                    newModelId = state.newModelId,
                    newModelKind = state.newModelKind,
                    onAction = dispatch,
                )
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
            ) {
                FooterSection(
                    service = service,
                    onOpenUrl = { url -> onOpenUrl(url, noLinkConfigured) },
                )
            }
        }
    }
}

private fun ModelServiceDetailAction.changesAgentConfiguration(): Boolean = when (this) {
    is ModelServiceDetailAction.ApiKeyChanged,
    is ModelServiceDetailAction.ApiBaseUrlChanged,
    is ModelServiceDetailAction.ApiProtocolChanged,
    is ModelServiceDetailAction.EnabledChanged,
    is ModelServiceDetailAction.OfficialToolEnabledChanged,
    is ModelServiceDetailAction.RemoveModel,
    is ModelServiceDetailAction.NewModelIdChanged,
    is ModelServiceDetailAction.NewModelKindChanged,
    ModelServiceDetailAction.ShowAddDialog,
    ModelServiceDetailAction.ConfirmAddModel,
    ModelServiceDetailAction.RefreshModels -> true
    else -> false
}
