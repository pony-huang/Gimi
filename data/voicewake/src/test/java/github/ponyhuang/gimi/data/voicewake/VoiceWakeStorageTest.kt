package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceWakeStorageTest {
    @Test
    fun modelsUsePersistentCapabilityNamespace() {
        assertEquals(StorageArea.FILES, VoiceWakeStorage.Models.area)
        assertEquals(StorageLifecycle.PERSISTENT, VoiceWakeStorage.Models.lifecycle)
        assertEquals("voicewake/models", VoiceWakeStorage.Models.relativePath)
    }

    @Test
    fun downloadsUseTemporaryCapabilityNamespace() {
        assertEquals(StorageArea.CACHE, VoiceWakeStorage.Downloads.area)
        assertEquals(StorageLifecycle.TEMPORARY, VoiceWakeStorage.Downloads.lifecycle)
        assertEquals("voicewake/downloads", VoiceWakeStorage.Downloads.relativePath)
    }
}
