package github.ponyhuang.asssistantai.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.LocalToolCatalog
import github.ponyhuang.asssistantai.agent.tools.allowConfirmationRequiredTools
import github.ponyhuang.asssistantai.agent.tools.toolConfigurationOrNull
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具的 ADK [Toolset] 入口。
 *
 * 过滤全部推迟到每次模型请求时按 invocation 上下文（RunConfig metadata）执行：
 * - 全局授权（[ToolAuthorizationRepository]）∩ 会话勾选（[ToolRunMetadata] 透传）；
 * - 调用方不允许确认类工具时（如无 UI 的后台执行），排除
 *   [LocalToolCatalog.confirmationRequiredToolIds]。
 *
 * 因此会话内勾选变化不需要重建 Agent。
 *
 */
@Singleton
class LocalToolset @Inject constructor(
    private val catalog: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val globallyAuthorized = toolAuthorization.enabledToolIds()
        val selected = readonlyContext.toolConfigurationOrNull()
            ?.enabledLocalToolIds
            ?.intersect(globallyAuthorized)
            ?: globallyAuthorized
        val allowConfirmation = readonlyContext.allowConfirmationRequiredTools()
        return catalog.tools().filter { tool ->
            tool.name in selected &&
                (allowConfirmation || tool.name !in catalog.confirmationRequiredToolIds)
        }
    }
}
