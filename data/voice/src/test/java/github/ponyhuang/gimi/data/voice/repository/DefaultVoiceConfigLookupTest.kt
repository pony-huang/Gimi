package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
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
        val lookup = DefaultVoiceConfigLookup(catalog, capabilities, mockk())

        assertEquals(true, lookup.isVoiceValid(VoicePipeline.NATIVE_REALTIME, selection, "cedar"))
        assertNull(lookup.isVoiceValid(VoicePipeline.NATIVE_REALTIME, selection, "00123"))
    }
}
