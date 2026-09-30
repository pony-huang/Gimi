package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.core.storage.FileSystemAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.core.storage.StorageRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun composerDraftsAndShareableFilesUseSeparateManagedCacheRoots() {
        assertEquals(StorageArea.CACHE, chatComposerDraftDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, chatComposerDraftDirectorySpec.lifecycle)
        assertEquals(SharingPolicy.PRIVATE, chatComposerDraftDirectorySpec.sharingPolicy)
        assertEquals(StorageArea.CACHE, chatShareableDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, chatShareableDirectorySpec.lifecycle)
        assertEquals(SharingPolicy.FILE_PROVIDER, chatShareableDirectorySpec.sharingPolicy)
        assertNotEquals(chatComposerDraftDirectorySpec.id, chatShareableDirectorySpec.id)
        val cache = temporaryFolder.newFolder("cache")
        val resolver = FileSystemAppDirectoryResolver(
            StorageRoots(
                files = temporaryFolder.newFolder("files"),
                cache = cache,
                codeCache = temporaryFolder.newFolder("code-cache"),
                externalFiles = null,
            ),
        )
        val drafts = resolver.resolve(chatComposerDraftDirectorySpec).toPath()
        val shared = resolver.resolve(chatShareableDirectorySpec).toPath()
        val providerRoot = cache.resolve("shareable").canonicalFile.toPath()
        assertNotEquals(drafts, shared)
        assertFalse("草稿不得位于 FileProvider 暴露的目录内", drafts.startsWith(providerRoot))
        assertTrue("共享文件必须位于 FileProvider 暴露的目录内", shared.startsWith(providerRoot))
    }
}
