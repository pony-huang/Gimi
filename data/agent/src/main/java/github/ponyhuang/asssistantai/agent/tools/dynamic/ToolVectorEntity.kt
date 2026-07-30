package github.ponyhuang.asssistantai.agent.tools.dynamic

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
 * @property embedding 由固定 MiniLM 模型生成的 384 维归一化向量。
 */
@Entity
data class ToolVectorEntity(
    @Id var id: Long = 0,
    @Index var scopeKey: String = "",
    @Index var documentKey: String = "",
    var contentHash: String = "",
    var searchableText: String = "",
    @HnswIndex(
        dimensions = ToolEmbeddingDimensions.MINI_LM,
        distanceType = VectorDistanceType.COSINE,
    )
    var embedding: FloatArray = FloatArray(0),
)

/** 工具向量模型的固定维度常量。 */
object ToolEmbeddingDimensions {
    const val MINI_LM: Long = 384
}
