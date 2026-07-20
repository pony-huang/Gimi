package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    SettingsPageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                HeaderSection(
                    service = service,
                    onToggleEnabled = {
                        onAction(ModelServiceDetailAction.EnabledChanged(it))
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
                        onAction(ModelServiceDetailAction.ApiKeyChanged(it))
                    },
                    onToggleVisibility = {
                        onAction(ModelServiceDetailAction.ToggleApiKeyVisibility)
                    },
                    onTest = { onAction(ModelServiceDetailAction.TestConnection) },
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
                    onToggleMenu = { onAction(ModelServiceDetailAction.ToggleProtocolMenu) },
                    onDismissMenu = { onAction(ModelServiceDetailAction.DismissProtocolMenu) },
                    onProtocolChange = {
                        onAction(ModelServiceDetailAction.ApiProtocolChanged(it))
                    },
                    onBaseUrlChange = {
                        onAction(ModelServiceDetailAction.ApiBaseUrlChanged(it))
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
                    onAction = onAction,
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
