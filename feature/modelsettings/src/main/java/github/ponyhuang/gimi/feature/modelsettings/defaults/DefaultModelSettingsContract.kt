package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/**
 * 模型选择器中的一行。
 *
 * @property capability 该行所属的能力分类；`null` 表示该行不区分能力（例如聊天模型），
 *   有值时用于把行按能力分组展示。
 * @property isSupported 该模型所属模板是否已接入此行的目标能力；未接入时仍然显示，
 *   但不可选中，避免用户以为配置失败而反复排查密钥。
 */
data class SelectableModelRow(
    val instance: ModelServiceInstance,
    val connection: ServiceConnection,
    val model: Model,
    val capability: ModelCapability? = null,
    val isSupported: Boolean = true,
) {
    fun selection() = ModelSelection(instance.id, connection.id, model.id)
}

data class DefaultModelSettingsUiState(
    val assistantSelection: ModelSelection? = null,
    val fastSelection: ModelSelection? = null,
    val speechSelection: ModelSelection? = null,
    val ttsSelection: ModelSelection? = null,
    val ttsVoiceId: String = "",
    val ttsVoiceOptions: List<TtsVoice> = emptyList(),
    val isTtsVoiceLoading: Boolean = false,
    /** 待呈现的一次性通知；Route 呈现后经 [DefaultModelSettingsAction.ConsumeNotice] 清除。 */
    val notice: DefaultModelSettingsNotice? = null,
    val chatModels: List<SelectableModelRow> = emptyList(),
    val speechModels: List<SelectableModelRow> = emptyList(),
    val ttsModels: List<SelectableModelRow> = emptyList(),
    /** Realtime 模式的默认模型；null 表示未配置，Realtime 模式因此不可进入。 */
    val realtimeSelection: ModelSelection? = null,
    /** Realtime 候选模型；只包含已接入原生 Realtime 适配器的模板。 */
    val realtimeModels: List<SelectableModelRow> = emptyList(),
    /** Realtime 自定义音色覆盖；null 表示跟随模型缺省音色。 */
    val realtimeVoiceId: String? = null,
    /** 当前生效的 Realtime 音色 ID（自定义覆盖或模型缺省音色）。 */
    val realtimeActiveVoiceId: String = "",
    val realtimeDefaultVoiceId: String = "",
    val realtimeDefaultVoiceName: String = "",
    val realtimeVoiceOptions: List<TtsVoice> = emptyList(),
    val isRealtimeVoiceLoading: Boolean = false,
    val dialog: DefaultModelDialog? = null,
    val isMutationBlocked: Boolean = false,
)

enum class DefaultModelDialog {
    Assistant,
    Fast,
    Speech,
    Tts,
    TtsVoice,
    RealtimeModel,
    RealtimeVoice,
}

sealed interface DefaultModelSettingsAction {
    data class ShowDialog(val dialog: DefaultModelDialog) : DefaultModelSettingsAction
    data class SelectModel(
        val target: DefaultModelDialog,
        val selection: ModelSelection,
    ) : DefaultModelSettingsAction
    data class SelectVoice(val voiceId: String) : DefaultModelSettingsAction
    /** 选择或恢复 Realtime 音色；null 或等于默认音色时重置为跟随模型缺省。 */
    data class SelectRealtimeVoice(val voiceId: String?) : DefaultModelSettingsAction
    /**
     * 选择 Realtime 默认模型；null 表示未配置（Realtime 模式随之不可进入）。
     * 选择器只发非空选择，null 仅供配置归一化/清除路径使用。
     */
    data class SelectRealtimeModel(val selection: ModelSelection?) : DefaultModelSettingsAction
    data object DismissDialog : DefaultModelSettingsAction

    /** 确认一次性通知已呈现，清空 [DefaultModelSettingsUiState.notice]。 */
    data object ConsumeNotice : DefaultModelSettingsAction
}

/** 默认模型页一次性通知；以 UiState 字段承载，由 Route 呈现后消费清除。 */
sealed interface DefaultModelSettingsNotice {
    /** 呈现一条本地化提示。 @param messageRes 文案的 string resource id。 */
    data class ShowToast(@StringRes val messageRes: Int) : DefaultModelSettingsNotice
}
