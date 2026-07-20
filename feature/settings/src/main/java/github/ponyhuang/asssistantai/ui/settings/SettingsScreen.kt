package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.ui.navigation.SettingsScaffold
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatDisplayPreferences: ChatDisplayRepository,
) : ViewModel() {
    val showToolActivity = chatDisplayPreferences.showToolActivity

    fun setShowToolActivity(show: Boolean) = chatDisplayPreferences.setShowToolActivity(show)
}

@Composable
fun SettingsScreen(
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val showToolActivity by viewModel.showToolActivity.collectAsStateWithLifecycle()

    SettingsHomeContent(
        showToolActivity = showToolActivity,
        appVersionName = appVersionName,
        onNavigateToModelService = onNavigateToModelService,
        onNavigateToDefaultModels = onNavigateToDefaultModels,
        onNavigateToVoiceWake = onNavigateToVoiceWake,
        onNavigateToMcpServers = onNavigateToMcpServers,
        onNavigateToWorkFiles = onNavigateToWorkFiles,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToToolAuthorization = onNavigateToToolAuthorization,
        onToggleToolActivity = viewModel::setShowToolActivity,
        modifier = modifier,
    )
}

@Composable
private fun SettingsHomeContent(
    showToolActivity: Boolean,
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    onToggleToolActivity: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = "模型服务",
                    subtitle = "API 密钥 · 服务地址 · 模型列表",
                    onClick = onNavigateToModelService,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "默认模型",
                    subtitle = "助手 · 快速响应 · 语音识别与播放",
                    onClick = onNavigateToDefaultModels,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.BluetoothAudio,
                    title = "语音唤醒",
                    subtitle = "蓝牙耳机 · 唤醒词 · 离线模型",
                    onClick = onNavigateToVoiceWake,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Build,
                    title = "MCP 服务器",
                    subtitle = "远程工具服务 · 连接与授权",
                    onClick = onNavigateToMcpServers,
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.Visibility,
                    title = "聊天显示",
                    subtitle = "显示工具调用和返回结果",
                    onClick = { onToggleToolActivity(!showToolActivity) },
                    trailingContent = {
                        Switch(
                            checked = showToolActivity,
                            onCheckedChange = onToggleToolActivity,
                        )
                    },
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Folder,
                    title = "工作文件",
                    subtitle = "授权助手搜索的本机文件夹",
                    onClick = onNavigateToWorkFiles,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Build,
                    title = "工具授权",
                    subtitle = "选择允许助手使用的本地工具",
                    onClick = onNavigateToToolAuthorization,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Security,
                    title = "权限管理",
                    subtitle = "预先授权聊天任务和语音功能所需权限",
                    onClick = onNavigateToPermissions,
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "Asssistant AI $appVersionName",
                )
            }
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
private annotation class SettingsFormFactorPreviews

@SettingsFormFactorPreviews
@Composable
private fun SettingsHomePreview() {
    AsssistantaiTheme {
        SettingsScaffold(title = "设置", onBack = {}) { modifier ->
            SettingsHomeContent(
                showToolActivity = true,
                appVersionName = "1.0",
                onNavigateToModelService = {},
                onNavigateToDefaultModels = {},
                onNavigateToVoiceWake = {},
                onNavigateToMcpServers = {},
                onNavigateToWorkFiles = {},
                onNavigateToPermissions = {},
                onNavigateToToolAuthorization = {},
                onToggleToolActivity = {},
                modifier = modifier,
            )
        }
    }
}
