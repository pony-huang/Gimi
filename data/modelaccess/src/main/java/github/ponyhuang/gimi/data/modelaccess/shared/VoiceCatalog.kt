package github.ponyhuang.gimi.data.modelaccess.shared

import github.ponyhuang.gimi.data.modelaccess.deepseek.speech.DeepSeekTtsVoices
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxTtsVoices
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoTtsVoices
import github.ponyhuang.gimi.data.modelaccess.openai.speech.OpenAiTtsVoices
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.QwenAudioTtsVoices
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import javax.inject.Inject

/**
 * 厂商级联 TTS 内置音色目录的统一解析。
 *
 * 音色表跟随各厂商适配器住在 `<vendor>/…`，按模板标识解析。
 */
class VoiceCatalog @Inject constructor() : VoiceCatalogResolver {
    override fun builtInVoices(serviceId: String?): List<TtsVoice> = when (serviceId) {
        "deepseek" -> DeepSeekTtsVoices.all
        "minimax" -> MinimaxTtsVoices.all
        "mimo" -> MiMoTtsVoices.all
        "openai" -> OpenAiTtsVoices.all
        else -> emptyList()
    }

    override fun builtInDefaultVoice(serviceId: String?, modelId: String?): TtsVoice? = when (serviceId) {
        // 千问的音色按模型组织（跨模型使用会被服务端拒绝），服务级内置表为空，
        // 默认音色必须按当前模型收窄取第一个。
        "qwen" -> modelId?.let { QwenAudioTtsVoices.forModel(it) }.orEmpty().firstOrNull()
        else -> builtInVoices(serviceId).firstOrNull()
    }
}
