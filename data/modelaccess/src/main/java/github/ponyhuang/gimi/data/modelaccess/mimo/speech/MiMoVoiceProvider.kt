package github.ponyhuang.gimi.data.modelaccess.mimo.speech

import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiMo 本地音色提供者：
 * 提供预置的静态音色列表。
 */
@Singleton
class MiMoVoiceProvider @Inject constructor() : TtsVoiceProvider {

    override val serviceIds: Set<String> = setOf(MIMO_SPEECH_SERVICE_ID)

    override suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice> {
        return MiMoTtsVoices.all
    }
}
