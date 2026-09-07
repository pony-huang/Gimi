package github.ponyhuang.gimi.data.workfiles.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DocumentsProvider 返回的可持久化 tree 元数据。
 *
 * @property treeUri 规范化后的 tree URI 字符串。
 * @property displayName provider 提供的用户可读名称。
 * @property authority DocumentsProvider authority。
 */
data class DocumentTreeInfo(
    val treeUri: String,
    val displayName: String,
    val authority: String,
)

/** 两个 SAF tree 的包含关系。 */
enum class DocumentTreeRelationship {
    IDENTICAL,
    OVERLAPPING,
    DISJOINT,
}

/** Isolates Android DocumentsProvider behavior from work-directory business rules. */
interface DocumentTreeGateway {
    fun inspect(uri: String): DocumentTreeInfo?

    fun takeReadPermission(uri: String): Boolean

    fun releaseReadPermission(uri: String)

    fun relationship(existingUri: String, candidateUri: String): DocumentTreeRelationship

    fun accessStatus(uri: String): WorkDirectoryAccessStatus
}

/** Android implementation of read-only SAF tree access. */
@Singleton
class AndroidDocumentTreeGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentTreeGateway {
    private val resolver get() = context.contentResolver

    override fun inspect(uri: String): DocumentTreeInfo? = runCatching {
        val treeUri = Uri.parse(uri)
        if (!DocumentsContract.isTreeUri(treeUri)) return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val displayName = resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null
        DocumentTreeInfo(
            treeUri = treeUri.toString(),
            displayName = displayName,
            authority = treeUri.authority.orEmpty(),
        )
    }.getOrNull()

    override fun takeReadPermission(uri: String): Boolean = try {
        resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun releaseReadPermission(uri: String) {
        try {
            resolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // 系统可能已回收授权；本地配置删除不能因此失败。
        } catch (_: IllegalArgumentException) {
            // Provider 消失或 URI 失效时，本地配置仍可安全删除。
        }
    }

    override fun relationship(
        existingUri: String,
        candidateUri: String,
    ): DocumentTreeRelationship {
        if (existingUri == candidateUri) return DocumentTreeRelationship.IDENTICAL
        return runCatching {
            val existingTree = Uri.parse(existingUri)
            val candidateTree = Uri.parse(candidateUri)
            if (existingTree.authority != candidateTree.authority) {
                return DocumentTreeRelationship.DISJOINT
            }
            val existingDocument = DocumentsContract.buildDocumentUriUsingTree(
                existingTree,
                DocumentsContract.getTreeDocumentId(existingTree),
            )
            val candidateDocument = DocumentsContract.buildDocumentUriUsingTree(
                candidateTree,
                DocumentsContract.getTreeDocumentId(candidateTree),
            )
            val overlaps = DocumentsContract.isChildDocument(
                resolver,
                existingDocument,
                candidateDocument,
            ) || DocumentsContract.isChildDocument(
                resolver,
                candidateDocument,
                existingDocument,
            )
            if (overlaps) DocumentTreeRelationship.OVERLAPPING else DocumentTreeRelationship.DISJOINT
        }.getOrDefault(DocumentTreeRelationship.DISJOINT)
    }

    override fun accessStatus(uri: String): WorkDirectoryAccessStatus {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull()
            ?: return WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE
        val hasReadPermission = resolver.persistedUriPermissions.any { permission ->
            permission.uri == parsed && permission.isReadPermission
        }
        if (!hasReadPermission) return WorkDirectoryAccessStatus.PERMISSION_LOST
        return if (inspect(uri) != null) {
            WorkDirectoryAccessStatus.AVAILABLE
        } else {
            WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE
        }
    }
}
