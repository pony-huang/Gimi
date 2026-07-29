package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset

/** 固定本地工具列表对应的动态候选来源。 */
internal class StaticToolCandidateSource(
    override val id: String,
    override val displayName: String,
    private val tools: List<BaseTool>,
) : DynamicToolCandidateSource {
    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools
}

/** 把可按上下文发现工具的 ADK [Toolset] 适配为动态候选来源。 */
internal class ToolsetCandidateSource(
    override val id: String,
    override val displayName: String,
    private val toolset: Toolset,
) : DynamicToolCandidateSource {
    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.getTools(readonlyContext)
}

/**
 * 把构建期已绑定 [ModelConfig] 的官方函数工具集适配为动态候选来源。
 *
 * 模型配置在 Agent 创建时已按会话官方函数选择完成过滤；动态目录只决定这些函数
 * 何时把 schema 暴露给模型，不再次解释厂商协议。
 */
internal class OfficialToolCandidateSource(
    private val toolset: DynamicOfficialToolset,
    private val modelConfig: ModelConfig,
) : DynamicToolCandidateSource {
    override val id: String = toolset.sourceId
    override val displayName: String = toolset.sourceDisplayName

    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.resolveTools(modelConfig)
}
