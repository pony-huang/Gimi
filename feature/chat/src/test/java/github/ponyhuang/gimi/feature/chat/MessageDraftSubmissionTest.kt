package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDraftSubmissionTest {
    @Test
    fun pendingOrRejectedSubmissionKeepsTheDraft() {
        val draft = MessageData(text = "尚未发送", attachments = listOf(attachment("draft")))
        var current = draft
        val submission = ChatSubmission { result ->
            if (result == ChatSubmissionResult.ACCEPTED) current = consumeAcceptedDraft(current, draft)
        }
        assertEquals(draft, current)
        submission.complete(ChatSubmissionResult.REJECTED)
        assertEquals(draft, current)
    }

    @Test
    fun acceptedReceiptConsumesOnlyTheSubmittedSnapshotAndIsDeliveredOnce() {
        val draft = MessageData(text = "请求", attachments = listOf(attachment("shared-media")))
        var current = draft
        var deliveries = 0
        val submission = ChatSubmission {
            deliveries++
            if (it == ChatSubmissionResult.ACCEPTED) current = consumeAcceptedDraft(current, draft)
        }
        submission.complete(ChatSubmissionResult.ACCEPTED)
        assertEquals(MessageData(), current)
        current = MessageData(text = "新的输入")
        submission.complete(ChatSubmissionResult.REJECTED)
        assertEquals(1, deliveries)
        assertEquals("新的输入", current.text)
    }

    @Test
    fun lateReceiptDoesNotClearNewerText() {
        val current = MessageData(text = "后来编辑的内容")
        assertEquals(current, consumeAcceptedDraft(current, MessageData(text = "旧请求")))
    }

    @Test
    fun lateAcceptedReceiptKeepsTextAndAttachmentsEditedAfterSwitchingConversations() {
        val submitted = MessageData(text = "旧请求", attachments = listOf(attachment("submitted")))
        val added = attachment("added")
        val current = submitted.copy(text = "切换后继续编辑", attachments = submitted.attachments + added)

        assertEquals(
            MessageData(text = "切换后继续编辑", attachments = listOf(added)),
            consumeAcceptedDraft(current, submitted),
        )
    }

    private fun attachment(name: String) = DraftAttachment(
        reference = "/drafts/$name",
        displayName = "$name.png",
        mimeType = "image/png",
        sizeBytes = 1L,
        category = AttachmentCategory.IMAGE,
    )
}
