package github.ponyhuang.gimi.data.agent.tools.search

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * ObjectBox 中持久化的工具向量记录。
 *
 * @property id ObjectBox 主键。
 * @property scopeKey 隔离不同模型/候选来源组合的检索目录。
 * @property documentKey 运行时工具候选的稳定键。
 * @property contentHash 工具可搜索文本的 SHA-256，用于跳过未变化的重新嵌入。
 * @property searchableText 名称、描述和参数组成的原始索引文本，便于诊断和更新。
 * @property embedding 由固定 MediaPipe USE 模型生成的 100 维向量。
 */
@Entity
data class ToolVectorEntity(
    @Id var id: Long = 0,
    @Index var scopeKey: String = "",
    @Index var documentKey: String = "",
    var contentHash: String = "",
    var searchableText: String = "",
    @HnswIndex(
        dimensions = ToolEmbeddingDimensions.MEDIA_PIPE_USE,
        distanceType = VectorDistanceType.COSINE,
    )
    var embedding: FloatArray = FloatArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ToolVectorEntity

        if (id != other.id) return false
        if (scopeKey != other.scopeKey) return false
        if (documentKey != other.documentKey) return false
        if (contentHash != other.contentHash) return false
        if (searchableText != other.searchableText) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + scopeKey.hashCode()
        result = 31 * result + documentKey.hashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + searchableText.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

/** 工具向量模型的固定维度常量。 */
object ToolEmbeddingDimensions {
    const val MEDIA_PIPE_USE: Long = 100
}
