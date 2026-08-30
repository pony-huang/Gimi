package github.ponyhuang.gimi.domain.conversation.model

import java.net.URI

/**
 * A local file returned by an authorized device search tool.
 *
 * @property displayName User-visible file name.
 * @property mimeType MIME type reported by the content provider.
 * @property sizeBytes File size in bytes, or zero when unavailable.
 * @property modifiedTimeMillis Last-modified timestamp, or zero when unavailable.
 * @property category Search-provider category such as image or document.
 * @property contentUri Read-only content URI used to render or open the file.
 */
data class LocalFileReference(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTimeMillis: Long,
    val category: String,
    val contentUri: String,
) {
    /** Whether this file can be rendered as an inline image. */
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)
}

/**
 * Structured result emitted by a supported local file search tool.
 *
 * @property query Original file-name query.
 * @property files Valid, ordered content-URI results.
 */
data class LocalFileSearchResult(
    val query: String,
    val files: List<LocalFileReference>,
)

/** Whether a tool response should be interpreted as a local file search payload. */
fun isLocalFileSearchTool(toolName: String): Boolean = toolName in LocalFileSearchToolNames

/** Converts the JSON-native response of a local search tool into a UI-safe domain result. */
fun parseLocalFileSearchResult(
    toolName: String,
    response: Map<String, Any?>,
): LocalFileSearchResult? {
    if (toolName !in LocalFileSearchToolNames) return null
    val payload = (response["result"] as? Map<*, *>) ?: response
    if (payload["success"] != true) return null
    val rawResults = payload["results"] as? List<*> ?: return null
    val files = rawResults.mapNotNull { raw ->
        val fields = raw as? Map<*, *> ?: return@mapNotNull null
        val displayName = fields["displayName"] as? String ?: return@mapNotNull null
        val mimeType = fields["mimeType"] as? String ?: return@mapNotNull null
        val contentUri = fields["contentUri"] as? String ?: return@mapNotNull null
        val scheme = runCatching { URI(contentUri).scheme }.getOrNull()
        if (!scheme.equals("content", ignoreCase = true)) return@mapNotNull null
        LocalFileReference(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = (fields["sizeBytes"] as? Number)?.toLong() ?: 0L,
            modifiedTimeMillis = (fields["modifiedTimeMillis"] as? Number)?.toLong() ?: 0L,
            category = fields["category"] as? String ?: "document",
            contentUri = contentUri,
        )
    }
    return LocalFileSearchResult(
        query = payload["query"] as? String ?: "",
        files = files,
    )
}

private val LocalFileSearchToolNames = setOf("search_media_files", "search_documents")
