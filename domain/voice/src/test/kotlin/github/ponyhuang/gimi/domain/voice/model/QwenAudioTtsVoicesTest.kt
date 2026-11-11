package github.ponyhuang.gimi.domain.voice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 千问音频 TTS 音色与模型的绑定关系。
 *
 * 音色按模型精确匹配：3.1 与 3.0、flash 与 plus 的音色集合互不相同，
 * 跨模型使用会被服务端拒绝（`InvalidParameter`），因此这里守住映射规则本身。
 */
class QwenAudioTtsVoicesTest {

    @Test
    fun eachModelResolvesItsOwnVoiceTable() {
        assertEquals(
            QwenAudioTtsVoices.flash31,
            QwenAudioTtsVoices.forModel(QwenAudioTtsVoices.MODEL_ID_31_FLASH),
        )
        assertEquals(
            QwenAudioTtsVoices.plus31,
            QwenAudioTtsVoices.forModel(QwenAudioTtsVoices.MODEL_ID_31_PLUS),
        )
        assertEquals(
            QwenAudioTtsVoices.flash30,
            QwenAudioTtsVoices.forModel(QwenAudioTtsVoices.MODEL_ID_30_FLASH),
        )
        assertEquals(
            QwenAudioTtsVoices.plus30,
            QwenAudioTtsVoices.forModel(QwenAudioTtsVoices.MODEL_ID_30_PLUS),
        )
    }

    @Test
    fun plusModelOnlyOffersTheVoicesSharedWithItsFlashCounterpart() {
        // 按 ID 比较而不是整个对象：plus 会把同一条音色的分类改写成旗舰，
        // 元数据因此与其 flash 版本不同，但可用音色集合必须完全一致。
        val plus = QwenAudioTtsVoices.plus31.map { it.id }.toSet()
        val flash = QwenAudioTtsVoices.flash31.map { it.id }.toSet()

        assertTrue(plus.isNotEmpty())
        assertTrue(plus.all { it in flash })
        assertTrue(plus.size < flash.size)
    }

    @Test
    fun everyVoiceIsGroupedIntoACategory() {
        // 选择器按分类分组；漏标的音色会落进没有标题的分组里，这里守住数据完整性。
        val tables = listOf(
            QwenAudioTtsVoices.flash31,
            QwenAudioTtsVoices.plus31,
            QwenAudioTtsVoices.flash30,
            QwenAudioTtsVoices.plus30,
        )

        tables.flatMap { it }.forEach { voice ->
            assertTrue(
                "${voice.id} 缺少分类",
                voice.category != VoiceCategory.Unspecified,
            )
        }
    }

    @Test
    fun voiceIdsAreUniquePerTableAndNotSharedAcrossGenerations() {
        val tables = listOf(
            QwenAudioTtsVoices.flash31,
            QwenAudioTtsVoices.plus31,
            QwenAudioTtsVoices.flash30,
            QwenAudioTtsVoices.plus30,
        )

        tables.forEach { table ->
            assertEquals(table.size, table.map { it.id }.distinct().size)
        }
        // 3.0 与 3.1 的音色 ID 互不相同：跨代际复用同样会被服务端拒绝。
        assertTrue(
            QwenAudioTtsVoices.flash31.map { it.id }.toSet()
                .intersect(QwenAudioTtsVoices.flash30.map { it.id }.toSet())
                .isEmpty(),
        )
    }

    @Test
    fun unknownModelHasNoVoices() {
        assertTrue(QwenAudioTtsVoices.forModel("qwen-audio-9.9-tts-flash").isEmpty())
    }
}
