package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.LocalToolCatalog
import github.ponyhuang.gimi.data.agent.tools.allowConfirmationRequiredTools
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具的 ADK [Toolset] 入口。
 *
 * 过滤全部推迟到每次模型请求时执行：
 * - 全局授权（[ToolAuthorizationRepository]，在设置中自定义）；
 * - 调用方不允许确认类工具时（如无 UI 的后台执行），排除
 *   [LocalToolCatalog.confirmationRequiredToolIds]。
 */
@Singleton
class LocalToolset @Inject constructor(
    private val catalog: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val authorized = toolAuthorization.enabledToolIds()
        val allowConfirmation = readonlyContext.allowConfirmationRequiredTools()
        return catalog.tools().filter { tool ->
            tool.name in authorized &&
                (allowConfirmation || tool.name !in catalog.confirmationRequiredToolIds)
        }
    }
}
