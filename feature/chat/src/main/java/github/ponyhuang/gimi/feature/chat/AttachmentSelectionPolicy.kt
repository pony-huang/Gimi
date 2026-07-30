package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment

sealed interface AttachmentSelectionResult {
    val attachments: List<DraftAttachment>

    data class Accepted(
        override val attachments: List<DraftAttachment>,
        val replaced: List<DraftAttachment>,
    ) : AttachmentSelectionResult

    data class MixedTypes(
        override val attachments: List<DraftAttachment>,
    ) : AttachmentSelectionResult
}

fun mergeAttachmentSelection(
    current: List<DraftAttachment>,
    selected: List<DraftAttachment>,
    maxAttachments: Int = 3,
): AttachmentSelectionResult {
    if (selected.isEmpty()) {
        return AttachmentSelectionResult.Accepted(current, replaced = emptyList())
    }
    val selectedCategories = selected.mapTo(linkedSetOf()) { it.category }
    if (selectedCategories.size != 1) {
        return AttachmentSelectionResult.MixedTypes(current)
    }
    val category = selectedCategories.single()
    val sameCategory = current.all { it.category == category }
    val base = if (sameCategory) current else emptyList()
    val merged = (base + selected)
        .distinctBy { attachment ->
            Triple(attachment.displayName, attachment.mimeType, attachment.sizeBytes)
        }
        .take(maxAttachments)
    return AttachmentSelectionResult.Accepted(
        attachments = merged,
        replaced = if (sameCategory) emptyList() else current,
    )
}
