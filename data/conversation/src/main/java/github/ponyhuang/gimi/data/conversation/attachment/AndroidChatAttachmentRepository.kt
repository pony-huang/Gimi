package github.ponyhuang.gimi.data.conversation.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

class AndroidChatAttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatAttachmentRepository {
    private val resolver = context.contentResolver

    override suspend fun read(
        sessionId: String,
        attachments: List<DraftAttachment>,
    ): List<FileAttachment> =
        withContext(Dispatchers.IO) {
            attachments.map { attachment ->
                val prepared = when (attachment.category) {
                    AttachmentCategory.IMAGE -> prepareImage(attachment)
                    AttachmentCategory.AUDIO,
                    AttachmentCategory.DOCUMENT,
                    -> prepareOriginal(attachment)
                }
                persist(sessionId, prepared)
            }
        }

    override suspend fun deleteDrafts(attachments: List<DraftAttachment>) {
        withContext(Dispatchers.IO) {
            attachments.forEach { attachment ->
                val file = File(attachment.reference)
                val draftDirectory = File(context.cacheDir, DRAFT_DIRECTORY).canonicalFile
                val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
                if (candidate.parentFile == draftDirectory) candidate.delete()
            }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, ATTACHMENT_DIRECTORY).canonicalFile
            val directory = File(root, safeSessionId(sessionId)).canonicalFile
            if (directory.parentFile == root) directory.deleteRecursively()
        }
    }

    /**
     * 重试前校验上次发送的附件仍然可读。仅当附件只有内存内联数据（无文件引用）时跳过文件
     * 校验；引用文件已被删除或破坏则抛出异常，确保不会把失效资源再次发给模型。
     */
    override suspend fun validateSaved(attachments: List<FileAttachment>) {
        withContext(Dispatchers.IO) {
            attachments.forEach { attachment ->
                attachment.payloadReference?.let { reference ->
                    check(File(reference).isFile) {
                        "Attachment payload is unavailable: $reference"
                    }
                }
            }
        }
    }

    /**
     * 为编辑建立独立的草稿引用。已持久化的附件复用其文件；仅有内联数据的附件（如从 ADK
     * inlineData 恢复）会写一份临时草稿文件，方便编辑器展示。取消/删除草稿不会删除历史
     * 消息已归档的附件。
     */
    override suspend fun createDrafts(attachments: List<FileAttachment>): List<DraftAttachment> =
        withContext(Dispatchers.IO) {
            attachments.map { attachment ->
                val reference = attachment.payloadReference ?: run {
                    val directory = File(context.cacheDir, DRAFT_DIRECTORY).apply { mkdirs() }
                    val target = File(directory, attachment.id)
                    if (!target.exists()) target.writeBytes(attachment.inlineData ?: ByteArray(0))
                    target.absolutePath
                }
                DraftAttachment(
                    reference = reference,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes,
                    category = attachment.category,
                )
            }
        }

    /**
     * Writes the payload into session-owned storage and returns a reference-only attachment.
     *
     * The bytes stay local to this call so that a long conversation holds paths rather than
     * every original payload. The file is named `<id>.<ext>`: the id keeps identical payloads
     * deduplicating onto the same file, while the extension lets consumers that infer a type
     * from the file name — such as the Xiaohongshu plugin's upload bridge — see the real MIME
     * type instead of `application/octet-stream`.
     */
    private fun persist(sessionId: String, payload: PreparedPayload): FileAttachment {
        val directory = File(
            File(context.filesDir, ATTACHMENT_DIRECTORY),
            safeSessionId(sessionId),
        ).apply { mkdirs() }
        val id = FileAttachment.stableAttachmentId(
            payload.mimeType,
            payload.displayName,
            payload.bytes,
        )
        val extension = extensionFor(payload.displayName, payload.mimeType)
        val target = File(directory, if (extension == null) id else "$id.$extension")
        if (!target.exists()) target.writeBytes(payload.bytes)
        return FileAttachment(
            mimeType = payload.mimeType,
            id = id,
            sizeBytes = payload.bytes.size.toLong(),
            displayName = payload.displayName,
            payloadReference = target.absolutePath,
            category = payload.category,
        )
    }

    private fun extensionFor(displayName: String, mimeType: String): String? {
        val fromName = displayName.substringAfterLast('.', "")
        if (fromName.isNotEmpty() && fromName.all(Char::isLetterOrDigit)) return fromName.lowercase()
        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType.lowercase().substringBefore(';'))
            ?.takeIf(String::isNotEmpty)
    }

    private fun safeSessionId(sessionId: String): String =
        sessionId.replace(Regex("""[^A-Za-z0-9._-]"""), "_")

    private fun prepareImage(attachment: DraftAttachment): PreparedPayload {
        val file = File(attachment.reference)
        val uri = Uri.fromFile(file)
        var bitmap = decode(resolver, uri)
            ?: throw IllegalArgumentException("The selected image could not be decoded")
        try {
            repeat(MAX_RESIZE_ATTEMPTS) {
                compress(bitmap)?.let { bytes ->
                    return PreparedPayload(
                        mimeType = "image/jpeg",
                        displayName = attachment.displayName.substringBeforeLast('.') + ".jpg",
                        bytes = bytes,
                        category = AttachmentCategory.IMAGE,
                    )
                }
                val width = (bitmap.width * 3 / 4).coerceAtLeast(1)
                val height = (bitmap.height * 3 / 4).coerceAtLeast(1)
                if (width == bitmap.width && height == bitmap.height) return@repeat
                val resized = bitmap.scale(width, height)
                if (resized != bitmap) {
                    bitmap.recycle()
                    bitmap = resized
                }
            }
        } finally {
            bitmap.recycle()
        }
        throw IllegalArgumentException("The selected image is too large after compression")
    }

    private fun prepareOriginal(attachment: DraftAttachment): PreparedPayload {
        val bytes = File(attachment.reference).readBytes()
        check(bytes.size.toLong() == attachment.sizeBytes) {
            "The selected attachment changed before it could be sent"
        }
        return PreparedPayload(
            mimeType = attachment.mimeType,
            displayName = attachment.displayName,
            bytes = bytes,
            category = attachment.category,
        )
    }

    /** A payload that has been normalised but not yet written to session storage. */
    private class PreparedPayload(
        val mimeType: String,
        val displayName: String,
        val bytes: ByteArray,
        val category: AttachmentCategory,
    )

    private fun decode(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.inSampleSize = sampleSize(options, MAX_DIMENSION_PX, MAX_DIMENSION_PX)
        options.inJustDecodeBounds = false
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return rotate(bitmap, orientation)
    }

    private fun compress(bitmap: Bitmap): ByteArray? {
        for (quality in INITIAL_JPEG_QUALITY downTo MIN_JPEG_QUALITY step JPEG_QUALITY_STEP) {
            val output = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) &&
                output.size() <= MAX_BYTES
            ) {
                return output.toByteArray()
            }
        }
        return null
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }

    private fun sampleSize(options: BitmapFactory.Options, width: Int, height: Int): Int {
        val sourceHeight = options.outHeight
        val sourceWidth = options.outWidth
        var result = 1
        if (sourceHeight > height || sourceWidth > width) {
            val halfHeight = sourceHeight / 2
            val halfWidth = sourceWidth / 2
            while (halfHeight / result >= height && halfWidth / result >= width) result *= 2
        }
        return result
    }

    private companion object {
        const val DRAFT_DIRECTORY = "chat-drafts"
        const val ATTACHMENT_DIRECTORY = "chat-attachments"
        const val MAX_DIMENSION_PX = 1280
        const val MAX_BYTES = 512 * 1024
        const val INITIAL_JPEG_QUALITY = 85
        const val MIN_JPEG_QUALITY = 45
        const val JPEG_QUALITY_STEP = 10
        const val MAX_RESIZE_ATTEMPTS = 4
    }
}
