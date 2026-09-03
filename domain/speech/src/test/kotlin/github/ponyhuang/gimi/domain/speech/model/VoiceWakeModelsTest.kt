package github.ponyhuang.gimi.domain.speech.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWakeModelsTest {
    @Test
    fun wakeModelsExposeDefaultWakeWordsAndRecognitionGrammar() {
        assertEquals("你好 吉米", WakeModelCatalog.Chinese.defaultWakeWord)
        assertEquals("你好 吉米", WakeModelCatalog.Chinese.defaultWakeWordGrammar)
        assertEquals("hey Gimi", WakeModelCatalog.English.defaultWakeWord)
        assertEquals("hey jimmy", WakeModelCatalog.English.defaultWakeWordGrammar)
    }

    @Test
    fun voiceStateCarriesEffectiveWakeWord() {
        val state = VoiceWakeState(
            availableModels = WakeModelCatalog.models,
            activeModelId = WakeModelCatalog.English.id,
            wakeWord = "hey assistant",
        )

        assertEquals("hey assistant", state.wakeWord)
    }

    @Test
    fun chineseWakeWordAcceptsTwoToTwentyVisibleCharacters() {
        assertNull(validateWakeKeyword("小助手", WakeModelCatalog.Chinese))
        assertNull(validateWakeKeyword("  你好助手  ", WakeModelCatalog.Chinese))
        assertEquals(
            WakeKeywordError.InvalidLength,
            validateWakeKeyword("好", WakeModelCatalog.Chinese),
        )
        assertEquals(
            WakeKeywordError.InvalidCharacters,
            validateWakeKeyword("你好\u0001助手", WakeModelCatalog.Chinese),
        )
    }

    @Test
    fun englishWakeWordAcceptsOneToFourWordsAndNormalizesSpacing() {
        assertNull(validateWakeKeyword("Jarvis", WakeModelCatalog.English))
        assertNull(validateWakeKeyword("Hey   smart assistant", WakeModelCatalog.English))
        assertEquals("Hey smart assistant", normalizeWakeKeyword("  Hey   smart assistant  "))
        assertEquals(
            WakeKeywordError.InvalidWordFormat,
            validateWakeKeyword("hey-assistant", WakeModelCatalog.English),
        )
        assertEquals(
            WakeKeywordError.InvalidWordFormat,
            validateWakeKeyword("one two three four five", WakeModelCatalog.English),
        )
    }

    @Test
    fun wakeWordGrammarPreservesDefaultAliasAndNormalizesCustomWords() {
        assertEquals("hey jimmy", wakeWordGrammar("Hey Gimi", WakeModelCatalog.English))
        assertEquals("hey assistant", wakeWordGrammar("Hey   Assistant", WakeModelCatalog.English))
        assertEquals("小助手", wakeWordGrammar("小助手", WakeModelCatalog.Chinese))
    }

    @Test
    fun commandPrefixAcceptsDisplayWordOrRecognitionGrammar() {
        assertEquals("open maps", stripWakeKeywordVariants("Gimi, open maps", "Gimi", "jimmy"))
        assertEquals("open maps", stripWakeKeywordVariants("jimmy, open maps", "Gimi", "jimmy"))
    }
}
