package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import github.ponyhuang.asssistantai.BuildConfig
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.ui.chat.EnabledModelRow
import github.ponyhuang.asssistantai.ui.chat.ModelPickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import github.ponyhuang.asssistantai.data.DocumentDirectoryRepository
import github.ponyhuang.asssistantai.data.ChatDisplayPreferences
import github.ponyhuang.asssistantai.speech.MiMoTtsVoices
import github.ponyhuang.asssistantai.speech.TtsVoice
import javax.inject.Inject

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val documentDirectories: DocumentDirectoryRepository,
    private val chatDisplayPreferences: ChatDisplayPreferences,
) : ViewModel() {
    val services = modelServices.services
    val defaultAssistantSelection = modelServices.defaultAssistantSelection
    val fastModelSelection = modelServices.fastModelSelection
    val defaultSpeechSelection = modelServices.defaultSpeechSelection
    val defaultTtsSelection = modelServices.defaultTtsSelection
    val defaultTtsVoice = modelServices.defaultTtsVoice
    val directories = documentDirectories.directories
    val showToolActivity = chatDisplayPreferences.showToolActivity

    fun setDefaultAssistantModel(selection: LLMModelSelection) =
        modelServices.setDefaultAssistantSelection(selection)

    fun setFastModel(selection: LLMModelSelection) = modelServices.setFastModelSelection(selection)

    fun setDefaultSpeechModel(selection: LLMModelSelection) =
        modelServices.setDefaultSpeechSelection(selection)

    fun setDefaultTtsModel(selection: LLMModelSelection) =
        modelServices.setDefaultTtsSelection(selection)

    fun setDefaultTtsVoice(voiceId: String) = modelServices.setDefaultTtsVoice(voiceId)

    fun addDocumentDirectory(uri: Uri) = documentDirectories.addDirectory(uri)

    fun removeDocumentDirectory(uri: Uri) = documentDirectories.removeDirectory(uri)

    fun setShowToolActivity(show: Boolean) = chatDisplayPreferences.setShowToolActivity(show)
}

