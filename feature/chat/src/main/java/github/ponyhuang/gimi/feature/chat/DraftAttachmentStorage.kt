package github.ponyhuang.gimi.feature.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import java.util.UUID

internal fun importDraftAttachment(context: Context, uri: Uri): DraftAttachment {
    val resolver = context.contentResolver
    val displayName = if (uri.scheme == "file") {
        File(uri.path ?: "").name.takeIf(String::isNotBlank) ?: "attachment"
    } else {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        }?.takeIf(String::isNotBlank) ?: "attachment"
    }
    val mimeType = resolver.getType(uri)
        ?: AttachmentCategory.inferMimeType(displayName)
        ?: throw IllegalArgumentException("Unsupported attachment type")
    val category = AttachmentCategory.from(mimeType, displayName)
        ?: throw IllegalArgumentException("Unsupported attachment type")
    val directory = AndroidAppDirectoryResolver(context).resolve(
        chatComposerDraftDirectorySpec,
        create = true,
    )
    val target = File(directory, UUID.randomUUID().toString())
    try {
        val input = if (uri.scheme == "file") {
            File(uri.path ?: throw IllegalArgumentException("Invalid file URI")).inputStream()
        } else {
            resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot read selected attachment")
        }
        input.use { source ->
            target.outputStream().use(source::copyTo)
        }
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

internal fun deleteManagedDrafts(context: Context, attachments: Iterable<DraftAttachment>) {
    val root = AndroidAppDirectoryResolver(context).resolve(chatComposerDraftDirectorySpec).canonicalFile
    attachments.forEach { attachment ->
        val file = runCatching { File(attachment.reference).canonicalFile }.getOrNull()
            ?: return@forEach
        if (file.parentFile == root) file.delete()
    }
}
