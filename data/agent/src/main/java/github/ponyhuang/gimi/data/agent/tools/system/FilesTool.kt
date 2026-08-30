package github.ponyhuang.gimi.data.agent.tools.system

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
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import github.ponyhuang.gimi.data.agent.permission.MediaPermissionActivity
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

/**
 * 文件域工具：文件选择、共享媒体搜索、文档目录递归搜索、预览打开。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.FILES]。
 */
@Singleton
class FilesTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
    private val documentDirectories: WorkDirectoryRepository,
) {
    // ---------- 文件选择 ----------

    @Tool(
        name = "get_file",
        description = "Opens a file picker so the user can choose a file of the requested type, then returns the chosen file's content URI in the 'data' field. Returns cancelled=true when the user aborts the picker.",
        requireConfirmation = true,
    )
    suspend fun getFile(@Param("A file type such as image or pdf.") mimeType: String): Map<String, Any> =
        pick(Intent.ACTION_GET_CONTENT, "Choose file", mimeType)

    @Tool(
        name = "open_file",
        description = "Opens the system document picker so the user can select a persistent file of the requested type, then returns the chosen document's content URI in the 'data' field. Returns cancelled=true when the user aborts the picker.",
        requireConfirmation = true,
    )
    suspend fun openFile(@Param("A file type such as image or pdf.") mimeType: String): Map<String, Any> =
        pick(Intent.ACTION_OPEN_DOCUMENT, "Open file", mimeType)

    private suspend fun pick(action: String, title: String, mimeType: String): Map<String, Any> {
        val type = mimeType.trim()
        if (type.isEmpty() || !type.contains('/')) return mapOf("success" to false, "error" to "mimeType must be a MIME type.")
        return queue.requestForResult(
            title,
            "$title with type $type.",
            Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(type),
        )
    }

    // ---------- 媒体文件搜索 ----------

    @Tool(
        name = "request_media_file_permissions",
        description = "Asks the user to grant permission to search shared images, videos, and audio files.",
        requireConfirmation = true,
    )
    fun requestMediaFilePermissions(): Map<String, Any> = queue.request(
        "Grant media search access",
        "Allow access to shared images, videos, and audio for local file searches.",
        Intent(context, MediaPermissionActivity::class.java),
    )

    @Tool(
        name = "search_media_files",
        description = "Searches shared images, videos, and audio files by file name on this device. " +
            "The query matches file NAMES only (no semantic or content search): for a request like " +
            "'photos of cats', derive the short keyword most likely inside a file name (e.g. 'cat') " +
            "instead of sending the full sentence. If the search returns no results, retry once or " +
            "twice with a different keyword before reporting failure. Returns up to 50 newest matches " +
            "as structured results that the chat UI renders directly. Summarize the outcome without " +
            "repeating the complete file-name list. Requires permission to access the requested media type.",
        requireConfirmation = true,
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
        return mediaResponse(value, results, requested.filterNot(accessible::contains).map { it.type })
    }

    // ---------- 文档目录搜索 ----------

    @Tool(
        name = "search_documents",
        description = "Recursively searches file names in document directories the user has authorized in Settings. " +
            "The query matches file NAMES only: use a short keyword most likely inside the target file name, " +
            "and if no results are returned, retry once or twice with a different keyword before reporting failure. " +
            "Returns up to 50 newest matching files as structured results that the chat UI renders directly. " +
            "Summarize the outcome without repeating the complete file-name list.",
        requireConfirmation = true,
    )
    fun searchDocuments(@Param("A non-blank file-name query.") query: String): Map<String, Any> {
        val value = query.trim()
        if (value.isEmpty()) return error("query must not be blank.")
        val trees = documentDirectories.currentDirectories().map { Uri.parse(it.uri) }
        if (trees.isEmpty()) return error(
            "No document search directory is configured. Ask the user to add one in Settings > Document search directories.",
        )
        val results = mutableListOf<Map<String, Any>>()
        trees.forEach { treeUri ->
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (rootId != null) searchTree(treeUri, rootId, listOf(value), results)
        }
        // 精确短语零结果时按分词放宽（任一词命中），与 queryMedia 的兜底策略保持一致。
        if (results.isEmpty()) {
            val tokens = relaxedQueryTokens(value)
            if (tokens.size > 1) {
                trees.forEach { treeUri ->
                    val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                    if (rootId != null) searchTree(treeUri, rootId, tokens, results)
                }
            }
        }
        return documentResponse(value, results)
    }

    @Tool(
        name = "open_local_file",
        description = "Opens a file found by a local search in a compatible app for preview. The identifier must come from search_media_files or search_documents.",
        requireConfirmation = true,
    )
    fun openLocalFile(@Param("The file identifier returned by search_mediaFiles or searchDocuments.") contentUri: String): Map<String, Any> {
        val uri = runCatching { contentUri.toUri() }.getOrNull() ?: return error("contentUri is invalid.")
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

    // ---------- helpers ----------

    private fun queryMedia(collection: MediaCollection, query: String): List<Map<String, Any>> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val primary = queryByName(collection, projection, listOf(query))
        // 精确短语零结果时按空白分词放宽为“任一词命中”，避免多词查询因整体短语不在文件名里而完全失败。
        if (primary.isNotEmpty()) return primary
        val tokens = relaxedQueryTokens(query)
        if (tokens.size <= 1) return primary
        return queryByName(collection, projection, tokens)
    }

    private fun queryByName(
        collection: MediaCollection,
        projection: Array<String>,
        patterns: List<String>,
    ): List<Map<String, Any>> {
        val selection = patterns.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\' COLLATE NOCASE"
        }
        val selectionArgs = patterns.map { "%${escapeLikePattern(it)}%" }.toTypedArray()
        return runCatching {
            context.contentResolver.query(
                collection.uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < MAX_RESULTS) {
                        val id = cursor.getLong(0)
                        add(
                            fileResult(
                                uri = Uri.withAppendedPath(collection.uri, id.toString()),
                                displayName = cursor.getString(1).orEmpty(),
                                mimeType = cursor.getString(2).orEmpty(),
                                sizeBytes = cursor.getLong(3),
                                modifiedTimeMillis = cursor.getLong(4) * 1000,
                                category = collection.type,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun searchTree(
        treeUri: Uri,
        parentId: String,
        patterns: List<String>,
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
                        searchTree(treeUri, documentId, patterns, results)
                    } else if (patterns.any { displayName.contains(it, ignoreCase = true) }) {
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

    // 零结果时明确告知模型“已搜过但文件名不含该词”，推动它换关键字重试而不是直接放弃。
    private fun mediaResponse(
        query: String,
        results: List<Map<String, Any>>,
        skippedMediaTypes: List<String>,
    ): Map<String, Any> = buildMap {
        put("success", true)
        put("query", query)
        put("results", results)
        put("skippedMediaTypes", skippedMediaTypes)
        if (results.isEmpty()) {
            put("hint", NO_RESULT_HINT)
        }
    }

    private fun documentResponse(query: String, unsorted: List<Map<String, Any>>): Map<String, Any> = buildMap {
        put("success", true)
        put("query", query)
        put("results", unsorted.sortedByDescending { it["modifiedTimeMillis"] as Long }.take(MAX_RESULTS))
        if (unsorted.isEmpty()) {
            put("hint", NO_RESULT_HINT)
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
        uri.authority == MediaStore.AUTHORITY || documentDirectories.contains(uri.toString())

    private fun error(message: String): Map<String, Any> = mapOf("success" to false, "error" to message)

    private data class MediaCollection(val type: String, val uri: Uri, val permission: String)

    private companion object {
        const val MAX_RESULTS = 50
        val MEDIA_COLLECTIONS by lazy {
            listOf(
                MediaCollection("image", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_IMAGES),
                MediaCollection("video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_VIDEO),
                MediaCollection("audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_AUDIO),
            )
        }
    }
}

internal fun escapeLikePattern(value: String): String = buildString(value.length) {
    value.forEach { character ->
        if (character == '\\' || character == '%' || character == '_') append('\\')
        append(character)
    }
}

// 按空白分词去重；单 token 场景调用方直接回退原查询，不产生行为变化。
internal fun relaxedQueryTokens(query: String): List<String> =
    query.trim().split(Regex("\\s+")).filter(String::isNotEmpty).distinct()

private const val NO_RESULT_HINT =
    "No file name contains this query. Ask the user for a different or shorter keyword, " +
        "or try a keyword that likely appears in the target file name."
