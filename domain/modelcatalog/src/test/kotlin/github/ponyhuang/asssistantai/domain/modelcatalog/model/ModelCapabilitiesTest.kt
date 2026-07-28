package github.ponyhuang.asssistantai.domain.modelcatalog.model

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilitiesTest {
    @Test
    fun defaultCapabilitiesSupportAllAttachmentCategories() {
        val capabilities = MultimodalCapabilities()

        assertTrue(capabilities.supportsImages)
        assertTrue(capabilities.supportsAudio)
        assertTrue(capabilities.supportsDocuments)
    }
}
