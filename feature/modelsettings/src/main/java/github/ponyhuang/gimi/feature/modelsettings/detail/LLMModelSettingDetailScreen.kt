package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun LLMModelSettingDetailScreen(
    state: LLMModelSettingDetailUiState,
    onAction: (LLMModelSettingDetailAction) -> Unit,
    onOpenUrl: (url: String, missingMessage: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = state.service ?: return
    val dispatch: (LLMModelSettingDetailAction) -> Unit = { action ->
        if (!state.isMutationBlocked || !action.changesAgentConfiguration()) onAction(action)
    }

    val homepageMissing = stringResource(R.string.modelsettings_homepage_missing)
    val keyUrlMissing = stringResource(R.string.modelsettings_key_url_missing)
    // Agent 任务进行中时整页配置降透明度提示只读，改配置动作被 dispatch 屏蔽。
    val blockedAlpha = if (state.isMutationBlocked) 0.6f else 1f

    PreferencePageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.modelsettings_agent_mutation_blocked),
                    tone = PreferenceBannerTone.Error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            PreferenceGroupCard(modifier = Modifier.alpha(blockedAlpha)) {
                HeaderSection(
                    service = service,
                    onToggleEnabled = {
                        dispatch(LLMModelSettingDetailAction.EnabledChanged(it))
                    },
                    onOpenHomepage = {
                        onOpenUrl(service.homepageUrl, homepageMissing)
                    },
                )
            }
            PreferenceSectionTitle(
                text = stringResource(R.string.modelsettings_section_connection),
            )
            PreferenceGroupCard(modifier = Modifier.alpha(blockedAlpha)) {
                ApiKeySection(
                    apiKey = service.apiKey,
                    keyHelpUrl = service.keyHelpUrl,
                    isVisible = state.isApiKeyVisible,
                    isTesting = state.isTestingKey,
                    onApiKeyChange = {
                        dispatch(LLMModelSettingDetailAction.ApiKeyChanged(it))
                    },
                    onToggleVisibility = {
                        dispatch(LLMModelSettingDetailAction.ToggleApiKeyVisibility)
                    },
                    onTest = { dispatch(LLMModelSettingDetailAction.TestConnection) },
                    onOpenKeyHelp = {
                        onOpenUrl(service.keyHelpUrl, keyUrlMissing)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                ApiBaseUrlSection(
                    service = service,
                    isMenuExpanded = state.isProtocolMenuExpanded,
                    onToggleMenu = { dispatch(LLMModelSettingDetailAction.ToggleProtocolMenu) },
                    onDismissMenu = { dispatch(LLMModelSettingDetailAction.DismissProtocolMenu) },
                    onProtocolChange = {
                        dispatch(LLMModelSettingDetailAction.ApiProtocolChanged(it))
                    },
                    onBaseUrlChange = {
                        dispatch(LLMModelSettingDetailAction.ApiBaseUrlChanged(it))
                    },
                )
            }
            LLMModelManagementSection(
                service = service,
                rows = state.rows,
                isRefreshing = state.isRefreshing,
                isAddDialogVisible = state.isAddDialogVisible,
                newModelId = state.newModelId,
                newModelKind = state.newModelKind,
                onAction = dispatch,
                modifier = Modifier.alpha(blockedAlpha),
            )
        }
    }
}

private fun LLMModelSettingDetailAction.changesAgentConfiguration(): Boolean = when (this) {
    is LLMModelSettingDetailAction.ApiKeyChanged,
    is LLMModelSettingDetailAction.ApiBaseUrlChanged,
    is LLMModelSettingDetailAction.ApiProtocolChanged,
    is LLMModelSettingDetailAction.EnabledChanged,
    is LLMModelSettingDetailAction.RemoveLLMModel,
    is LLMModelSettingDetailAction.NewLLMModelIdChanged,
    is LLMModelSettingDetailAction.NewLLMModelKindChanged,
    LLMModelSettingDetailAction.ShowAddDialog,
    LLMModelSettingDetailAction.ConfirmAddLLMModel,
    LLMModelSettingDetailAction.RefreshModels -> true
    else -> false
}
