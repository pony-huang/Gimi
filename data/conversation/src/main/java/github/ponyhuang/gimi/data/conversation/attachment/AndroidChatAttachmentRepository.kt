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
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.WorkspaceStorageIds
import github.ponyhuang.gimi.domain.conversation.model.ConversationStorageIds
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
    storageRegistry: StorageRegistry,
) : ChatAttachmentRepository {
    private val resolver = context.contentResolver
    private val attachmentRoot = storageRegistry.resolve(
        ConversationStorageIds.ATTACHMENTS,
        create = true,
    )

    // 全局共享工作区：所有会话的附件统一归档于此，文件不随会话删除（用户自管）。
    private val workspaceRoot = storageRegistry.resolve(
        WorkspaceStorageIds.WORKSPACE,
        create = true,
    )
    private val draftRoots = listOf(
        storageRegistry.resolve(ConversationStorageIds.REPOSITORY_DRAFTS, create = true),
        storageRegistry.resolve(ConversationStorageIds.COMPOSER_DRAFTS, create = true),
    )
    private val draftRoot = draftRoots.first()

    override suspend fun read(
        sessionId: String,
        attachments: List<DraftAttachment>,
    ): List<FileAttachment> =
        withContext(Dispatchers.IO) {
            // 归档目的地是全局共享工作区，与会话无关；sessionId 仅为接口兼容保留。
            attachments.map { attachment ->
                when (attachment.category) {
                    AttachmentCategory.IMAGE -> persist(prepareImage(attachment))
                    AttachmentCategory.AUDIO,
                    AttachmentCategory.DOCUMENT,
                    -> archiveDraftAttachment(
                        workspaceRoot = workspaceRoot,
                        legacyArchiveRoot = attachmentRoot,
                        attachment = attachment,
                        extension = extensionFor(attachment.displayName, attachment.mimeType),
                    )
                }
            }
        }

    override suspend fun deleteDrafts(attachments: List<DraftAttachment>) {
        withContext(Dispatchers.IO) {
            attachments.forEach { attachment ->
                val file = File(attachment.reference)
                val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
                if (draftRoots.any { candidate.parentFile == it.canonicalFile }) candidate.delete()
            }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            // 仅清理遗留的按会话归档目录（旧版本数据）；工作区文件永不随会话删除。
            conversationSessionDirectory(attachmentRoot, sessionId).deleteRecursively()
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
                    val target = File(draftRoot, attachment.id)
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
     * Writes the re-encoded image payload into the shared workspace and returns a
     * reference-only attachment.
     *
     * Only images take this path: they must be decoded and compressed before sending. The
     * archive name embeds the sanitized display name plus a content-addressed suffix
     * (`<显示名>-<sha256 前 16 位>.<ext>`)，so identical images with the same name reuse
     * the same file across sessions, while the extension lets consumers that infer a type
     * from the file name — such as the Xiaohongshu plugin's upload bridge — see the real
     * MIME type. Audio and document drafts skip this path and are moved into the workspace
     * by [archiveDraftAttachment] instead. The attachment id remains the full stable
     * content hash, unchanged from the previous session-scoped layout.
     */
    private fun persist(payload: PreparedPayload): FileAttachment {
        val id = FileAttachment.stableAttachmentId(
            payload.mimeType,
            payload.displayName,
            payload.bytes,
        )
        val extension = extensionFor(payload.displayName, payload.mimeType)
        val target = writeWorkspaceImage(
            workspaceRoot = workspaceRoot,
            baseName = archiveBaseName(payload.displayName),
            contentSuffix = id.take(IMAGE_SUFFIX_CHARS),
            extension = extension,
            bytes = payload.bytes,
        )
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
        const val MAX_DIMENSION_PX = 1280
        const val MAX_BYTES = 512 * 1024
        const val INITIAL_JPEG_QUALITY = 85
        const val MIN_JPEG_QUALITY = 45
        const val JPEG_QUALITY_STEP = 10
        const val MAX_RESIZE_ATTEMPTS = 4
        const val IMAGE_SUFFIX_CHARS = 16
    }
}

internal fun conversationSessionDirectory(root: File, sessionId: String): File {
    val safeSessionId = sessionId.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    val directory = File(root.canonicalFile, safeSessionId).canonicalFile
    check(directory.parentFile == root.canonicalFile) { "Invalid conversation session directory" }
    return directory
}

/**
 * 把音频/文档草稿移动进全局共享工作区，以零拷贝方式建立持久载荷引用。
 *
 * 草稿位于 cache 草稿目录：系统存储紧张时可能被回收，发送成功后也会被清理，不能直接
 * 作为历史消息的载荷引用。用移动（rename）代替复制归档，同一份数据不再产生第二份副
 * 本。归档名 `<清洗显示名>-<12 位随机后缀>.<ext>`，撞名时重新生成后缀。三个约定：
 * - [workspaceRoot] 内的引用原样返回：工作区文件本就是最终归档，改名毫无意义；
 * - [legacyArchiveRoot] 的会话子目录内的引用也原样返回——旧版本按会话归档的文件仍被
 *   旧历史事件引用，编辑重发路径复用它们时不能改名，否则旧事件路径悬空；
 * - [extension] 由调用方解析并校验合法性（displayName 扩展名优先，MimeTypeMap 兜底），
 *   归档名保留扩展名，供知乎等上传桥按文件名推断 Content-Type。
 *
 * rename 失败（跨卷挂载等罕见场景）时回退为复制后删除，成本回落到旧的复制归档。
 */
internal fun archiveDraftAttachment(
    workspaceRoot: File,
    legacyArchiveRoot: File,
    attachment: DraftAttachment,
    extension: String?,
): FileAttachment {
    val source = File(attachment.reference)
    require(source.isFile) { "The selected attachment is no longer available" }
    check(source.length() == attachment.sizeBytes) {
        "The selected attachment changed before it could be sent"
    }
    val canonicalSource = source.canonicalFile
    val alreadyArchived = canonicalSource.parentFile == workspaceRoot.canonicalFile ||
        canonicalSource.parentFile?.parentFile == legacyArchiveRoot.canonicalFile
    if (alreadyArchived) {
        return FileAttachment.fromFile(
            file = source,
            mimeType = attachment.mimeType,
            displayName = attachment.displayName,
        )
    }
    workspaceRoot.mkdirs()
    val target = resolveWorkspaceArchiveTarget(
        workspaceRoot = workspaceRoot,
        baseName = archiveBaseName(attachment.displayName),
        extension = extension,
    )
    if (!source.renameTo(target)) {
        source.copyTo(target, overwrite = false)
        source.delete()
    }
    return FileAttachment.fromFile(
        file = target,
        mimeType = attachment.mimeType,
        displayName = attachment.displayName,
    )
}
