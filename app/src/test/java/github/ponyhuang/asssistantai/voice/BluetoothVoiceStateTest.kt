package github.ponyhuang.asssistantai.voice

import org.junit.Assert.assertEquals
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
}
