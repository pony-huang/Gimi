package github.ponyhuang.asssistantai.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.LocalToolCatalog
import github.ponyhuang.asssistantai.agent.tools.allowConfirmationRequiredTools
import github.ponyhuang.asssistantai.agent.tools.toolConfigurationOrNull
import github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory
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
 * [getToolsForCategory] 是为 `ToolSearchToolset` 按类别展开 source 时提供的
 * 内部入口；`getTools(readonlyContext)` 仍按全部类别返回。
 */
@Singleton
class LocalToolset @Inject constructor(
    private val catalog: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        getToolsForCategory(readonlyContext, category = null)

    /**
     * 按 [category] 过滤的工具加载入口。
     *
     * @param category 非 null 时仅返回该类别的工具；null 表示全部（与 [getTools] 等价）。
     */
    suspend fun getToolsForCategory(
        readonlyContext: ReadonlyContext?,
        category: LocalToolCategory?,
    ): List<BaseTool> {
        val globallyAuthorized = toolAuthorization.enabledToolIds()
        val selected = readonlyContext.toolConfigurationOrNull()
            ?.enabledLocalToolIds
            ?.intersect(globallyAuthorized)
            ?: globallyAuthorized
        val allowConfirmation = readonlyContext.allowConfirmationRequiredTools()
        return catalog.toolsByCategory().flatMap { (cat, tools) ->
            if (category != null && cat != category) return@flatMap emptyList()
            tools.filter { tool ->
                tool.name in selected &&
                    (allowConfirmation || tool.name !in catalog.confirmationRequiredToolIds)
            }
        }
    }
}
