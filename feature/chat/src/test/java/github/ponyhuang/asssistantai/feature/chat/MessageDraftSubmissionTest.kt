package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.AttachmentCategory
import github.ponyhuang.asssistantai.domain.conversation.model.DraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDraftSubmissionTest {

    @Test
    fun acceptedSendImmediatelyConsumesTextAndAttachments() {
        val draft = MessageData(
            text = "请分析附件",
            attachments = listOf(
                DraftAttachment(
                    reference = "draft/audio.mp3",
                    displayName = "audio.mp3",
                    mimeType = "audio/mpeg",
                    sizeBytes = 128,
                    category = AttachmentCategory.AUDIO,
                ),
            ),
        )
        var submitted: MessageData? = null

        val nextDraft = consumeDraftForSend(draft) {
            submitted = it
            true
        }

        assertEquals(draft, submitted)
        assertEquals(MessageData(), nextDraft)
    }

    @Test
    fun rejectedSendKeepsCurrentDraft() {
        val draft = MessageData(text = "尚未发送")

        val nextDraft = consumeDraftForSend(draft) { false }

        assertEquals(draft, nextDraft)
    }
}
