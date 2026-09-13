package github.ponyhuang.gimi.data.conversation.attachment

import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.data.conversation.conversationAttachmentDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationDraftDirectorySpec
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun archiveDraftAttachmentMovesDraftIntoSessionDirectory() {
        val root = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")
        val bytes = "attachment-bytes".toByteArray()
        val draft = File(drafts, "selection-1").apply { writeBytes(bytes) }

        val archived = archiveDraftAttachment(
            root = root,
            sessionId = "session-1",
            attachment = DraftAttachment(
                reference = draft.absolutePath,
                displayName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = bytes.size.toLong(),
                category = AttachmentCategory.DOCUMENT,
            ),
            extension = "pdf",
        )

        val file = File(requireNotNull(archived.payloadReference))
        assertEquals(root.canonicalFile, file.parentFile?.parentFile)
        assertTrue(file.name.endsWith(".pdf"))
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(draft.exists())
        assertEquals(file.nameWithoutExtension, archived.id)
        assertEquals(bytes.size.toLong(), archived.sizeBytes)
        assertEquals("application/pdf", archived.mimeType)
        assertEquals(AttachmentCategory.DOCUMENT, archived.category)
    }

    @Test
    fun archiveDraftAttachmentReusesArchivedDraftWithoutRenaming() {
        val root = temporaryFolder.newFolder("attachments")
        val sessionDir = File(root, "session-1").apply { mkdirs() }
        val bytes = "archived-payload".toByteArray()
        val archivedFile = File(sessionDir, "abc123.pdf").apply { writeBytes(bytes) }

        val result = archiveDraftAttachment(
            root = root,
            sessionId = "session-1",
            attachment = DraftAttachment(
                reference = archivedFile.absolutePath,
                displayName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = bytes.size.toLong(),
                category = AttachmentCategory.DOCUMENT,
            ),
            extension = "pdf",
        )

        assertEquals(archivedFile.absolutePath, result.payloadReference)
        assertEquals("abc123", result.id)
        assertEquals(listOf("abc123.pdf"), sessionDir.listFiles()?.map { it.name })
    }

    @Test
    fun archiveDraftAttachmentThrowsWhenDraftFileIsMissing() {
        val root = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")

        try {
            archiveDraftAttachment(
                root = root,
                sessionId = "session-1",
                attachment = DraftAttachment(
                    reference = File(drafts, "missing").absolutePath,
                    displayName = "report.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 4L,
                    category = AttachmentCategory.DOCUMENT,
                ),
                extension = "pdf",
            )
            fail("Expected a missing draft file to fail archiving")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun archiveDraftAttachmentThrowsWhenDraftSizeChanged() {
        val root = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")
        val draft = File(drafts, "selection-2").apply { writeBytes("abc".toByteArray()) }

        try {
            archiveDraftAttachment(
                root = root,
                sessionId = "session-1",
                attachment = DraftAttachment(
                    reference = draft.absolutePath,
                    displayName = "report.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 99L,
                    category = AttachmentCategory.DOCUMENT,
                ),
                extension = "pdf",
            )
            fail("Expected a size mismatch to fail archiving")
        } catch (expected: IllegalStateException) {
        }
    }

    @Test
    fun archiveDraftAttachmentAppendsExtensionOnlyWhenResolved() {
        val root = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")
        val first = File(drafts, "selection-3").apply { writeBytes("a".toByteArray()) }
        val second = File(drafts, "selection-4").apply { writeBytes("b".toByteArray()) }

        val withExtension = archiveDraftAttachment(
            root = root,
            sessionId = "session-1",
            attachment = DraftAttachment(
                reference = first.absolutePath,
                displayName = "notes",
                mimeType = "text/plain",
                sizeBytes = 1L,
                category = AttachmentCategory.DOCUMENT,
            ),
            extension = "txt",
        )
        val withoutExtension = archiveDraftAttachment(
            root = root,
            sessionId = "session-2",
            attachment = DraftAttachment(
                reference = second.absolutePath,
                displayName = "notes",
                mimeType = "text/plain",
                sizeBytes = 1L,
                category = AttachmentCategory.DOCUMENT,
            ),
            extension = null,
        )

        assertTrue(requireNotNull(withExtension.payloadReference).endsWith(".txt"))
        assertFalse(requireNotNull(withoutExtension.payloadReference).contains('.'))
    }
}
