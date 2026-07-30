package github.ponyhuang.gimi.domain.speech.usecase

import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import javax.inject.Inject

class ObserveSpeechRecognitionAvailabilityUseCase @Inject constructor(
    private val repository: SpeechRecognitionRepository,
) {
    operator fun invoke() = repository.availability
}

class TranscribeSpeechUseCase @Inject constructor(
    private val repository: SpeechRecognitionRepository,
) {
    suspend operator fun invoke(pcm16: ByteArray): String = repository.transcribe(pcm16)
}

class ControlSpeechPlaybackUseCase @Inject constructor(
    private val repository: SpeechPlaybackRepository,
) {
    val state = repository.state
    val errors = repository.errors

    fun toggle(messageId: String, text: String) = repository.toggle(messageId, text)

    fun clearSession() = repository.clearSession()
}
