package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.ui.settings.SettingsCard
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

/**
 * 详情页屏幕：把可用的服务配置按卡片区块组织。
 */
@Composable
fun ModelServiceDetailScreen(
    viewModel: ModelServiceDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val service by viewModel.service.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    val svc = service ?: return

    SettingsPageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                HeaderSection(
                    service = svc,
                    onToggleEnabled = viewModel::onToggleEnabled,
                )
            }
            SettingsSectionTitle(text = "连接配置", modifier = Modifier.padding(top = 20.dp))
            SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                ApiKeySection(
                    service = svc,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    onTest = viewModel::testApiKey,
                )
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
            ) {
                ApiBaseUrlSection(
                    service = svc,
                    onBaseTypeChange = viewModel::onBaseTypeChange,
                    onBaseUrlChange = viewModel::onApiBaseUrlChange,
                )
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp),
            ) {
                LLMModelManagementSection(
                    service = svc,
                    rows = rows,
                    onToggleGroup = viewModel::toggleGroupExpanded,
                    onRemoveModel = viewModel::removeModel,
                    onAppendModel = viewModel::appendModel,
                    onRefreshRemote = viewModel::refreshRemoteModels,
                )
            }
            SettingsCard(
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
            ) {
                FooterSection(service = svc)
            }
        }
    }
}
