package github.ponyhuang.asssistantai.data.workfiles.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.workfiles.model.WorkDirectory
import github.ponyhuang.asssistantai.domain.workfiles.repository.WorkDirectoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persists the document-tree grants explicitly selected by the user. */
@Singleton
class DocumentDirectoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkDirectoryRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val directories = MutableStateFlow(readDirectories())

    override fun observeDirectories() = directories
        .map { values -> values.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun currentDirectories(): List<WorkDirectory> =
        directories.value.map { it.toDomain() }

    override fun addDirectory(uri: String): Boolean {
        val parsed = uri.toUriOrNull() ?: return false
        if (!DocumentsContract.isTreeUri(parsed)) return false
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(parsed, flags) }
            .getOrElse { return false }
        val updated = (directories.value + parsed).distinct()
        persist(updated)
        directories.value = updated
        return true
    }

    override fun removeDirectory(uri: String) {
        val parsed = uri.toUriOrNull() ?: return
        context.contentResolver.releasePersistableUriPermission(
            parsed,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val updated = directories.value - parsed
        persist(updated)
        directories.value = updated
    }

    /** True only for a file which belongs to one of the persisted tree grants. */
    override fun contains(uri: String): Boolean {
        val parsed = uri.toUriOrNull() ?: return false
        return directories.value.any { treeUri ->
            runCatching {
                DocumentsContract.isChildDocument(context.contentResolver, treeUri, parsed)
            }.getOrDefault(false)
        }
    }

    private fun readDirectories(): List<Uri> = preferences.getStringSet(DIRECTORIES_KEY, emptySet())
        .orEmpty()
        .mapNotNull { it.toUriOrNull() }
        .filter(DocumentsContract::isTreeUri)

    private fun persist(uris: List<Uri>) {
        preferences.edit(commit = true) {
            putStringSet(DIRECTORIES_KEY, uris.map(Uri::toString).toSet())
        }
    }

    private fun Uri.toDomain() = WorkDirectory(
        uri = toString(),
        displayName = lastPathSegment ?: toString(),
        authority = authority.orEmpty(),
    )

    private fun String.toUriOrNull(): Uri? = runCatching(Uri::parse).getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "document_search_directories_v1"
        const val DIRECTORIES_KEY = "tree_uris"
    }
}
