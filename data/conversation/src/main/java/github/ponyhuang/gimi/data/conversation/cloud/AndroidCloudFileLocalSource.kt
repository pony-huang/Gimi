package github.ponyhuang.gimi.data.conversation.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.conversation.attachment.archiveDraftAttachment
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.WorkspaceStorageIds
import github.ponyhuang.gimi.domain.conversation.model.ConversationStorageIds
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileLocalSource
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 云文件归档保留原始字节，图片也不压缩；临时副本避免消耗用户的原草稿。 */
internal class AndroidCloudFileLocalSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: StorageRegistry,
) : CloudFileLocalSource {
    override suspend fun archive(sessionId: String, draft: DraftAttachment): FileAttachment = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "cloud-upload-preparation").apply { mkdirs() }
        val scratch = File(folder, UUID.randomUUID().toString())
        try {
            File(draft.reference).inputStream().use { input ->
                scratch.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(scratch.length() == draft.sizeBytes) { "Attachment changed before cloud preparation" }
            archiveDraftAttachment(
                workspaceRoot = registry.resolve(WorkspaceStorageIds.WORKSPACE, create = true),
                legacyArchiveRoot = registry.resolve(ConversationStorageIds.ATTACHMENTS, create = true),
                attachment = draft.copy(reference = scratch.absolutePath),
                extension = draft.displayName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) },
            )
        } finally {
            scratch.delete()
        }
    }
}
