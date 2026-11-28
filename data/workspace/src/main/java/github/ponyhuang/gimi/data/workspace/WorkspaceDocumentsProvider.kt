package github.ponyhuang.gimi.data.workspace

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.workspaceDirectorySpec
import github.ponyhuang.gimi.domain.workspace.WorkspaceDocumentsContract
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/**
 * 将共享缓存区（`files/workspace/`）以系统文档树形式提供给官方文件管理器。
 *
 * 缓存区仅承载聊天与环境导出的持久归档文件：对外开放只读打开（预览/分享/导出）与单文件删除，
 * 拒绝外部写入、新建或重命名，并隐藏归档过程中的 `.tmp` 瞬时暂存文件。
 */
class WorkspaceDocumentsProvider : DocumentsProvider() {
    private val rootDirectory: File
        get() = AndroidAppDirectoryResolver(requireNotNull(context))
            .resolve(workspaceDirectorySpec, create = true)
            .canonicalFile

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        val app = requireNotNull(context)
        if (rootDirectory.isDirectory) {
            cursor.addValues(
                mapOf(
                    Root.COLUMN_ROOT_ID to WorkspaceDocumentsContract.ROOT_ID,
                    Root.COLUMN_DOCUMENT_ID to WorkspaceDocumentsContract.ROOT_DOCUMENT_ID,
                    Root.COLUMN_TITLE to app.getString(R.string.workspace_documents_title),
                    Root.COLUMN_SUMMARY to app.getString(R.string.workspace_documents_summary),
                    Root.COLUMN_FLAGS to (Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY),
                    Root.COLUMN_MIME_TYPES to "*/*",
                    Root.COLUMN_ICON to (app.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_menu_agenda),
                ),
            )
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            includeDocument(documentId, resolveDocument(documentId))
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val parent = resolveDocument(parentDocumentId)
        val files = parent.listFiles() ?: throw FileNotFoundException("Directory unavailable")
        val rootPath = rootDirectory.toPath()
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            files
                .filter { !it.name.endsWith(TMP_SUFFIX) }
                .sortedWith(
                    compareBy<File> { !it.isDirectory }
                        .thenByDescending { it.lastModified() }
                        .thenBy { it.name },
                )
                .forEach { file ->
                    val canonical = try {
                        file.canonicalFile
                    } catch (_: IOException) {
                        return@forEach
                    }
                    if (canonical.toPath().startsWith(rootPath) && (canonical.isDirectory || canonical.isFile)) {
                        val relative = rootPath.relativize(canonical.toPath()).joinToString("/")
                        includeDocument(
                            documentId = WorkspaceDocumentsContract.ROOT_DOCUMENT_ID + relative,
                            file = canonical,
                            name = file.name,
                        )
                    }
                }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolveDocument(parentDocumentId).toPath()
        val child = resolveDocument(documentId).toPath()
        return child != parent && child.startsWith(parent)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        // 缓存区文件只允许外部查看器只读消费，防止外部覆盖已归档附件内容。
        if (mode != "r") throw UnsupportedOperationException("Read-only workspace")
        signal?.throwIfCanceled()
        val file = resolveDocument(documentId)
        if (!file.isFile) throw FileNotFoundException("Not a regular file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == WorkspaceDocumentsContract.ROOT_DOCUMENT_ID) {
            throw UnsupportedOperationException("Cannot delete workspace root")
        }
        val file = resolveDocument(documentId)
        if (!file.isFile || !file.delete()) {
            throw FileNotFoundException("Document could not be deleted")
        }
    }

    private fun resolveDocument(documentId: String): File {
        if (!documentId.startsWith(WorkspaceDocumentsContract.ROOT_DOCUMENT_ID)) {
            throw FileNotFoundException("Unknown document")
        }
        val root = rootDirectory
        val relative = documentId.removePrefix(WorkspaceDocumentsContract.ROOT_DOCUMENT_ID)
        val file = File(root, relative).canonicalFile
        if (
            !file.toPath().startsWith(root.toPath()) ||
            (file != root && file.name.endsWith(TMP_SUFFIX)) ||
            (!file.isDirectory && !file.isFile)
        ) {
            throw FileNotFoundException("Document unavailable")
        }
        return file
    }

    private fun MatrixCursor.includeDocument(
        documentId: String,
        file: File,
        name: String = file.name,
    ) {
        val isRoot = documentId == WorkspaceDocumentsContract.ROOT_DOCUMENT_ID
        val displayName = if (isRoot) {
            requireNotNull(context).getString(R.string.workspace_documents_title)
        } else {
            name
        }
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = when {
            file.isDirectory -> Document.MIME_TYPE_DIR
            extension in TEXT_EXTENSIONS -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
        val flags = if (!isRoot && file.isFile) Document.FLAG_SUPPORTS_DELETE else 0
        addValues(
            mapOf(
                Document.COLUMN_DOCUMENT_ID to documentId,
                Document.COLUMN_DISPLAY_NAME to displayName,
                Document.COLUMN_MIME_TYPE to mimeType,
                Document.COLUMN_FLAGS to flags,
                Document.COLUMN_SIZE to if (file.isFile) file.length() else null,
                Document.COLUMN_LAST_MODIFIED to file.lastModified(),
            ),
        )
    }

    private fun MatrixCursor.addValues(values: Map<String, Any?>) {
        addRow(columnNames.map { values[it] })
    }

    companion object {
        private const val TMP_SUFFIX = ".tmp"
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "py", "sh", "kt", "java", "js", "ts",
            "log", "csv", "yaml", "yml", "toml", "ini", "conf",
        )
        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_ICON,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
