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
    /** TTS 服务是否允许填写内置音色表之外的音色 ID（声音复刻、基础音色）。 */
    val ttsVoiceCustomInputAllowed: Boolean = false,
    /** 开放自由填写厂商（千问）的音色 ID 命名模板；null 表示不展示。 */
    val ttsVoiceIdTemplate: String? = null,
    /** 开放自由填写厂商的官方音色列表页；null 表示不展示。 */
    val ttsVoiceDocsUrl: String? = null,
    val chatModels: List<SelectableModelRow> = emptyList(),
    val speechModels: List<SelectableModelRow> = emptyList(),
    val ttsModels: List<SelectableModelRow> = emptyList(),
    /** Realtime 模式的默认模型；null 表示未配置，Realtime 模式因此不可进入。 */
    val realtimeSelection: ModelSelection? = null,
    /** Realtime 候选模型；只包含已接入原生 Realtime 适配器的模板。 */
    val realtimeModels: List<SelectableModelRow> = emptyList(),
    /** 厂商内置的 Realtime 会话音色表；为空表示该厂商走自由输入。 */
    val realtimeVoiceOptions: List<TtsVoice> = emptyList(),
    /** 所选 Realtime 模型在该厂商下的缺省会话音色 ID。 */
    val realtimeDefaultVoice: String = "",
    /** 当前生效的自定义音色 ID；null 表示使用 [realtimeDefaultVoice]。 */
    val realtimeVoiceId: String? = null,
    /** 该厂商是否允许填写内置音色之外的自定义 voice_id（声音复刻）。 */
    val realtimeCustomVoiceAllowed: Boolean = false,
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
    /** 选择 Realtime 默认模型；null 表示取消配置（Realtime 模式随之不可进入）。 */
    data class SelectRealtimeModel(val selection: ModelSelection?) : DefaultModelSettingsAction
    /** 设置 Realtime 会话音色；null 表示回到所选模型的缺省音色。 */
    data class SelectRealtimeVoice(val voiceId: String?) : DefaultModelSettingsAction
    data object DismissDialog : DefaultModelSettingsAction
}

/** 一次性 UI 反馈（Toast 等），由 Route 经 effects 通道消费。 */
sealed interface DefaultModelSettingsEffect {
    /** @param messageRes 文案的 string resource id。 */
    data class ShowToast(@StringRes val messageRes: Int) : DefaultModelSettingsEffect
}
