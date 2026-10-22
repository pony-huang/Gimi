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
            attachment.cloudReference ?: Triple(attachment.displayName, attachment.mimeType, attachment.sizeBytes)
        }
        .take(maxAttachments)
    return AttachmentSelectionResult.Accepted(
        attachments = merged,
        replaced = if (sameCategory) emptyList() else current,
    )
}

/** 云文件确认只追加或去重，不能静默替换已有草稿或截断本次选择。 */
sealed interface CloudDraftAddition {
    /** 保留原草稿顺序，追加新的云文件引用。 */
    data class Accepted(val attachments: List<DraftAttachment>) : CloudDraftAddition
    /** 类型不同需分开发送，原草稿不变。 */
    data object MixedCategories : CloudDraftAddition
    /** 超过容量时整次拒绝，避免只添加用户所选的一部分。 */
    data object LimitExceeded : CloudDraftAddition
}

internal fun addCloudFilesToDraft(current: List<DraftAttachment>, selected: List<DraftAttachment>): CloudDraftAddition {
    val merged = (current + selected).distinctBy { it.cloudReference ?: it.reference }
    if (merged.map { it.category }.distinct().size > 1) return CloudDraftAddition.MixedCategories
    if (merged.size > 3) return CloudDraftAddition.LimitExceeded
    return CloudDraftAddition.Accepted(merged)
}
