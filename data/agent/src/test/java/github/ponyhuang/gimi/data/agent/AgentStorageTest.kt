package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentStorageTest {
    @Test
    fun artifactsPreferExternalPersistentNamespace() {
        assertEquals(StorageArea.EXTERNAL_FILES, AgentStorage.Artifacts.area)
        assertEquals(StorageLifecycle.PERSISTENT, AgentStorage.Artifacts.lifecycle)
        assertEquals("agent/artifacts", AgentStorage.Artifacts.relativePath)
    }
}
