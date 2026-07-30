package github.ponyhuang.gimi.agent.tools.search

/**
 * 需要写入向量索引的工具文档。
 *
 * @property key 当前检索目录内唯一的稳定键，格式由调用方管理。
 * @property text 由工具名称、描述和参数说明组成的可搜索文本。
 */
data class ToolVectorDocument(
    val key: String,
    val text: String,
)

/**
 * 向量最近邻检索返回的单个工具匹配。
 *
 * @property key 与 [ToolVectorDocument.key] 对应的稳定键。
 * @property distance ObjectBox 返回的向量距离；越小表示越相关。
 */
data class ToolVectorMatch(
    val key: String,
    val distance: Double,
)

/**
 * 工具向量目录的同步与检索边界。
 *
 * 每次调用必须先把 [documents] 的完整集合同步到当前 [scopeKey]，再对 [query]
 * 做最近邻搜索。调用方只在结果返回后应用用户开关和授权过滤，避免关闭的工具从
 * 向量目录中消失。
 */
interface ToolVectorSearch {
    suspend fun search(
        scopeKey: String,
        documents: List<ToolVectorDocument>,
        query: String,
        maxResultCount: Int,
    ): List<ToolVectorMatch>
}

/**
 * 把文本转换为固定维度向量的模型边界。
 *
 * 工具文档和 Agent 查询必须使用同一实现与版本，否则向量距离没有意义。
 */
interface ToolEmbeddingModel {
    val dimensions: Int
    val version: String

    suspend fun encode(text: String): FloatArray
}
