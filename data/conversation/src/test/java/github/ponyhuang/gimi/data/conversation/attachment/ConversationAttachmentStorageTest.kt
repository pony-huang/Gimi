package github.ponyhuang.gimi.data.conversation.attachment

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.core.storage.workspaceDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationAttachmentDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationDraftDirectorySpec
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAttachmentStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun documentDraft(
        folder: File,
        name: String,
        bytes: ByteArray,
        displayName: String = "report.pdf",
        extension: String? = "pdf",
    ): DraftAttachment {
        val draft = File(folder, name).apply { writeBytes(bytes) }
        return DraftAttachment(
            reference = draft.absolutePath,
            displayName = displayName,
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            category = AttachmentCategory.DOCUMENT,
        )
    }

    @Test
    fun workspaceIsPersistentUserManagedStorageWhileDraftsAreTemporary() {
        assertEquals(StorageArea.FILES, workspaceDirectorySpec.area)
        assertEquals(StorageLifecycle.PERSISTENT, workspaceDirectorySpec.lifecycle)
        assertEquals(BackupPolicy.EXCLUDED, workspaceDirectorySpec.backupPolicy)
        assertEquals(StorageArea.CACHE, conversationDraftDirectorySpec.area)
        assertEquals(StorageLifecycle.TEMPORARY, conversationDraftDirectorySpec.lifecycle)
        // 遗留的按会话目录保留旧声明，仅供旧数据随会话删除自然收缩。
        assertEquals(StorageArea.FILES, conversationAttachmentDirectorySpec.area)
        assertEquals(StorageLifecycle.PERSISTENT, conversationAttachmentDirectorySpec.lifecycle)
    }

    @Test
    fun sessionDirectoryCannotEscapeAttachmentRoot() {
        val root = temporaryFolder.newFolder("attachments")

        val directory = conversationSessionDirectory(root, "../../outside")

        assertEquals(root.canonicalFile, directory.parentFile)
    }

    @Test
    fun archiveDraftAttachmentMovesDraftIntoFlatWorkspaceFile() {
        val workspace = temporaryFolder.newFolder("workspace")
        val drafts = temporaryFolder.newFolder("drafts")
        val legacy = temporaryFolder.newFolder("attachments")
        val bytes = "attachment-bytes".toByteArray()

        val archived = archiveDraftAttachment(
            workspaceRoot = workspace,
            legacyArchiveRoot = legacy,
            attachment = documentDraft(
                folder = drafts,
                name = "selection-1",
                bytes = bytes,
                displayName = "报告 最终版.pdf",
            ),
            extension = "pdf",
        )

        val file = File(requireNotNull(archived.payloadReference))
        assertEquals(workspace.canonicalFile, file.parentFile)
        assertTrue(file.name.startsWith("报告 最终版-"))
        assertTrue(file.name.endsWith(".pdf"))
        assertEquals(12, file.nameWithoutExtension.substringAfterLast('-').length)
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(File(drafts, "selection-1").exists())
        assertEquals(file.nameWithoutExtension, archived.id)
        assertEquals(bytes.size.toLong(), archived.sizeBytes)
        assertEquals("application/pdf", archived.mimeType)
        assertEquals(AttachmentCategory.DOCUMENT, archived.category)
    }

    @Test
    fun archiveDraftAttachmentReusesFileAlreadyInWorkspace() {
        val workspace = temporaryFolder.newFolder("workspace")
        val legacy = temporaryFolder.newFolder("attachments")
        val bytes = "archived-payload".toByteArray()
        val archivedFile = File(workspace, "notes-abc123def456.pdf").apply { writeBytes(bytes) }

        val result = archiveDraftAttachment(
            workspaceRoot = workspace,
            legacyArchiveRoot = legacy,
            attachment = DraftAttachment(
                reference = archivedFile.absolutePath,
                displayName = "notes-abc123def456.pdf",
                mimeType = "application/pdf",
                sizeBytes = bytes.size.toLong(),
                category = AttachmentCategory.DOCUMENT,
            ),
            extension = "pdf",
        )

        assertEquals(archivedFile.absolutePath, result.payloadReference)
        assertEquals("notes-abc123def456", result.id)
        assertEquals(listOf("notes-abc123def456.pdf"), workspace.listFiles()?.map { it.name })
    }

    @Test
    fun archiveDraftAttachmentReusesLegacySessionArchiveWithoutRenaming() {
        val workspace = temporaryFolder.newFolder("workspace")
        val legacy = temporaryFolder.newFolder("attachments")
        val sessionDir = File(legacy, "session-1").apply { mkdirs() }
        val bytes = "legacy-archived".toByteArray()
        val archivedFile = File(sessionDir, "abc123.pdf").apply { writeBytes(bytes) }

        val result = archiveDraftAttachment(
            workspaceRoot = workspace,
            legacyArchiveRoot = legacy,
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
        assertTrue(workspace.listFiles().isNullOrEmpty())
    }

    @Test
    fun archiveDraftAttachmentThrowsWhenDraftFileIsMissing() {
        val workspace = temporaryFolder.newFolder("workspace")
        val legacy = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")

        try {
            archiveDraftAttachment(
                workspaceRoot = workspace,
                legacyArchiveRoot = legacy,
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
        val workspace = temporaryFolder.newFolder("workspace")
        val legacy = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")

        try {
            archiveDraftAttachment(
                workspaceRoot = workspace,
                legacyArchiveRoot = legacy,
                attachment = documentDraft(
                    folder = drafts,
                    name = "selection-2",
                    bytes = "abc".toByteArray(),
                ).copy(sizeBytes = 99L),
                extension = "pdf",
            )
            fail("Expected a size mismatch to fail archiving")
        } catch (expected: IllegalStateException) {
        }
    }

    @Test
    fun archiveDraftAttachmentAppendsExtensionOnlyWhenResolved() {
        val workspace = temporaryFolder.newFolder("workspace")
        val legacy = temporaryFolder.newFolder("attachments")
        val drafts = temporaryFolder.newFolder("drafts")

        val withExtension = archiveDraftAttachment(
            workspaceRoot = workspace,
            legacyArchiveRoot = legacy,
            attachment = documentDraft(
                folder = drafts,
                name = "selection-3",
                bytes = "a".toByteArray(),
                displayName = "notes",
                extension = "txt",
            ),
            extension = "txt",
        )
        val withoutExtension = archiveDraftAttachment(
            workspaceRoot = workspace,
            legacyArchiveRoot = legacy,
            attachment = documentDraft(
                folder = drafts,
                name = "selection-4",
                bytes = "b".toByteArray(),
                displayName = "notes",
                extension = null,
            ),
            extension = null,
        )

        assertTrue(requireNotNull(withExtension.payloadReference).endsWith(".txt"))
        assertFalse(requireNotNull(withoutExtension.payloadReference).contains('.'))
    }

    @Test
    fun sanitizesDisplayNamesForArchiveFileNames() {
        assertEquals("file", archiveBaseName("   "))
        assertEquals("a_b", archiveBaseName("a/b"))
        assertEquals("report", archiveBaseName("report.pdf"))
        assertEquals(60, archiveBaseName("很长的名字".repeat(20)).length)
        assertEquals("会议纪要_2026-09-13", archiveBaseName("会议纪要/2026-09-13"))
    }

    @Test
    fun writeWorkspaceImageReusesIdenticalContentAndName() {
        val workspace = temporaryFolder.newFolder("workspace")
        val bytes = "image-bytes".toByteArray()

        val first = writeWorkspaceImage(workspace, "photo", "aaaa", "jpg", bytes)
        val second = writeWorkspaceImage(workspace, "photo", "aaaa", "jpg", bytes)

        assertEquals(first.absolutePath, second.absolutePath)
        assertArrayEquals(bytes, first.readBytes())
        assertTrue(workspace.listFiles()?.none { it.name.endsWith(".tmp") } == true)
    }

    @Test
    fun resolveWorkspaceArchiveTargetRegeneratesSuffixOnCollision() {
        val workspace = temporaryFolder.newFolder("workspace")
        val existing = File(workspace, "notes-aaaaaaaaaaaa.pdf").apply { writeBytes("x".toByteArray()) }

        val target = resolveWorkspaceArchiveTarget(
            workspaceRoot = workspace,
            baseName = "notes",
            extension = "pdf",
            preferredSuffix = "aaaaaaaaaaaa",
        )

        assertFalse(target.exists())
        assertEquals(existing.name, workspace.listFiles()?.single()?.name)
        assertTrue(target.name.startsWith("notes-"))
        assertTrue(target.name.endsWith(".pdf"))
        assertNotEquals("aaaaaaaaaaaa", target.nameWithoutExtension.substringAfterLast('-'))
    }
}
