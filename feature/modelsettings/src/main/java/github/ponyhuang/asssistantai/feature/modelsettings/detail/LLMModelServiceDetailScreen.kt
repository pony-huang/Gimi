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
import androidx.compose.ui.unit.dp
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

    SettingsPageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked) {
                Text(
                    text = "Agent 任务进行中，请先停止任务后再修改。",
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
                        onOpenUrl(service.homepageUrl, "未配置主页链接")
                    },
                )
            }
            SettingsSectionTitle(text = "连接配置", modifier = Modifier.padding(top = 20.dp))
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
                        onOpenUrl(service.keyHelpUrl, "未配置密钥获取链接")
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
                    onOpenUrl = { url -> onOpenUrl(url, "未配置对应链接") },
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
    is ModelServiceDetailAction.RemoveModel,
    is ModelServiceDetailAction.NewModelIdChanged,
    is ModelServiceDetailAction.NewModelKindChanged,
    ModelServiceDetailAction.ShowAddDialog,
    ModelServiceDetailAction.ConfirmAddModel,
    ModelServiceDetailAction.RefreshModels -> true
    else -> false
}
