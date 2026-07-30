package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSelectionPolicyTest {
    @Test
    fun `same category appends and deduplicates up to three attachments`() {
        val current = listOf(draft("a", AttachmentCategory.IMAGE))
        val selected = listOf(
            draft("b", AttachmentCategory.IMAGE),
            draft("a", AttachmentCategory.IMAGE),
            draft("c", AttachmentCategory.IMAGE),
            draft("d", AttachmentCategory.IMAGE),
        )

        val result = mergeAttachmentSelection(current, selected)

        assertEquals(listOf("a", "b", "c"), result.attachments.map { it.reference })
        assertTrue(result is AttachmentSelectionResult.Accepted)
    }

    @Test
    fun `different category replaces all previous attachments`() {
        val current = listOf(
            draft("photo-1", AttachmentCategory.IMAGE),
            draft("photo-2", AttachmentCategory.IMAGE),
        )
        val selected = listOf(draft("paper", AttachmentCategory.DOCUMENT))

        val result = mergeAttachmentSelection(current, selected)

        assertEquals(listOf("paper"), result.attachments.map { it.reference })
        assertEquals(current, (result as AttachmentSelectionResult.Accepted).replaced)
    }

    @Test
    fun `mixed file selection is rejected without changing existing attachments`() {
        val current = listOf(draft("photo", AttachmentCategory.IMAGE))
        val selected = listOf(
            draft("voice", AttachmentCategory.AUDIO),
            draft("paper", AttachmentCategory.DOCUMENT),
        )

        val result = mergeAttachmentSelection(current, selected)

        assertEquals(current, result.attachments)
        assertTrue(result is AttachmentSelectionResult.MixedTypes)
    }

    private fun draft(
        reference: String,
        category: AttachmentCategory,
    ) = DraftAttachment(
        reference = reference,
        displayName = reference,
        mimeType = when (category) {
            AttachmentCategory.IMAGE -> "image/jpeg"
            AttachmentCategory.AUDIO -> "audio/mpeg"
            AttachmentCategory.DOCUMENT -> "application/pdf"
        },
        sizeBytes = 1L,
        category = category,
    )
}
