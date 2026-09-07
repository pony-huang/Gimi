package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillsStorageTest {
    @Test
    fun installedSkillsAndImportsUseSeparateLifecycles() {
        assertEquals(StorageArea.FILES, SkillsStorage.Installed.area)
        assertEquals(StorageLifecycle.PERSISTENT, SkillsStorage.Installed.lifecycle)
        assertEquals("skills/installed", SkillsStorage.Installed.relativePath)
        assertEquals(StorageArea.CACHE, SkillsStorage.Imports.area)
        assertEquals(StorageLifecycle.TEMPORARY, SkillsStorage.Imports.lifecycle)
        assertEquals("skills/imports", SkillsStorage.Imports.relativePath)
    }
}
