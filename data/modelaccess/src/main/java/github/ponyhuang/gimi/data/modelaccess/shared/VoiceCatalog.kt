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
 * 厂商级联 TTS 内置音色目录与自定义音色策略的统一解析。
 *
 * 音色表跟随各厂商适配器住在 `<vendor>/…`，按模板标识解析；千问基础音色没有列表接口，
 * 因此是唯一开放自由填写音色 ID 的服务（命名模板与官方列表页随策略一起给出）。
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

    override fun allowsCustomVoice(serviceId: String?): Boolean = serviceId == QWEN_TEMPLATE_ID

    override fun customVoiceIdTemplate(serviceId: String?): String? =
        if (allowsCustomVoice(serviceId)) QWEN_BASE_VOICE_TEMPLATE else null

    override fun customVoiceListUrl(serviceId: String?): String? =
        if (allowsCustomVoice(serviceId)) QWEN_VOICE_LIST_URL else null

    private companion object {
        /**
         * 千问音频：内置表只是系统音色的一部分，3.0 还有五百余个基础音色没有列表接口，
         * 因此除了选择还需要允许直接填写音色 ID。
         */
        private const val QWEN_TEMPLATE_ID = "qwen"

        /** 千问基础音色没有列表接口，用户需要按这个命名规则拼出 voice 参数。 */
        private const val QWEN_BASE_VOICE_TEMPLATE = "qwen-audio-3.0-tts-{plus|flash}-{后缀}"

        /** 千问官方音色列表页，用于查基础音色的后缀。 */
        private const val QWEN_VOICE_LIST_URL = "https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list"
    }
}
