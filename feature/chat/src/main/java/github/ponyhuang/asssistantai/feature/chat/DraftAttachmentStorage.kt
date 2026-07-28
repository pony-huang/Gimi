package github.ponyhuang.asssistantai.feature.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import github.ponyhuang.asssistantai.domain.conversation.model.AttachmentCategory
import github.ponyhuang.asssistantai.domain.conversation.model.DraftAttachment
import java.io.File
import java.util.UUID

internal fun importDraftAttachment(context: Context, uri: Uri): DraftAttachment {
    val resolver = context.contentResolver
    val displayName = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.getString(0)
    }?.takeIf(String::isNotBlank) ?: "attachment"
    val mimeType = resolver.getType(uri)
        ?: AttachmentCategory.inferMimeType(displayName)
        ?: throw IllegalArgumentException("Unsupported attachment type")
    val category = AttachmentCategory.from(mimeType, displayName)
        ?: throw IllegalArgumentException("Unsupported attachment type")
    val directory = File(context.cacheDir, DRAFT_DIRECTORY).apply { mkdirs() }
    val target = File(directory, UUID.randomUUID().toString())
    try {
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: throw IllegalArgumentException("Cannot read selected attachment")
        return DraftAttachment(
            reference = target.absolutePath,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = target.length(),
            category = category,
        )
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}

internal fun deleteManagedDrafts(attachments: Iterable<DraftAttachment>) {
    attachments.forEach { attachment ->
        val file = File(attachment.reference)
        if (file.parentFile?.name == DRAFT_DIRECTORY) file.delete()
    }
}

private const val DRAFT_DIRECTORY = "chat-drafts"
