package github.ponyhuang.asssistantai.agent.tools.search

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.LocalToolCatalog
import github.ponyhuang.asssistantai.agent.tools.system.LocalToolset

/**
 * 全部本地工具的扁平向量候选来源。
 *
 * [loadAllTools] 不读取会话开关，确保完整目录先写入向量索引；
 * [loadEnabledTools] 才委托 [LocalToolset] 应用全局授权、会话开关和确认能力过滤。
 */
internal class LocalToolSource(
    private val catalog: LocalToolCatalog,
    private val toolset: LocalToolset,
) : ToolCandidateSource {
    override val id: String = "local"
    override val displayName: String = "Local"

    override suspend fun loadAllTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> = catalog.tools()

    override suspend fun loadEnabledTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> = toolset.getTools(readonlyContext)
}
