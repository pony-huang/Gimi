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
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
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

@Preview(showBackground = true)
@Composable
private fun LLMModelSettingDetailScreenPreview() {
    AsssistantaiTheme {
        val service = LLMModelSetting(
            id = "openai",
            name = "OpenAI",
            isEnabled = true,
            apiKey = "sk-test",
            apiBaseUrl = "https://api.openai.com/v1",
            apiProtocol = ApiProtocol.Standard,
            anthropicBaseUrl = "https://api.anthropic.com",
            groups = listOf(
                ModelGroup(
                    id = "gpt",
                    name = "GPT",
                    models = listOf(Model(id = "gpt-4o", name = "GPT-4o")),
                ),
            ),
            homepageUrl = "https://openai.com",
            keyHelpUrl = "https://help.openai.com",
        )
        LLMModelSettingDetailScreen(
            state = LLMModelSettingDetailUiState(
                isLoading = false,
                service = service,
                rows = listOf(
                    LLMModelSettingDetailRow.GroupHeader(
                        groupId = service.groups.first().id,
                        groupName = service.groups.first().name,
                        isExpanded = true,
                    ),
                    LLMModelSettingDetailRow.LLMModelItem(
                        groupId = service.groups.first().id,
                        model = service.groups.first().models.first(),
                    ),
                ),
            ),
            onAction = {},
            onOpenUrl = { _, _ -> },
        )
    }
}
