package github.ponyhuang.gimi.domain.speech.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWakeModelsTest {
    @Test
    fun phraseIsNormalizedForPersistenceAndMatching() {
        assertEquals("Hey Gimi", normalizeWakePhrase("  Hey   Gimi  "))
    }

    @Test
    fun phraseAcceptsChineseAndEnglishWithinSharedLengthLimit() {
        assertNull(validateWakePhrase("吉米"))
        assertNull(validateWakePhrase("Hey Gimi"))
        assertEquals(WakePhraseError.InvalidLength, validateWakePhrase("吉"))
        assertEquals(
            WakePhraseError.InvalidCharacters,
            validateWakePhrase("吉米\u0001"),
        )
    }
}
