package github.ponyhuang.asssistantai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSettingsContractTest {

    @Test
    fun settingsStateContainsOnlyRemainingSystemVoiceControls() {
        assertEquals(
            setOf("microphoneGranted", "tileAddRequested"),
            AssistantSettingsUiState::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("$") }
                .toSet(),
        )
    }
}
