package github.ponyhuang.asssistantai.domain.speech.repository

import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import kotlinx.coroutines.flow.StateFlow

interface VoiceWakeRepository {
    val state: StateFlow<VoiceWakeState>

    fun setKeyword(keyword: String): Result<Unit>

    fun installModel()

    fun start()

    fun stop()
}
