package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.saveable.SaverScope
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

/** 无本地来源的有效远端引用仍需随草稿恢复；丢失的普通本地草稿则不恢复。 */
class CloudDraftSaverTest {
    private val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }

    @Test fun remoteIdAndConnectionScopeSurviveWithoutALocalFile() {
        val cloud = DraftAttachment("", "report.pdf", "application/pdf", 10, AttachmentCategory.DOCUMENT,
            CloudAttachmentReference("file-id", "connection-scope"))
        val draft = MessageData("未发送", listOf(cloud))
        val saved = with(MessageData.Saver) { scope.save(draft) }!!
        assertEquals(draft, MessageData.Saver.restore(saved))
    }

    @Test fun missingLocalFilesAreStillDroppedOnRestore() {
        val local = DraftAttachment("/nonexistent-gimi-test-source", "report.pdf", "application/pdf", 10, AttachmentCategory.DOCUMENT)
        val saved = with(MessageData.Saver) { scope.save(MessageData("未发送", listOf(local))) }!!
        assertEquals(MessageData("未发送"), MessageData.Saver.restore(saved))
    }
}
