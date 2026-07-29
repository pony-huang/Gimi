package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.toolConfigurationOrNull

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
 * 把构建期已绑定服务级 [ModelConfig] 的官方函数工具集适配为动态候选来源。
 *
 * 会话级函数选择通过 invocation 上下文（RunConfig metadata）按请求读取；
 * 动态目录只决定这些函数何时把 schema 暴露给模型，不再次解释厂商协议。
 */
internal class OfficialToolCandidateSource(
    private val toolset: DynamicOfficialToolset,
    private val modelConfig: ModelConfig,
) : DynamicToolCandidateSource {
    override val id: String = toolset.sourceId
    override val displayName: String = toolset.sourceDisplayName

    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.resolveTools(modelConfig, readonlyContext.toolConfigurationOrNull())
}
