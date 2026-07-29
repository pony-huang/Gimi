package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.tools.system.LocalToolset
import github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory

/**
 * 单个 [LocalToolCategory] 下的本地工具检索来源。
 *
 * 在 [ToolSearchToolset] 中每个类别注册为一个独立 source；模型检索时拿到的
 * `source: "<displayName>"` 就是这个类别的中文标签（例如「音频」/「日历」）。
 *
 * [LocalToolset] 暴露了一个 `category-aware` 内部入口（`getTools(readonlyContext, category)`），
 * 让 source 在加载时直接按类别缩小范围；不需要在 RunConfig metadata 上做文章，也避免
 * 包装 ADK 内部 `ReadonlyContext`。
 */
internal class LocalCategorySource(
    private val toolset: LocalToolset,
    override val category: LocalToolCategory,
) : CategorizedDynamicToolCandidateSource {
    override val id: String = "local:${category.id}"
    override val displayName: String = category.displayName

    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.getToolsForCategory(readonlyContext, category)
}
