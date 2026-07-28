package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.annotation.StringRes
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.feature.modelsettings.R

data class LLMModelSettingDetailUiState(
    val isLoading: Boolean = true,
    val service: LLMModelSetting? = null,
    val rows: List<LLMModelSettingDetailRow> = emptyList(),
    val isTestingKey: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isProtocolMenuExpanded: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val newModelId: String = "custom-model",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isMutationBlocked: Boolean = false,
)

sealed interface LLMModelSettingDetailAction {
    data class Load(val serviceId: String) : LLMModelSettingDetailAction
    data class ApiKeyChanged(val value: String) : LLMModelSettingDetailAction
    data class ApiBaseUrlChanged(val value: String) : LLMModelSettingDetailAction
    data class ApiProtocolChanged(val value: ApiProtocol) : LLMModelSettingDetailAction
    data class EnabledChanged(val value: Boolean) : LLMModelSettingDetailAction
    data class ToggleGroup(val groupId: String) : LLMModelSettingDetailAction
    data class RemoveLLMModel(val groupId: String, val modelId: String) : LLMModelSettingDetailAction
    data class NewLLMModelIdChanged(val value: String) : LLMModelSettingDetailAction
    data class NewLLMModelKindChanged(val value: NewModelKind) : LLMModelSettingDetailAction
    data object ToggleApiKeyVisibility : LLMModelSettingDetailAction
    data object ToggleProtocolMenu : LLMModelSettingDetailAction
    data object DismissProtocolMenu : LLMModelSettingDetailAction
    data object ShowAddDialog : LLMModelSettingDetailAction
    data object DismissAddDialog : LLMModelSettingDetailAction
    data object ConfirmAddLLMModel : LLMModelSettingDetailAction
    data object TestConnection : LLMModelSettingDetailAction
    data object RefreshModels : LLMModelSettingDetailAction
}

sealed interface LLMModelSettingDetailNotice {
    data object SettingNotFoundLLM : LLMModelSettingDetailNotice
    data object ConnectionSucceeded : LLMModelSettingDetailNotice
    data object ConnectionFailed : LLMModelSettingDetailNotice
    data class ModelsSynchronized(val count: Int) : LLMModelSettingDetailNotice
    data object LLMModelSynchronizationFailed : LLMModelSettingDetailNotice
    data object AgentMutationBlocked : LLMModelSettingDetailNotice
}

/** 一次性 UI 反馈（Toast、关闭页面等），由 Route 经 effects 通道消费。 */
sealed interface LLMModelSettingDetailEffect {
    data class ShowToast(val notice: LLMModelSettingDetailNotice) : LLMModelSettingDetailEffect
    data object Close : LLMModelSettingDetailEffect
}

sealed interface LLMModelSettingDetailRow {
    data class GroupHeader(
        val groupId: String,
        val groupName: String,
        val isExpanded: Boolean,
    ) : LLMModelSettingDetailRow

    data class LLMModelItem(
        val groupId: String,
        val model: Model,
    ) : LLMModelSettingDetailRow
}

enum class NewModelKind(@StringRes val labelRes: Int) {
    Chat(R.string.modelsettings_new_model_chat),
    Stt(R.string.modelsettings_new_model_stt),
    Tts(R.string.modelsettings_new_model_tts),
}
