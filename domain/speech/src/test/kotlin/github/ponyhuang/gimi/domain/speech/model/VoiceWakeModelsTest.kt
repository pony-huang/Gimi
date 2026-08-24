package github.ponyhuang.gimi.domain.speech.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceWakeModelsTest {
    @Test
    fun wakeModelsUseFixedGimiWakeWords() {
        assertEquals("吉米", WakeModelCatalog.Chinese.wakeWord)
        assertEquals("吉米", WakeModelCatalog.Chinese.wakeWordGrammar)
        assertEquals("Gimi", WakeModelCatalog.English.wakeWord)
        assertEquals("jimmy", WakeModelCatalog.English.wakeWordGrammar)
    }

    @Test
    fun voiceStateDerivesWakeWordFromActiveModel() {
        val state = VoiceWakeState(
            availableModels = WakeModelCatalog.models,
            activeModelId = WakeModelCatalog.English.id,
        )

        assertEquals("Gimi", state.wakeWord)
    }
}
