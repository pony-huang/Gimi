package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 厂商锁定的消息推导判定：附件携带 file 引用即锁定，纯文本/普通附件不锁定。 */
class DetectConversationVendorLockUseCaseTest {

    private val detect = DetectConversationVendorLockUseCase()

    private fun userMessage(vararg attachments: FileAttachment) = Message(
        author = "user",
        role = MessageRole.User,
        fileAttachments = attachments.toList(),
    )

    private fun vendorAttachment(vendor: String = "deepseek") = FileAttachment.fromVendorReference(
        vendor = vendor,
        fileId = "file-1",
        mimeType = "application/pdf",
        displayName = "doc.pdf",
    )

    @Test
    fun emptyMessagesAreUnlocked() {
        assertNull(detect(emptyList()))
    }

    @Test
    fun textOnlyConversationIsUnlocked() {
        val messages = listOf(
            userMessage(),
            Message(author = "model", role = MessageRole.Assistant),
        )
        assertNull(detect(messages))
    }

    @Test
    fun localFileAttachmentDoesNotLock() {
        val messages = listOf(
            userMessage(
                FileAttachment.fromFile(
                    file = java.io.File("/tmp/a.pdf"),
                    mimeType = "application/pdf",
                    displayName = "a.pdf",
                ),
            ),
        )
        assertNull(detect(messages))
    }

    @Test
    fun vendorFileAttachmentLocksToItsVendor() {
        assertEquals("deepseek", detect(listOf(userMessage(vendorAttachment()))))
    }

    @Test
    fun firstVendorFileAttachmentWins() {
        val messages = listOf(
            userMessage(vendorAttachment("deepseek")),
            userMessage(vendorAttachment("kimi")),
        )
        assertEquals("deepseek", detect(messages))
    }
}
