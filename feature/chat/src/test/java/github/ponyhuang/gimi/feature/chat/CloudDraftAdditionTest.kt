package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

/** 确认云文件不能替换草稿或静默只添加一部分；身份以连接和文件 ID 为准。 */
class CloudDraftAdditionTest {
    @Test fun differentIdsWithTheSameNameRemainDistinctAndReselectingIsDeduplicated() {
        val first = draft("first")
        val next = draft("next")
        assertEquals(CloudDraftAddition.Accepted(listOf(first, next)), addCloudFilesToDraft(listOf(first), listOf(first, next)))
    }

    @Test fun exceedingTheLimitOrMixingTypesRejectsTheWholeAddition() {
        val current = listOf(draft("one"), draft("two"))
        assertEquals(CloudDraftAddition.LimitExceeded, addCloudFilesToDraft(current, listOf(draft("three"), draft("four"))))
        assertEquals(CloudDraftAddition.MixedCategories,
            addCloudFilesToDraft(current, listOf(draft("image").copy(category = AttachmentCategory.IMAGE))))
    }

    private fun draft(id: String) = DraftAttachment("", "same.pdf", "application/pdf", 10,
        AttachmentCategory.DOCUMENT, CloudAttachmentReference(id, "scope"))
}
