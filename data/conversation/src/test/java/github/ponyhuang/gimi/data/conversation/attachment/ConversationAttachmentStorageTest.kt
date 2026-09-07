package github.ponyhuang.gimi.data.conversation.attachment

import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.data.conversation.conversationAttachmentDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationDraftDirectorySpec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAttachmentStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sentAttachmentsArePersistentWhileDraftsAreTemporary() {
        assertEquals(StorageArea.FILES, conversationAttachmentDirectorySpec.area)
        assertEquals(StorageLifecycle.PERSISTENT, conversationAttachmentDirectorySpec.lifecycle)
        assertEquals(StorageArea.CACHE, conversationDraftDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, conversationDraftDirectorySpec.lifecycle)
    }

    @Test
    fun sessionDirectoryCannotEscapeAttachmentRoot() {
        val root = temporaryFolder.newFolder("attachments")

        val directory = conversationSessionDirectory(root, "../../outside")

        assertEquals(root.canonicalFile, directory.parentFile)
    }
}
