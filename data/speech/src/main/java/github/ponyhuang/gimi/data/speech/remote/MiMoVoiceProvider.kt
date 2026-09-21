package github.ponyhuang.gimi.data.speech.remote

import github.ponyhuang.gimi.domain.speech.model.MiMoTtsVoices
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiMo 本地音色提供者：
 * 提供预置的静态音色列表。
 */
@Singleton
class MiMoVoiceProvider @Inject constructor() : TtsVoiceProvider {

    override val serviceId: String = "mimo"

    override suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice> {
        return MiMoTtsVoices.all
    }
}
