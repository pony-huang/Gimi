package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultVoiceConfigLookupTest {
    @Test
    fun realtimeCatalogConfirmsBuiltInVoicesAndLeavesCustomIdsForProviderValidation() {
        val selection = ModelSelection("instance", "realtime", "gpt-realtime")
        val connection = mockk<ResolvedServiceConnection> {
            every { templateId } returns "openai-speech"
        }
        val catalog = mockk<ModelCatalogRepository> {
            every { resolveConnection(selection) } returns connection
        }
        val realtimeSupport = mockk<RealtimeVoiceSupport> {
            every { voicesFor(selection.modelId) } returns listOf(TtsVoice("cedar", "Cedar", null, null))
        }
        val capabilities = mockk<VoiceCapabilityResolver> {
            every { realtimeSupport("openai-speech") } returns realtimeSupport
        }
        val lookup = DefaultVoiceConfigLookup(catalog, capabilities, mockk(), FakeVoiceCatalog(emptyMap()))

        assertEquals(true, lookup.isVoiceValid(VoicePipeline.NATIVE_REALTIME, selection, "cedar"))
        assertNull(lookup.isVoiceValid(VoicePipeline.NATIVE_REALTIME, selection, "00123"))
    }

    @Test
    fun cascadedVoiceOwnedByAnotherServiceIsInvalid() {
        val selection = ModelSelection("instance", "tts", "deepseek-tts")
        val lookup = lookupWith(
            selection,
            template = "deepseek",
            owners = mapOf("baianran_v3.1" to "qwen", "stella" to "deepseek"),
        )

        // 千问音色发往 DeepSeek：确定跨厂商残留，判失效触发替代回落。
        assertEquals(false, lookup.isVoiceValid(VoicePipeline.CASCADED, selection, "baianran_v3.1"))
        assertEquals(true, lookup.isVoiceValid(VoicePipeline.CASCADED, selection, "stella"))
    }

    @Test
    fun cascadedOpenSetVoiceIdsAndMissingConnectionStayUnverifiable() {
        val selection = ModelSelection("instance", "tts", "deepseek-tts")
        val lookup = lookupWith(
            selection,
            template = "deepseek",
            owners = mapOf("baianran_v3.1" to "qwen"),
        )

        // 复刻音色等开放集 ID 无法核验，维持厂商端校验。
        assertNull(lookup.isVoiceValid(VoicePipeline.CASCADED, selection, "cloned-voice"))

        val noConnection = lookupWith(selection, template = null, owners = mapOf("baianran_v3.1" to "qwen"))
        assertNull(noConnection.isVoiceValid(VoicePipeline.SEMI_CASCADED, selection, "baianran_v3.1"))
    }

    private fun lookupWith(
        selection: ModelSelection,
        template: String?,
        owners: Map<String, String>,
    ): DefaultVoiceConfigLookup {
        val connection = template?.let { id ->
            mockk<ResolvedServiceConnection> { every { templateId } returns id }
        }
        val catalog = mockk<ModelCatalogRepository> {
            every { resolveConnection(selection) } returns connection
        }
        return DefaultVoiceConfigLookup(catalog, mockk(), mockk(), FakeVoiceCatalog(owners))
    }

    /** 固定归属表的假音色目录：只服务归属判定断言。 */
    private class FakeVoiceCatalog(private val owners: Map<String, String>) : VoiceCatalogResolver {
        override fun builtInVoices(serviceId: String?): List<TtsVoice> = emptyList()
        override fun builtInDefaultVoice(serviceId: String?, modelId: String?): TtsVoice? = null
        override fun builtInVoiceOwner(voiceId: String): String? = owners[voiceId]
    }
}
