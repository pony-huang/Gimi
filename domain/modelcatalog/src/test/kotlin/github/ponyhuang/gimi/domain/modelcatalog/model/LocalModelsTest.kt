package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.*
import org.junit.Test

class LocalModelsTest {
    @Test
    fun downloadedVersionsAreAutomaticallyAvailableWithoutApiKey() {
        val service = LocalModelCatalogState(false, listOf(ready("cpu"), ready("gpu"))).downloadedService()
        assertTrue(service.isConfiguredForChat)
        assertEquals("", service.apiKey)
        assertFalse(service.isOfficialToolsEnabled)
        assertEquals(listOf("cpu", "gpu"), service.groups.single().models.map { it.id })
        assertTrue(service.groups.single().models.none { it.capabilities.isMultimodal })
    }

    @Test
    fun incompleteAndFailedFilesCannotBeSelected() {
        val service = LocalModelCatalogState(false, listOf(
            ready("partial").copy(status = LocalModelDownloadStatus.Downloading),
            ready("bad").copy(status = LocalModelDownloadStatus.Failed),
        )).downloadedService()
        assertFalse(service.isConfiguredForChat)
        assertTrue(service.groups.single().models.isEmpty())
    }

    @Test
    fun remoteServicesStillRequireKeyAndEnabledState() {
        val local = LocalModelCatalogState(false, listOf(ready("cpu"))).downloadedService()
        assertFalse(local.copy(isLocal = false).isConfiguredForChat)
        assertTrue(local.copy(isLocal = false, apiKey = "key").isConfiguredForChat)
        assertFalse(local.copy(isEnabled = false).isConfiguredForChat)
    }

    private fun ready(id: String) = LocalModelState(
        LocalModelVariant(id, "gemma4", id, LocalModelBackend.CPU, 16),
        LocalModelDownloadStatus.Ready,
    )
}
