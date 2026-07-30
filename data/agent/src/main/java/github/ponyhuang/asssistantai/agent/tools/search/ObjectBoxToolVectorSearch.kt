package github.ponyhuang.asssistantai.agent.tools.search

import io.objectbox.Box
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 使用 ObjectBox HNSW 索引实现的工具最近邻检索。
 *
 * 同一个 [scopeKey] 中，文本未变化的工具复用已有向量；新增或变化的工具先生成
 * 向量并写入，已移除的工具记录同步删除。完整同步结束后才执行最近邻查询。
 */
@Singleton
class ObjectBoxToolVectorSearch @Inject constructor(
    private val box: Box<ToolVectorEntity>,
    private val embeddingModel: ToolEmbeddingModel,
) : ToolVectorSearch {
    private val mutex = Mutex()

    override suspend fun search(
        scopeKey: String,
        documents: List<ToolVectorDocument>,
        query: String,
        maxResultCount: Int,
    ): List<ToolVectorMatch> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val effectiveScope = "$scopeKey|embedding:${embeddingModel.version}"
            synchronize(effectiveScope, documents)
            if (documents.isEmpty() || maxResultCount <= 0) {
                return@withLock emptyList()
            }

            val queryVector = embeddingModel.encode(query).validated()
            val nearest = ToolVectorEntity_.embedding
                .nearestNeighbors(queryVector, maxResultCount)
                .and(ToolVectorEntity_.scopeKey.equal(effectiveScope))
            val objectBoxQuery = box.query(nearest).build()
            objectBoxQuery.use { objectBoxQuery ->
                objectBoxQuery.findWithScores().map { result ->
                    ToolVectorMatch(
                        key = result.get().documentKey,
                        distance = result.score,
                    )
                }
            }
        }
    }

    private suspend fun synchronize(
        scopeKey: String,
        documents: List<ToolVectorDocument>,
    ) {
        require(documents.map(ToolVectorDocument::key).distinct().size == documents.size) {
            "Tool vector document keys must be unique within a scope."
        }

        val existingQuery = box.query(ToolVectorEntity_.scopeKey.equal(scopeKey)).build()
        val existing = existingQuery.use { existingQuery ->
            existingQuery.find()
        }
        val existingByKey = existing.associateBy(ToolVectorEntity::documentKey)
        val desiredKeys = documents.mapTo(hashSetOf(), ToolVectorDocument::key)

        documents.forEach { document ->
            val contentHash = document.text.sha256()
            val current = existingByKey[document.key]
            if (current?.contentHash == contentHash) return@forEach
            box.put(
                ToolVectorEntity(
                    id = current?.id ?: 0,
                    scopeKey = scopeKey,
                    documentKey = document.key,
                    contentHash = contentHash,
                    searchableText = document.text,
                    embedding = embeddingModel.encode(document.text).validated(),
                ),
            )
        }
        existing.asSequence()
            .filter { entity -> entity.documentKey !in desiredKeys }
            .forEach { entity -> box.remove(entity.id) }
    }

    private fun FloatArray.validated(): FloatArray {
        require(size == embeddingModel.dimensions) {
            "Expected ${embeddingModel.dimensions} embedding dimensions, got $size."
        }
        return this
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
