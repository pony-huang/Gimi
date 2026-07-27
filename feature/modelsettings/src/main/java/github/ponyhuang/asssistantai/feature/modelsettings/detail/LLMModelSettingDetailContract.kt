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
    val notice: LLMModelSettingDetailNotice? = null,
    val shouldClose: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isProtocolMenuExpanded: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val newModelId: String = "custom-model",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isMutationBlocked: Boolean = false,
)

sealed interface LLmModelSettingDetailAction {
    data class Load(val serviceId: String) : LLmModelSettingDetailAction
    data class ApiKeyChanged(val value: String) : LLmModelSettingDetailAction
    data class ApiBaseUrlChanged(val value: String) : LLmModelSettingDetailAction
    data class ApiProtocolChanged(val value: ApiProtocol) : LLmModelSettingDetailAction
    data class EnabledChanged(val value: Boolean) : LLmModelSettingDetailAction
    data class ToggleGroup(val groupId: String) : LLmModelSettingDetailAction
    data class RemoveLLmModel(val groupId: String, val modelId: String) : LLmModelSettingDetailAction
    data class NewLLmModelIdChanged(val value: String) : LLmModelSettingDetailAction
    data class NewLLmModelKindChanged(val value: NewModelKind) : LLmModelSettingDetailAction
    data object ToggleApiKeyVisibility : LLmModelSettingDetailAction
    data object ToggleProtocolMenu : LLmModelSettingDetailAction
    data object DismissProtocolMenu : LLmModelSettingDetailAction
    data object ShowAddDialog : LLmModelSettingDetailAction
    data object DismissAddDialog : LLmModelSettingDetailAction
    data object ConfirmAddLLmModel : LLmModelSettingDetailAction
    data object TestConnection : LLmModelSettingDetailAction
    data object RefreshModels : LLmModelSettingDetailAction
    data object NoticeConsumed : LLmModelSettingDetailAction
    data object CloseConsumed : LLmModelSettingDetailAction
}

sealed interface LLMModelSettingDetailNotice {
    data object SettingNotFoundLLM : LLMModelSettingDetailNotice
    data object ConnectionSucceeded : LLMModelSettingDetailNotice
    data object ConnectionFailed : LLMModelSettingDetailNotice
    data class ModelsSynchronized(val count: Int) : LLMModelSettingDetailNotice
    data object LLMModelSynchronizationFailed : LLMModelSettingDetailNotice
    data object AgentMutationBlocked : LLMModelSettingDetailNotice
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
