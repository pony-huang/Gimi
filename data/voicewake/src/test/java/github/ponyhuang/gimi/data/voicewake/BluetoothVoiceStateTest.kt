package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.speech.model.normalizeWakeText
import github.ponyhuang.gimi.domain.speech.model.stripWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothVoiceStateTest {
    @Test
    fun wakeTextNormalizationIgnoresSpacingPunctuationAndCase() {
        assertEquals("你好assistant", normalizeWakeText(" 你 好，Assistant！ "))
    }

    @Test
    fun fixedChineseWakeWordIsRemovedFromTranscriptPrefix() {
        assertEquals("打开地图", stripWakeKeyword("你好 吉米，打开地图", WakeModelCatalog.Chinese.defaultWakeWord))
    }

    @Test
    fun transcriptWithoutWakePrefixIsPreserved() {
        assertEquals("播放音乐", stripWakeKeyword("播放音乐", WakeModelCatalog.Chinese.defaultWakeWord))
    }
}
