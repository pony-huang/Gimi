package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStorageTest {
    @Test
    fun composerDraftsAndShareableFilesUseSeparateManagedCacheRoots() {
        assertEquals(StorageArea.CACHE, chatComposerDraftDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, chatComposerDraftDirectorySpec.lifecycle)
        assertEquals(SharingPolicy.PRIVATE, chatComposerDraftDirectorySpec.sharingPolicy)
        assertEquals(StorageArea.CACHE, chatShareableDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, chatShareableDirectorySpec.lifecycle)
        assertEquals(SharingPolicy.FILE_PROVIDER, chatShareableDirectorySpec.sharingPolicy)
    }
}
