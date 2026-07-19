package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.isChatModel
import github.ponyhuang.asssistantai.data.isConfiguredForChat
import github.ponyhuang.asssistantai.speech.MiMoTtsVoices
import github.ponyhuang.asssistantai.speech.TtsVoice
import github.ponyhuang.asssistantai.ui.chat.EnabledModelRow
import github.ponyhuang.asssistantai.ui.common.PickerSingleChoiceDialog
import javax.inject.Inject

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
) : ViewModel() {
    val services = modelServices.services
    val defaultAssistantSelection = modelServices.defaultAssistantSelection
    val fastModelSelection = modelServices.fastModelSelection
    val defaultSpeechSelection = modelServices.defaultSpeechSelection
    val defaultTtsSelection = modelServices.defaultTtsSelection
    val defaultTtsVoice = modelServices.defaultTtsVoice

    fun setDefaultAssistantModel(selection: LLMModelSelection) =
        modelServices.setDefaultAssistantSelection(selection)

    fun setFastModel(selection: LLMModelSelection) = modelServices.setFastModelSelection(selection)

    fun setDefaultSpeechModel(selection: LLMModelSelection) =
        modelServices.setDefaultSpeechSelection(selection)

    fun setDefaultTtsModel(selection: LLMModelSelection) =
        modelServices.setDefaultTtsSelection(selection)

    fun setDefaultTtsVoice(voiceId: String) = modelServices.setDefaultTtsVoice(voiceId)
}

@Composable
fun DefaultModelSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val defaultAssistantSelection by viewModel.defaultAssistantSelection.collectAsStateWithLifecycle()
    val fastModelSelection by viewModel.fastModelSelection.collectAsStateWithLifecycle()
    val defaultSpeechSelection by viewModel.defaultSpeechSelection.collectAsStateWithLifecycle()
    val defaultTtsSelection by viewModel.defaultTtsSelection.collectAsStateWithLifecycle()
    val defaultTtsVoice by viewModel.defaultTtsVoice.collectAsStateWithLifecycle()

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
    val enabledTtsModels = remember(services) {
        services.filter { it.isEnabled && it.apiKey.isNotBlank() }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models
                    .filter { it.isTts }
                    .map { model -> EnabledModelRow(service, group, model) }
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                DefaultModelOption(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "默认助手模型",
                    subtitle = "创建新助手时使用的模型",
                    selection = defaultAssistantSelection,
                    rows = enabledChatModels,
                    onClick = { selectingDefaultAssistant = true },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.Default.FlashOn,
                    title = "快速模型",
                    subtitle = "用于需要快速响应的助手功能",
                    selection = fastModelSelection,
                    rows = enabledChatModels,
                    onClick = { selectingFastModel = true },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.Default.Mic,
                    title = "默认语音模型",
                    subtitle = "用于语音输入，仅显示语音识别模型",
                    selection = defaultSpeechSelection,
                    rows = enabledSpeechModels,
                    onClick = { selectingDefaultSpeech = true },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "默认语音播放模型",
                    subtitle = "用于朗读助手回复，仅显示语音合成模型",
                    selection = defaultTtsSelection,
                    rows = enabledTtsModels,
                    onClick = { selectingDefaultTts = true },
                )
            }
            item {
                val voice = MiMoTtsVoices.all.firstOrNull { it.id == defaultTtsVoice }
                val enabled = enabledTtsModels.any { it.toSelection() == defaultTtsSelection }
                SettingsListItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "语音播放音色",
                    subtitle = voice?.name ?: defaultTtsVoice,
                    onClick = if (enabled) ({ selectingTtsVoice = true }) else null,
                    iconTint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }

    if (selectingDefaultAssistant) {
        ModelChoiceDialog(
            rows = enabledChatModels,
            currentSelection = defaultAssistantSelection,
            title = "选择默认助手模型",
            onPick = {
                viewModel.setDefaultAssistantModel(it.toSelection())
                selectingDefaultAssistant = false
            },
            onDismiss = { selectingDefaultAssistant = false },
        )
    }
    if (selectingFastModel) {
        ModelChoiceDialog(
            rows = enabledChatModels,
            currentSelection = fastModelSelection,
            title = "选择快速模型",
            onPick = {
                viewModel.setFastModel(it.toSelection())
                selectingFastModel = false
            },
            onDismiss = { selectingFastModel = false },
        )
    }
    if (selectingDefaultSpeech) {
        ModelChoiceDialog(
            rows = enabledSpeechModels,
            currentSelection = defaultSpeechSelection,
            title = "选择默认语音模型",
            onPick = {
                viewModel.setDefaultSpeechModel(it.toSelection())
                selectingDefaultSpeech = false
            },
            onDismiss = { selectingDefaultSpeech = false },
        )
    }
    if (selectingDefaultTts) {
        ModelChoiceDialog(
            rows = enabledTtsModels,
            currentSelection = defaultTtsSelection,
            title = "选择默认语音播放模型",
            onPick = {
                viewModel.setDefaultTtsModel(it.toSelection())
                selectingDefaultTts = false
            },
            onDismiss = { selectingDefaultTts = false },
        )
    }
    if (selectingTtsVoice) {
        PickerSingleChoiceDialog(
            options = MiMoTtsVoices.all,
            selected = { it.id == defaultTtsVoice },
            key = TtsVoice::id,
            title = "选择语音播放音色",
            optionTitle = TtsVoice::name,
            optionSubtitle = { listOfNotNull(it.language, it.gender).joinToString(" · ") },
            emptyText = "暂无可用音色",
            onPick = {
                viewModel.setDefaultTtsVoice(it.id)
                selectingTtsVoice = false
            },
            onDismiss = { selectingTtsVoice = false },
        )
    }
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
    SettingsListItem(
        icon = icon,
        title = title,
        subtitle = selected?.let { "${it.model.modelName} · ${it.service.serviceName}" } ?: subtitle,
        onClick = onClick,
    )
}

@Composable
private fun ModelChoiceDialog(
    rows: List<EnabledModelRow>,
    currentSelection: LLMModelSelection?,
    title: String,
    onPick: (EnabledModelRow) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSingleChoiceDialog(
        options = rows,
        selected = { it.toSelection() == currentSelection },
        key = { "${it.service.serviceId}/${it.group.groupId}/${it.model.modelId}" },
        title = title,
        optionTitle = { it.model.modelName },
        optionSubtitle = { "${it.service.serviceName} · ${it.group.groupName}" },
        emptyText = "暂无可用模型，请先在模型服务中启用至少一个模型。",
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

private fun EnabledModelRow.toSelection() = LLMModelSelection(
    serviceId = service.serviceId,
    groupId = group.groupId,
    modelId = model.modelId,
)
