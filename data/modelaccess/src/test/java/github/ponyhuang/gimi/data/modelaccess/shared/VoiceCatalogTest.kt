package github.ponyhuang.gimi.data.modelaccess.shared

import github.ponyhuang.gimi.data.modelaccess.qwen.speech.QwenAudioTtsVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音色归属判定：跨厂商残留检测依赖各厂商封闭命名空间的完整与互斥。 */
class VoiceCatalogTest {
    private val catalog = VoiceCatalog()

    @Test
    fun voiceOwnerSpansVendorNamespacesIncludingPerModelQwenTables() {
        assertEquals("deepseek", catalog.builtInVoiceOwner("stella"))
        // 千问音色按模型组织，归属判定必须取全部模型的并集。
        assertEquals("qwen", catalog.builtInVoiceOwner("baianran_v3.1"))
        assertEquals("qwen", catalog.builtInVoiceOwner("loongmary"))
    }

    @Test
    fun unknownVoiceIdsStayUnownedForOpenSetLeniency() {
        // 复刻音色等自定义 ID 不在任何封闭表内：返回 null 按无法核验处理。
        assertNull(catalog.builtInVoiceOwner("cloned-voice-42"))
        assertNull(catalog.builtInVoiceOwner(""))
    }

    @Test
    fun serviceLevelVoiceTableAndDefaultVoiceKeepPerModelSemanticsForQwen() {
        // 归属并集不得泄漏进服务级内置表：选择与默认音色仍按模型收窄。
        assertTrue(catalog.builtInVoices("qwen").isEmpty())
        assertEquals(
            "longanhuan_v3.1",
            catalog.builtInDefaultVoice("qwen", QwenAudioTtsVoices.MODEL_ID_31_FLASH)?.id,
        )
    }
}
