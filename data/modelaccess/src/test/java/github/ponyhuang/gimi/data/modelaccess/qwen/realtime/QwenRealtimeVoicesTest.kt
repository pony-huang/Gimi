package github.ponyhuang.gimi.data.modelaccess.qwen.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.QwenAudioTtsVoices
import github.ponyhuang.gimi.domain.voice.model.VoiceCategory

/**
 * 千问 Realtime 音色与模型的绑定关系。
 *
 * 实时音色既不是独立的一套表，也不是 TTS 表的直接照搬：3.1 复用 3.1 TTS 全量音色
 * 再加一个 realtime 专属默认音色，3.0 则是五个与 TTS 表不重合的固定音色。
 */
class QwenRealtimeVoicesTest {
    @Test
    fun v31ContainsTheRealtimeOnlyDefaultVoiceFollowedByTheTtsCatalog() {
        val expected = listOf(QwenRealtimeVoices.DEFAULT_VOICE_31) +
            QwenAudioTtsVoices.flash31.map { it.id }

        assertEquals(expected, QwenRealtimeVoices.v31.map { it.id })
    }

    @Test
    fun v31CoversEveryPlusVoiceBecausePlusIsASubsetOfFlash() {
        val v31Ids = QwenRealtimeVoices.v31.map { it.id }.toSet()

        assertTrue(QwenAudioTtsVoices.plus31.all { it.id in v31Ids })
    }

    @Test
    fun v30IsExactlyTheFiveVoicesPublishedForTheOlderGeneration() {
        // 官方 session.voice 说明给出的完整列表，多一个或少一个都会被服务端拒绝。
        val published = listOf(
            "longanqian",
            "longanlingxin",
            "longanlingxi",
            "longanxiaoxin",
            "longanlufeng",
        )

        assertEquals(published, QwenRealtimeVoices.v30.map { it.id })
    }

    @Test
    fun everyRealtimeVoiceIsCategorized() {
        // 漏标分类的音色会掉进选择器里没有标题的分组，等于在界面上消失。
        assertTrue(QwenRealtimeVoices.v31.all { it.category != VoiceCategory.Unspecified })
        assertTrue(QwenRealtimeVoices.v30.all { it.category != VoiceCategory.Unspecified })
    }

    @Test
    fun voiceCatalogIsResolvedPerModelGeneration() {
        assertEquals(
            QwenRealtimeVoices.v31,
            QwenRealtimeVoices.forModel("qwen-audio-3.1-realtime-plus"),
        )
        assertEquals(
            QwenRealtimeVoices.v30,
            QwenRealtimeVoices.forModel("qwen-audio-3.0-realtime-flash"),
        )
    }

    @Test
    fun unknownModelGetsNoCatalogButStillGetsADefaultVoice() {
        // 用户手填的模型 ID 不猜代际：猜错会把另一代音色摆出来，选中即被服务端拒绝。
        assertFalse(QwenRealtimeVoices.forModel("qwen-audio-9.9-realtime-plus").isNotEmpty())
        assertEquals(
            QwenRealtimeVoices.DEFAULT_VOICE_31,
            QwenRealtimeVoices.defaultFor("qwen-audio-9.9-realtime-plus"),
        )
    }

    @Test
    fun defaultVoiceFollowsTheGeneration() {
        assertEquals(
            QwenRealtimeVoices.DEFAULT_VOICE_31,
            QwenRealtimeVoices.defaultFor("qwen-audio-3.1-realtime-flash"),
        )
        assertEquals(
            QwenRealtimeVoices.DEFAULT_VOICE_30,
            QwenRealtimeVoices.defaultFor("qwen-audio-3.0-realtime-plus"),
        )
    }
}
