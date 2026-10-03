package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.*
import org.junit.Test

class LocalModelsTest {
    @Test
    fun multipleEnabledVersionsAreAvailableWithoutApiKey() {
        val service = LocalModelCatalogState(false, listOf(ready("cpu"), ready("gpu"))).enabledService()
        assertTrue(service.isConfiguredForChat)
        assertEquals("", service.apiKey)
        assertFalse(service.isOfficialToolsEnabled)
        assertEquals(listOf("cpu", "gpu"), service.groups.single().models.map { it.id })
        assertTrue(service.groups.single().models.none { it.capabilities.isMultimodal })
    }

    @Test
    fun disabledIncompleteAndFailedFilesCannotBeSelected() {
        val available = ready("cpu")
        val service = LocalModelCatalogState(false, listOf(
            available.copy(enabled = false),
            ready("partial").copy(status = LocalModelDownloadStatus.Downloading),
            ready("bad").copy(status = LocalModelDownloadStatus.Failed),
        )).enabledService()
        assertFalse(service.isConfiguredForChat)
        assertTrue(service.groups.single().models.isEmpty())
    }

    @Test
    fun remoteServicesStillRequireKeyAndEnabledState() {
        val local = LocalModelCatalogState(false, listOf(ready("cpu"))).enabledService()
        assertFalse(local.copy(isLocal = false).isConfiguredForChat)
        assertTrue(local.copy(isLocal = false, apiKey = "key").isConfiguredForChat)
        assertFalse(local.copy(isEnabled = false).isConfiguredForChat)
    }

    private fun ready(id: String) = LocalModelState(
        LocalModelVariant(id, "gemma4", id, LocalModelBackend.CPU, 16),
        LocalModelDownloadStatus.Ready,
        enabled = true,
    )
}
