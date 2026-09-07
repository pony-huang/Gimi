package github.ponyhuang.gimi.data.appupdate

import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateStorageTest {
    @Test
    fun packagesUseNamespacedShareableCacheDirectory() {
        val spec = AppUpdateStorage.Packages

        assertEquals(StorageArea.CACHE, spec.area)
        assertEquals(StorageLifecycle.TEMPORARY, spec.lifecycle)
        assertEquals(SharingPolicy.FILE_PROVIDER, spec.sharingPolicy)
        assertEquals("shareable/appupdate/packages", spec.relativePath)
    }
}
