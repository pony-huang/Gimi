package github.ponyhuang.asssistantai.voice

import github.ponyhuang.asssistantai.domain.speech.model.isPresetWakeKeyword
import github.ponyhuang.asssistantai.domain.speech.model.wakeKeywordGrammar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothVoiceStateTest {
    @Test
    fun wakeTextNormalizationIgnoresSpacingPunctuationAndCase() {
        assertEquals("你好assistant", normalizeWakeText(" 你 好，Assistant！ "))
    }

    @Test
    fun configuredWakeKeywordIsRemovedFromTranscriptPrefix() {
        assertEquals("打开地图", stripWakeKeyword("你 好 助 手，打开地图", "你好助手"))
    }

    @Test
    fun transcriptWithoutWakePrefixIsPreserved() {
        assertEquals("播放音乐", stripWakeKeyword("播放音乐", "你好助手"))
    }

    @Test
    fun presetKeywordResolvesToSpaceSegmentedGrammar() {
        assertEquals("你好 助手", wakeKeywordGrammar("你好助手"))
        assertEquals("小助手", wakeKeywordGrammar("小助手"))
    }

    @Test
    fun nonPresetKeywordFallsBackToPerCharacterGrammar() {
        assertEquals("天 天", wakeKeywordGrammar("天天"))
    }

    @Test
    fun latinKeywordKeepsWhitespaceTokenization() {
        assertEquals("hello assistant", wakeKeywordGrammar("hello assistant"))
    }

    @Test
    fun presetKeywordValidation() {
        assertTrue(isPresetWakeKeyword("你好助手"))
        assertTrue(isPresetWakeKeyword("语音助手"))
        assertFalse(isPresetWakeKeyword("天天"))
        assertFalse(isPresetWakeKeyword(""))
    }
}
