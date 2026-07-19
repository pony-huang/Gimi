package github.ponyhuang.asssistantai.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.isChatModel
import github.ponyhuang.asssistantai.data.isConfiguredForChat
import github.ponyhuang.asssistantai.ui.chat.EnabledModelRow
import github.ponyhuang.asssistantai.voice.BluetoothVoiceController
import github.ponyhuang.asssistantai.voice.BluetoothVoiceUiState
import github.ponyhuang.asssistantai.voice.WakeModelStatus
import javax.inject.Inject

@HiltViewModel
class VoiceWakeSettingsViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val bluetoothVoiceController: BluetoothVoiceController,
) : ViewModel() {
    val services = modelServices.services
    val defaultSpeechSelection = modelServices.defaultSpeechSelection
    val bluetoothVoiceState = bluetoothVoiceController.state

    fun setWakeKeyword(keyword: String): Result<Unit> = bluetoothVoiceController.setKeyword(keyword)

    fun installWakeModel() = bluetoothVoiceController.installModel()

    fun startVoiceWake() = bluetoothVoiceController.start()

    fun stopVoiceWake() = bluetoothVoiceController.stop()
}

@Composable
fun VoiceWakeSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: VoiceWakeSettingsViewModel = hiltViewModel(),
) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val defaultSpeechSelection by viewModel.defaultSpeechSelection.collectAsStateWithLifecycle()
    val voiceState by viewModel.bluetoothVoiceState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val enabledChatModels = remember(services) {
        services.filter { it.isConfiguredForChat }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models
                    .filter { it.isChatModel }
                    .map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    val enabledSpeechModels = remember(services) {
        services.filter { it.isEnabled && it.apiKey.isNotBlank() }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models
                    .filter { it.isStt }
                    .map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    val defaultSpeechReady = enabledSpeechModels.any { it.toSelection() == defaultSpeechSelection }
    val configurationReady = enabledChatModels.isNotEmpty() && defaultSpeechReady

    var wakeKeywordDraft by remember { mutableStateOf(voiceState.keyword) }
    var wakeKeywordError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(voiceState.keyword) {
        if (wakeKeywordDraft != voiceState.keyword) wakeKeywordDraft = voiceState.keyword
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) viewModel.startVoiceWake()
    }
    val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    val requestVoiceStart = {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.startVoiceWake()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { SettingsSectionTitle(text = "监听") }
            item {
                SettingsListItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = "后台监听",
                    subtitle = voiceState.deviceName?.let { "当前设备：$it" }
                        ?: voiceState.message
                        ?: "连接蓝牙耳机后，通过唤醒词在后台执行任务",
                    onClick = {
                        if (voiceState.isRunning) {
                            viewModel.stopVoiceWake()
                        } else {
                            when {
                                voiceState.model.status != WakeModelStatus.Ready ->
                                    viewModel.installWakeModel()
                                !configurationReady -> Unit
                                else -> requestVoiceStart()
                            }
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = voiceState.isRunning,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    when {
                                        voiceState.model.status != WakeModelStatus.Ready ->
                                            viewModel.installWakeModel()
                                        !configurationReady -> Unit
                                        else -> requestVoiceStart()
                                    }
                                } else {
                                    viewModel.stopVoiceWake()
                                }
                            },
                        )
                    },
                )
            }

            item { SettingsSectionTitle(text = "唤醒词") }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = wakeKeywordDraft,
                        onValueChange = {
                            wakeKeywordDraft = it
                            wakeKeywordError = null
                        },
                        label = { Text("唤醒词") },
                        supportingText = { Text(wakeKeywordError ?: "2–20 个字符，例如：你好助手") },
                        isError = wakeKeywordError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.setWakeKeyword(wakeKeywordDraft)
                                .onFailure { wakeKeywordError = it.message }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text("保存唤醒词")
                    }
                }
            }

            item { SettingsSectionTitle(text = "离线模型", modifier = Modifier.padding(top = 12.dp)) }
            item {
                WakeModelRow(
                    voiceState = voiceState,
                    onInstall = viewModel::installWakeModel,
                )
            }
            if (!configurationReady) {
                item {
                    Text(
                        text = "启用前请配置可用的默认助手模型和语音识别模型。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WakeModelRow(
    voiceState: BluetoothVoiceUiState,
    onInstall: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("离线中文唤醒模型", fontWeight = FontWeight.Medium) },
        supportingContent = {
            when (voiceState.model.status) {
                WakeModelStatus.Missing -> Text("已内置 APK，首次启用时安装")
                WakeModelStatus.Downloading -> Column {
                    Text("正在读取内置模型 ${(voiceState.model.progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { voiceState.model.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WakeModelStatus.Extracting -> Text("正在安装模型")
                WakeModelStatus.Ready -> Text("已安装，仅在本机识别唤醒词")
                WakeModelStatus.Error -> Text(voiceState.model.message ?: "安装失败")
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            if (voiceState.model.status == WakeModelStatus.Missing ||
                voiceState.model.status == WakeModelStatus.Error
            ) {
                TextButton(onClick = onInstall) {
                    Text(if (voiceState.model.status == WakeModelStatus.Error) "重试" else "安装")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

private fun EnabledModelRow.toSelection() = LLMModelSelection(
    serviceId = service.serviceId,
    groupId = group.groupId,
    modelId = model.modelId,
)
