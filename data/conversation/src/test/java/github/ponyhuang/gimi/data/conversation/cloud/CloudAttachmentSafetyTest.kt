package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.conversation.attachment.AndroidChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.ConversationStorageIds
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 移除云引用不能删除共享来源，未接入的远端引用不能走本地归档旁路。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class CloudAttachmentSafetyTest {
    @get:Rule val directory = TemporaryFolder(File("../../temp/cloud-file-tests").apply { mkdirs() })

    @Test fun cloudPreparationArchivesACopyWithoutConsumingTheOriginalDraft() = runBlocking {
        val registry = mockk<StorageRegistry>()
        every { registry.resolve(any(), any()) } answers {
            File(directory.root, firstArg<String>().replace('/', '_')).apply { mkdirs() }
        }
        val source = File(registry.resolve(ConversationStorageIds.COMPOSER_DRAFTS), "report.pdf").apply { writeText("pdf") }
        val draft = DraftAttachment(source.absolutePath, "report.pdf", "application/pdf", source.length(), AttachmentCategory.DOCUMENT)
        val context = RuntimeEnvironment.getApplication()
        val archived = AndroidCloudFileLocalSource(context, registry).archive("session", draft)
        assertTrue(source.isFile)
        val archivedPath = requireNotNull(archived.payloadReference)
        assertTrue(File(archivedPath).isFile)
        assertTrue(archived.payloadReference != source.absolutePath)
        org.junit.Assert.assertEquals(source.readText(), File(archivedPath).readText())
    }

    @Test fun cloudImageArchivePreservesOriginalBytesAndMimeWithoutInlineCompression() = runBlocking {
        val registry = mockk<StorageRegistry>()
        every { registry.resolve(any(), any()) } answers {
            File(directory.root, firstArg<String>().replace('/', '_')).apply { mkdirs() }
        }
        val source = directory.newFile("original.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val draft = DraftAttachment(source.absolutePath, "original.png", "image/png", source.length(), AttachmentCategory.IMAGE)
        val archived = AndroidCloudFileLocalSource(RuntimeEnvironment.getApplication(), registry).archive("session", draft)
        org.junit.Assert.assertEquals("image/png", archived.mimeType)
        org.junit.Assert.assertEquals("original.png", archived.displayName)
        org.junit.Assert.assertArrayEquals(source.readBytes(), File(requireNotNull(archived.payloadReference)).readBytes())
        assertTrue(source.isFile)
    }

    @Test fun removingACloudDraftRetainsItsSourceAndInlineReadRejectsIt() = runBlocking {
        val registry = mockk<StorageRegistry>()
        every { registry.resolve(any(), any()) } answers {
            File(directory.root, firstArg<String>().replace('/', '_')).apply { mkdirs() }
        }
        val source = File(registry.resolve(ConversationStorageIds.COMPOSER_DRAFTS), "report.pdf").apply { writeText("pdf") }
        val reference = DraftAttachment(source.absolutePath, "report.pdf", "application/pdf", source.length(),
            AttachmentCategory.DOCUMENT, CloudAttachmentReference("file", "scope"))
        val repository = AndroidChatAttachmentRepository(RuntimeEnvironment.getApplication(), registry)
        repository.deleteDrafts(listOf(reference))
        assertTrue(source.isFile)
        try {
            repository.read("session", listOf(reference))
            throw AssertionError("Cloud reference must not use the inline attachment path")
        } catch (_: IllegalArgumentException) {
            assertTrue(source.isFile)
        }
    }
}
