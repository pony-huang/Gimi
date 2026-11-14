package github.ponyhuang.gimi.data.modelaccess.qwen.speech

import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 千问音频的音色来源。
 *
 * 音色是静态系统音色，不需要也没有可用的远程查询接口；
 * 但音色与模型绑定，因此必须用当前模型 ID 过滤，不能只按服务返回整张表。
 */
@Singleton
class DashScopeVoiceProvider @Inject constructor() : TtsVoiceProvider {
    override val serviceIds: Set<String> = setOf(DASHSCOPE_SERVICE_ID)

    override suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice> =
        QwenAudioTtsVoices.forModel(context.modelId)
}
