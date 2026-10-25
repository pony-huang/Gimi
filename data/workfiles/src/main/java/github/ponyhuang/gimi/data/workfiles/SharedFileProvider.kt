package github.ponyhuang.gimi.data.workfiles

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import github.ponyhuang.gimi.data.workfiles.repository.SharedStorageAccess
import github.ponyhuang.gimi.data.workfiles.repository.sharedFileMimeType
import java.io.File
import java.io.FileNotFoundException

/** 搜索结果的只读提供方；每次读取重新检查权限和规范路径，临时 URI 授权不提供写入能力。 */
class SharedFileProvider : ContentProvider() {
    private val access by lazy { SharedStorageAccess(requireNotNull(context)) }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = sharedFileMimeType(requireFile(uri))

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = requireFile(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Shared search files are read-only.")
        return ParcelFileDescriptor.open(requireFile(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only provider")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")

    private fun requireFile(uri: Uri): File = access.fileForUri(uri.toString())
        ?: throw FileNotFoundException("Shared file is unavailable or access was revoked.")
}
