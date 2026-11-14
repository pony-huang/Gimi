package github.ponyhuang.gimi.environment.runtime

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/** 将 Debian 文件系统以只读文档树提供给系统文件界面，导出通过读取源文件完成。 */
class RuntimeDocumentsProvider : DocumentsProvider() {
    private val rootDirectory: File
        get() = File(requireNotNull(context).filesDir, "rootfs").canonicalFile

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        val app = requireNotNull(context)
        if (File(app.filesDir, "installed").isFile && rootDirectory.isDirectory) {
            cursor.addValues(mapOf(
                Root.COLUMN_ROOT_ID to ROOT_ID,
                Root.COLUMN_DOCUMENT_ID to ROOT_DOCUMENT_ID,
                Root.COLUMN_TITLE to app.getString(R.string.runtime_documents_title),
                Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_IS_CHILD,
                Root.COLUMN_MIME_TYPES to "*/*",
                Root.COLUMN_ICON to android.R.drawable.ic_menu_agenda,
            ))
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            includeDocument(documentId, resolveDocument(documentId))
        }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = resolveDocument(parentDocumentId)
        val files = parent.listFiles() ?: throw FileNotFoundException("Directory unavailable")
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }).forEach { file ->
                // rootfs 中的链接只允许指向环境内部；设备节点、管道及失效链接不作为可导出文件。
                val canonical = try { file.canonicalFile } catch (_: IOException) { return@forEach }
                if (canonical.toPath().startsWith(rootDirectory.toPath()) && (canonical.isDirectory || canonical.isFile)) {
                    val relative = rootDirectory.toPath().relativize(file.toPath()).joinToString("/")
                    includeDocument(ROOT_DOCUMENT_ID + relative, canonical, file.name)
                }
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolveDocument(parentDocumentId).toPath()
        val child = resolveDocument(documentId).toPath()
        return child != parent && child.startsWith(parent)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        // URI 即使被客户端请求为读写，也只能返回只读描述符，权限约束必须在数据边界执行。
        if (mode != "r") throw UnsupportedOperationException("Read-only environment")
        signal?.throwIfCanceled()
        val file = resolveDocument(documentId)
        if (!file.isFile) throw FileNotFoundException("Not a regular file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolveDocument(documentId: String): File {
        if (!documentId.startsWith(ROOT_DOCUMENT_ID)) throw FileNotFoundException("Unknown document")
        val root = rootDirectory
        val relative = documentId.removePrefix(ROOT_DOCUMENT_ID)
        val file = File(root, relative).canonicalFile
        if (!File(requireNotNull(context).filesDir, "installed").isFile ||
            !file.toPath().startsWith(root.toPath()) || (!file.isDirectory && !file.isFile)
        ) throw FileNotFoundException("Document unavailable")
        return file
    }

    private fun MatrixCursor.includeDocument(documentId: String, file: File, name: String = file.name) {
        val displayName = if (documentId == ROOT_DOCUMENT_ID) requireNotNull(context).getString(R.string.runtime_documents_title) else name
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = when {
            file.isDirectory -> Document.MIME_TYPE_DIR
            extension in TEXT_EXTENSIONS -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
        addValues(mapOf(
            Document.COLUMN_DOCUMENT_ID to documentId,
            Document.COLUMN_DISPLAY_NAME to displayName,
            Document.COLUMN_MIME_TYPE to mimeType,
            Document.COLUMN_FLAGS to 0,
            Document.COLUMN_SIZE to if (file.isFile) file.length() else null,
            Document.COLUMN_LAST_MODIFIED to file.lastModified(),
        ))
    }

    private fun MatrixCursor.addValues(values: Map<String, Any?>) {
        addRow(columnNames.map { values[it] })
    }

    companion object {
        const val AUTHORITY = "github.ponyhuang.gimi.environment.runtime.documents"
        const val ROOT_ID = "local"
        const val ROOT_DOCUMENT_ID = "root:"
        private val TEXT_EXTENSIONS = setOf("txt", "md", "py", "sh", "kt", "java", "js", "ts", "log", "csv", "yaml", "yml", "toml", "ini", "conf")
        private val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_ICON)
        private val DOCUMENT_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    }
}
