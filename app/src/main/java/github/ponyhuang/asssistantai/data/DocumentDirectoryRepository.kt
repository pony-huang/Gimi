package github.ponyhuang.asssistantai.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the document-tree grants explicitly selected by the user. */
@Singleton
class DocumentDirectoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _directories = MutableStateFlow(readDirectories())

    val directories: StateFlow<List<Uri>> = _directories.asStateFlow()

    fun addDirectory(uri: Uri): Boolean {
        if (!DocumentsContract.isTreeUri(uri)) return false
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .getOrElse { return false }
        val updated = (_directories.value + uri).distinct()
        persist(updated)
        _directories.value = updated
        return true
    }

    fun removeDirectory(uri: Uri) {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val updated = _directories.value - uri
        persist(updated)
        _directories.value = updated
    }

    /** True only for a file which belongs to one of the persisted tree grants. */
    fun contains(uri: Uri): Boolean = _directories.value.any { treeUri ->
        runCatching { DocumentsContract.isChildDocument(context.contentResolver, treeUri, uri) }
            .getOrDefault(false)
    }

    private fun readDirectories(): List<Uri> = preferences.getStringSet(DIRECTORIES_KEY, emptySet())
        .orEmpty()
        .mapNotNull { encoded -> encoded.toUriOrNull() }
        .filter(DocumentsContract::isTreeUri)

    private fun persist(uris: List<Uri>) {
        preferences.edit(commit = true) {
            putStringSet(DIRECTORIES_KEY, uris.map(Uri::toString).toSet())
        }
    }

    private fun String.toUriOrNull(): Uri? = runCatching(Uri::parse).getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "document_search_directories_v1"
        const val DIRECTORIES_KEY = "tree_uris"
    }
}