/** Settings landing page containing only available settings capabilities. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val defaultAssistantSelection by viewModel.defaultAssistantSelection.collectAsStateWithLifecycle()
    val fastModelSelection by viewModel.fastModelSelection.collectAsStateWithLifecycle()
    val defaultSpeechSelection by viewModel.defaultSpeechSelection.collectAsStateWithLifecycle()
    val defaultTtsSelection by viewModel.defaultTtsSelection.collectAsStateWithLifecycle()
    val defaultTtsVoice by viewModel.defaultTtsVoice.collectAsStateWithLifecycle()
    val documentDirectories by viewModel.directories.collectAsStateWithLifecycle()
    val showToolActivity by viewModel.showToolActivity.collectAsStateWithLifecycle()
    val documentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::addDocumentDirectory) }
    val enabledChatModels = remember(services) {
        services.filter { it.isEnabled }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models.filterNot { it.isStt || it.isTts }.map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    val enabledSpeechModels = remember(services) {
        services.filter { it.isEnabled && it.apiKey.isNotBlank() }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models.filter { it.isStt }.map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    val enabledTtsModels = remember(services) {
        services.filter { it.isEnabled && it.apiKey.isNotBlank() }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models.filter { it.isTts }.map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    var selectingDefaultAssistant by remember { mutableStateOf(false) }
    var selectingFastModel by remember { mutableStateOf(false) }
    var selectingDefaultSpeech by remember { mutableStateOf(false) }
    var selectingDefaultTts by remember { mutableStateOf(false) }
    var selectingTtsVoice by remember { mutableStateOf(false) }

    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 28.dp),
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = "模型服务",
                    subtitle = "配置 API 密钥、地址和模型列表",
                    onClick = onNavigateToModelService,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Build,
                    title = "MCP 服务器",
                    subtitle = "配置远程 MCP 工具服务",
                    onClick = onNavigateToMcpServers,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsSectionTitle(
                    text = "默认模型",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                )
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "默认助手模型",
                        subtitle = "创建新助手时使用的模型；未设置时使用第一个可用模型",
                        selection = defaultAssistantSelection,
                        rows = enabledChatModels,
                        onClick = { selectingDefaultAssistant = true },
                    )
                    DefaultModelOption(
                        icon = Icons.Default.FlashOn,
                        title = "快速模型",
                        subtitle = "用于需要快速响应的助手功能",
                        selection = fastModelSelection,
                        rows = enabledChatModels,
                        onClick = { selectingFastModel = true },
                    )
                    DefaultModelOption(
                        icon = Icons.Default.Mic,
                        title = "默认语音模型",
                        subtitle = "用于语音输入；仅显示语音识别模型",
                        selection = defaultSpeechSelection,
                        rows = enabledSpeechModels,
                        onClick = { selectingDefaultSpeech = true },
                    )
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "默认语音播放模型",
                        subtitle = "用于朗读助手回复；仅显示语音合成模型",
                        selection = defaultTtsSelection,
                        rows = enabledTtsModels,
                        onClick = { selectingDefaultTts = true },
                    )
                    TtsVoiceOption(
                        selectedVoiceId = defaultTtsVoice,
                        enabled = enabledTtsModels.any { it.toSelection() == defaultTtsSelection },
                        onClick = { selectingTtsVoice = true },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsSectionTitle(
                    text = "聊天显示",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                )
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text("显示工具调用", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text("在对话中显示工具调用和返回结果")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Build, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = showToolActivity,
                                onCheckedChange = viewModel::setShowToolActivity,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setShowToolActivity(!showToolActivity)
                            },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsSectionTitle(
                    text = "本机文件搜索",
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                )
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text("文档搜索目录", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(
                                if (documentDirectories.isEmpty()) {
                                    "选择允许助手搜索的文件夹"
                                } else {
                                    "已授权 ${documentDirectories.size} 个目录"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { documentTreeLauncher.launch(null) }) {
                                Icon(Icons.Default.Add, contentDescription = "添加文档搜索目录")
                            }
                        },
                    )
                    documentDirectories.forEach { uri ->
                        androidx.compose.material3.ListItem(
                            headlineContent = {
                                Text(
                                    uri.lastPathSegment ?: uri.toString(),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    uri.authority.orEmpty(),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeDocumentDirectory(uri) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "移除目录")
                                }
                            },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp).weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (selectingDefaultAssistant) {
        ModelPickerDialog(
            rows = enabledChatModels,
            currentSelection = defaultAssistantSelection,
            title = "选择默认助手模型",
            onPick = { row ->
                viewModel.setDefaultAssistantModel(row.toSelection())
                selectingDefaultAssistant = false
            },
            onDismiss = { selectingDefaultAssistant = false },
        )
    }
    if (selectingFastModel) {
        ModelPickerDialog(
            rows = enabledChatModels,
            currentSelection = fastModelSelection,
            title = "选择快速模型",
            onPick = { row ->
                viewModel.setFastModel(row.toSelection())
                selectingFastModel = false
            },
            onDismiss = { selectingFastModel = false },
        )
    }
    if (selectingDefaultSpeech) {
        ModelPickerDialog(
            rows = enabledSpeechModels,
            currentSelection = defaultSpeechSelection,
            title = "选择默认语音模型",
            onPick = { row ->
                viewModel.setDefaultSpeechModel(row.toSelection())
                selectingDefaultSpeech = false
            },
            onDismiss = { selectingDefaultSpeech = false },
        )
    }
    if (selectingDefaultTts) {
        ModelPickerDialog(
            rows = enabledTtsModels,
            currentSelection = defaultTtsSelection,
            title = "选择默认语音播放模型",
            onPick = { row ->
                viewModel.setDefaultTtsModel(row.toSelection())
                selectingDefaultTts = false
            },
            onDismiss = { selectingDefaultTts = false },
        )
    }
    if (selectingTtsVoice) {
        TtsVoicePickerDialog(
            voices = MiMoTtsVoices.all,
            selectedVoiceId = defaultTtsVoice,
            onPick = { voice ->
                viewModel.setDefaultTtsVoice(voice.id)
                selectingTtsVoice = false
            },
            onDismiss = { selectingTtsVoice = false },
        )
    }
}

@Composable
private fun TtsVoiceOption(
    selectedVoiceId: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val voice = MiMoTtsVoices.all.firstOrNull { it.id == selectedVoiceId }
    androidx.compose.material3.ListItem(
        headlineContent = { Text("语音播放音色", fontWeight = FontWeight.Medium) },
        supportingContent = { Text(voice?.name ?: selectedVoiceId) },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.Tune, contentDescription = "选择语音播放音色") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun TtsVoicePickerDialog(
    voices: List<TtsVoice>,
    selectedVoiceId: String,
    onPick: (TtsVoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择语音播放音色") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(voices, key = TtsVoice::id) { voice ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(voice.name) },
                        supportingContent = {
                            val detail = listOfNotNull(voice.language, voice.gender).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail)
                        },
                        leadingContent = {
                            RadioButton(
                                selected = voice.id == selectedVoiceId,
                                onClick = { onPick(voice) },
                            )
                        },
                        modifier = Modifier.clickable { onPick(voice) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DefaultModelOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selection: LLMModelSelection?,
    rows: List<EnabledModelRow>,
    onClick: () -> Unit,
) {
    val selected = remember(rows, selection) {
        rows.firstOrNull { it.toSelection() == selection }
    }
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(selected?.let { "${it.model.modelName} | ${it.service.serviceName}" } ?: subtitle)
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.Tune, contentDescription = "选择$title") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun EnabledModelRow.toSelection() = LLMModelSelection(
    serviceId = service.serviceId,
    groupId = group.groupId,
    modelId = model.modelId,
)
