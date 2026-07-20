package github.ponyhuang.asssistantai.feature.modelsettings.detail

import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService

data class ModelServiceDetailUiState(
    val isLoading: Boolean = true,
    val service: ModelService? = null,
    val rows: List<ModelServiceDetailRow> = emptyList(),
    val isTestingKey: Boolean = false,
    val isRefreshing: Boolean = false,
    val notice: ModelServiceDetailNotice? = null,
    val shouldClose: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isProtocolMenuExpanded: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val newModelId: String = "custom-model",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isMutationBlocked: Boolean = false,
)

sealed interface ModelServiceDetailAction {
    data class Load(val serviceId: String) : ModelServiceDetailAction
    data class ApiKeyChanged(val value: String) : ModelServiceDetailAction
    data class ApiBaseUrlChanged(val value: String) : ModelServiceDetailAction
    data class ApiProtocolChanged(val value: ApiProtocol) : ModelServiceDetailAction
    data class EnabledChanged(val value: Boolean) : ModelServiceDetailAction
    data class ToggleGroup(val groupId: String) : ModelServiceDetailAction
    data class RemoveModel(val groupId: String, val modelId: String) : ModelServiceDetailAction
    data class NewModelIdChanged(val value: String) : ModelServiceDetailAction
    data class NewModelKindChanged(val value: NewModelKind) : ModelServiceDetailAction
    data object ToggleApiKeyVisibility : ModelServiceDetailAction
    data object ToggleProtocolMenu : ModelServiceDetailAction
    data object DismissProtocolMenu : ModelServiceDetailAction
    data object ShowAddDialog : ModelServiceDetailAction
    data object DismissAddDialog : ModelServiceDetailAction
    data object ConfirmAddModel : ModelServiceDetailAction
    data object TestConnection : ModelServiceDetailAction
    data object RefreshModels : ModelServiceDetailAction
    data object NoticeConsumed : ModelServiceDetailAction
    data object CloseConsumed : ModelServiceDetailAction
}

sealed interface ModelServiceDetailNotice {
    data object ServiceNotFound : ModelServiceDetailNotice
    data object ConnectionSucceeded : ModelServiceDetailNotice
    data object ConnectionFailed : ModelServiceDetailNotice
    data class ModelsSynchronized(val count: Int) : ModelServiceDetailNotice
    data object ModelSynchronizationFailed : ModelServiceDetailNotice
    data object AgentMutationBlocked : ModelServiceDetailNotice
}

sealed interface ModelServiceDetailRow {
    data class GroupHeader(
        val groupId: String,
        val groupName: String,
        val isExpanded: Boolean,
    ) : ModelServiceDetailRow

    data class ModelItem(
        val groupId: String,
        val model: Model,
    ) : ModelServiceDetailRow
}

enum class NewModelKind(val label: String) {
    Chat("聊天模型"),
    Stt("语音识别模型（STT）"),
    Tts("语音合成模型（TTS）"),
}
