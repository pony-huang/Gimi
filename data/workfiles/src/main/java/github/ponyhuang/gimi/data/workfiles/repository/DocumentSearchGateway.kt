package github.ponyhuang.gimi.data.workfiles.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Isolates recursive DocumentsProvider traversal from repository policy. */
interface DocumentSearchGateway {
    suspend fun search(treeUri: String, query: String): List<WorkFileSearchResult>

    suspend fun contains(treeUri: String, contentUri: String): Boolean
}

/** Android DocumentsProvider traversal used by local document search. */
@Singleton
class AndroidDocumentSearchGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentSearchGateway {
    override suspend fun search(
        treeUri: String,
        query: String,
    ): List<WorkFileSearchResult> = withContext(Dispatchers.IO) {
        val parsedTree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return@withContext emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(parsedTree) }.getOrNull()
            ?: return@withContext emptyList()
        val results = mutableListOf<WorkFileSearchResult>()
        searchChildren(parsedTree, rootId, query, mutableSetOf(), results)
        results
    }

    override suspend fun contains(treeUri: String, contentUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return@withContext false
            val document = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return@withContext false
            runCatching {
                DocumentsContract.isChildDocument(context.contentResolver, tree, document)
            }.getOrDefault(false)
        }

    private suspend fun searchChildren(
        treeUri: Uri,
        parentId: String,
        query: String,
        visitedDirectoryIds: MutableSet<String>,
        results: MutableList<WorkFileSearchResult>,
    ) {
        coroutineContext.ensureActive()
        if (!visitedDirectoryIds.add(parentId) || results.size >= MAX_GATEWAY_RESULTS) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && results.size < MAX_GATEWAY_RESULTS) {
                    coroutineContext.ensureActive()
                    val documentId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1).orEmpty()
                    val mimeType = cursor.getString(2).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        searchChildren(treeUri, documentId, query, visitedDirectoryIds, results)
                    } else if (displayName.contains(query, ignoreCase = true)) {
                        results += WorkFileSearchResult(
                            contentUri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                documentId,
                            ).toString(),
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = cursor.getLong(3),
                            modifiedTimeMillis = cursor.getLong(4),
                        )
                    }
                }
            }
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        }
    }

    private companion object {
        const val MAX_GATEWAY_RESULTS = 200
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
