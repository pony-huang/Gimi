package github.ponyhuang.asssistantai.agent.tools.systems

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.agent.tools.intents.IntentActionQueue
import github.ponyhuang.asssistantai.data.DocumentDirectoryRepository
import github.ponyhuang.asssistantai.permission.MediaPermissionActivity
import javax.inject.Inject
import javax.inject.Singleton

/** Searches user-accessible shared media and explicitly granted document trees. */
@Singleton
class LocalFileSearchTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
    private val documentDirectories: DocumentDirectoryRepository,
) {
    @Tool(
        name = "request_media_file_permissions",
        description = "Launches Android's runtime prompt for permission to search shared images, videos, and audio files.",
    )
    fun requestMediaFilePermissions(): Map<String, Any> = queue.request(
        "Grant media search access",
        "Allow access to shared images, videos, and audio for local file searches.",
        Intent(context, MediaPermissionActivity::class.java),
    )

    @Tool(
        name = "search_media_files",
        description = "Searches shared image, video, and audio file names on this device. Returns up to 50 newest matching files and their content URIs. Requires the matching Android media permission.",
    )
    fun searchMediaFiles(
        @Param("A non-blank file-name query.") query: String,
        @Param("Optional media category: all, image, video, or audio. Defaults to all.") mediaType: String? = "all",
    ): Map<String, Any> {
        val value = query.trim()
        if (value.isEmpty()) return error("query must not be blank.")
        val type = mediaType.orEmpty().trim().lowercase().ifEmpty { "all" }
        val requested = when (type) {
            "all" -> MEDIA_COLLECTIONS
            "image" -> listOf(MEDIA_COLLECTIONS[0])
            "video" -> listOf(MEDIA_COLLECTIONS[1])
            "audio" -> listOf(MEDIA_COLLECTIONS[2])
            else -> return error("mediaType must be all, image, video, or audio.")
        }
        val accessible = requested.filter { hasPermission(it.permission) }
        if (accessible.isEmpty()) return error(
            "Media permission is required. Call request_media_file_permissions and grant access before searching.",
        )
        val results = accessible.flatMap { collection -> queryMedia(collection, value) }
            .sortedByDescending { it["modifiedTimeMillis"] as Long }
            .take(MAX_RESULTS)
        return mapOf(
            "success" to true,
            "query" to value,
            "results" to results,
            "skippedMediaTypes" to requested.filterNot(accessible::contains).map { it.type },
        )
    }

    @Tool(
        name = "search_documents",
        description = "Recursively searches file names in document directories the user has authorized in Settings. Returns up to 50 newest matching files and their content URIs.",
    )
    fun searchDocuments(@Param("A non-blank file-name query.") query: String): Map<String, Any> {
        val value = query.trim()
        if (value.isEmpty()) return error("query must not be blank.")
        val trees = documentDirectories.directories.value
        if (trees.isEmpty()) return error(
            "No document search directory is configured. Ask the user to add one in Settings > Document search directories.",
        )
        val results = mutableListOf<Map<String, Any>>()
        trees.forEach { treeUri ->
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (rootId != null) searchTree(treeUri, rootId, value, results)
        }
        return mapOf(
            "success" to true,
            "query" to value,
            "results" to results.sortedByDescending { it["modifiedTimeMillis"] as Long }.take(MAX_RESULTS),
        )
    }

    @Tool(
        name = "open_local_file",
        description = "Opens a local media-search result or a document-search result in a compatible app for preview. The URI must have been returned by a local search tool.",
    )
    fun openLocalFile(@Param("The contentUri returned by search_media_files or search_documents.") contentUri: String): Map<String, Any> {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return error("contentUri is invalid.")
        if (uri.scheme != "content" || !isAllowedUri(uri)) return error(
            "contentUri is not an accessible media result or a file from an authorized document directory.",
        )
        val mimeType = context.contentResolver.getType(uri) ?: "*/*"
        return queue.request(
            "Open local file",
            "Open a local $mimeType file in a compatible app.",
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun queryMedia(collection: MediaCollection, query: String): List<Map<String, Any>> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        return runCatching {
            context.contentResolver.query(
                collection.uri,
                projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? COLLATE NOCASE",
                arrayOf("%$query%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < MAX_RESULTS) {
                        val id = cursor.getLong(0)
                        add(fileResult(
                            uri = Uri.withAppendedPath(collection.uri, id.toString()),
                            displayName = cursor.getString(1).orEmpty(),
                            mimeType = cursor.getString(2).orEmpty(),
                            sizeBytes = cursor.getLong(3),
                            modifiedTimeMillis = cursor.getLong(4) * 1000,
                            category = collection.type,
                        ))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun searchTree(
        treeUri: Uri,
        parentId: String,
        query: String,
        results: MutableList<Map<String, Any>>,
    ) {
        if (results.size >= MAX_RESULTS) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && results.size < MAX_RESULTS) {
                    val documentId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1).orEmpty()
                    val mimeType = cursor.getString(2).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        searchTree(treeUri, documentId, query, results)
                    } else if (displayName.contains(query, ignoreCase = true)) {
                        results += fileResult(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = cursor.getLong(3),
                            modifiedTimeMillis = cursor.getLong(4),
                            category = "document",
                        )
                    }
                }
            }
        }
    }

    private fun fileResult(
        uri: Uri,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        modifiedTimeMillis: Long,
        category: String,
    ): Map<String, Any> = mapOf(
        "displayName" to displayName,
        "mimeType" to mimeType,
        "sizeBytes" to sizeBytes,
        "modifiedTimeMillis" to modifiedTimeMillis,
        "category" to category,
        "contentUri" to uri.toString(),
    )

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isAllowedUri(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY || documentDirectories.contains(uri)

    private fun error(message: String): Map<String, Any> = mapOf("success" to false, "error" to message)

    private data class MediaCollection(val type: String, val uri: Uri, val permission: String)

    private companion object {
        const val MAX_RESULTS = 50
        val MEDIA_COLLECTIONS = listOf(
            MediaCollection("image", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_IMAGES),
            MediaCollection("video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_VIDEO),
            MediaCollection("audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_AUDIO),
        )
    }
}
