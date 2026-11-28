package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [enrichVendorAttachments] 的绑定还原行为：厂商引用附件在历史还原时按注册表绑定
 * 补回本地归档副本，绑定悬空或查询失败时保持可下载形态（payloadReference 为 null）。
 */
class VendorAttachmentBindingRestoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun restoresLocalReferenceWhenBoundWorkspaceFileExists() = runTest {
        val bytes = "archived-pdf".toByteArray()
        val archived = temporaryFolder.newFile("简历-abc123def456.pdf").apply { writeBytes(bytes) }
        val message = messageWith(
            FileAttachment.fromVendorReference(
                vendor = "deepseek",
                fileId = "file-1",
                mimeType = "application/pdf",
                displayName = "简历.pdf",
            ),
        )

        val restored = enrichVendorAttachments(listOf(message)) { vendor, fileId ->
            assertEquals("deepseek", vendor)
            assertEquals("file-1", fileId)
            archived.absolutePath
        }.single().fileAttachments.single()

        assertEquals(archived.absolutePath, restored.payloadReference)
        assertEquals(bytes.size.toLong(), restored.sizeBytes)
        assertEquals("file-1", restored.fileId)
        assertNull(restored.inlineData)
        assertTrue(!restored.isMissing)
    }

    @Test
    fun keepsNullPayloadWhenBoundFileIsMissing() = runTest {
        val dangling = File(File(temporaryFolder.root, "gone"), "deleted.pdf").absolutePath
        val message = messageWith(vendorAttachment())

        val restored = enrichVendorAttachments(listOf(message)) { _, _ -> dangling }
            .single().fileAttachments.single()

        assertNull(restored.payloadReference)
        assertTrue(!restored.isMissing)
    }

    @Test
    fun keepsNullPayloadWhenNoBindingExists() = runTest {
        val message = messageWith(vendorAttachment())

        val restored = enrichVendorAttachments(listOf(message)) { _, _ -> null }
            .single().fileAttachments.single()

        assertNull(restored.payloadReference)
    }

    @Test
    fun keepsAttachmentsThatAlreadyCarryLocalPayload() = runTest {
        val payload = temporaryFolder.newFile("local.pdf").apply { writeBytes("x".toByteArray()) }
        val attachment = FileAttachment(
            mimeType = "application/pdf",
            id = "local-1",
            sizeBytes = 1L,
            displayName = "local.pdf",
            payloadReference = payload.absolutePath,
            fileId = "file-1",
            fileVendor = "deepseek",
        )
        var queried = false

        val restored = enrichVendorAttachments(listOf(messageWith(attachment))) { _, _ ->
            queried = true
            "/elsewhere.pdf"
        }.single().fileAttachments.single()

        assertEquals(payload.absolutePath, restored.payloadReference)
        assertTrue(!queried)
    }

    @Test
    fun leavesAttachmentsWithoutVendorReferenceUntouched() = runTest {
        val payload = temporaryFolder.newFile("plain.pdf").apply { writeBytes("x".toByteArray()) }
        val message = messageWith(
            FileAttachment.fromFile(file = payload, mimeType = "application/pdf", displayName = "plain.pdf"),
        )
        var queried = false

        val restored = enrichVendorAttachments(listOf(message)) { _, _ ->
            queried = true
            null
        }.single().fileAttachments.single()

        assertEquals(payload.absolutePath, restored.payloadReference)
        assertTrue(!queried)
    }

    @Test
    fun keepsMessageIntactWhenBindingLookupFails() = runTest {
        val message = messageWith(vendorAttachment())

        val restored = enrichVendorAttachments(listOf(message)) { _, _ ->
            throw IllegalStateException("registry unavailable")
        }.single().fileAttachments.single()

        assertNull(restored.payloadReference)
    }

    @Test
    fun bindingLookupCancellationPropagates() = runTest {
        val message = messageWith(vendorAttachment())

        try {
            enrichVendorAttachments(listOf(message)) { _, _ ->
                throw CancellationException("cancelled")
            }
            throw AssertionError("Expected cancellation to propagate")
        } catch (expected: CancellationException) {
        }
    }

    @Test
    fun skipsLookupWhenNoVendorAttachmentPending() = runTest {
        val payload = temporaryFolder.newFile("plain.pdf").apply { writeBytes("x".toByteArray()) }
        val message = messageWith(
            FileAttachment.fromFile(file = payload, mimeType = "application/pdf", displayName = "plain.pdf"),
        )

        val restored = enrichVendorAttachments(listOf(message)) { _, _ ->
            throw IllegalStateException("must not be queried")
        }

        assertEquals(message, restored.single())
    }

    private fun vendorAttachment() = FileAttachment.fromVendorReference(
        vendor = "deepseek",
        fileId = "file-1",
        mimeType = "application/pdf",
        displayName = "简历.pdf",
    )

    private fun messageWith(attachment: FileAttachment) = Message(
        id = "message-1",
        author = "user",
        role = MessageRole.User,
        fileAttachments = listOf(attachment),
    )
}
