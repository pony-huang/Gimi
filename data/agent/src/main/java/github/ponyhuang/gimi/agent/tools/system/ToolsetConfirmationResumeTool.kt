package github.ponyhuang.gimi.agent.tools.system

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration

/**
 * 仅用于 ADK 确认恢复阶段定位真实本地工具的隐藏执行入口。
 *
 * ADK 0.6.0 恢复确认调用时只从 `LlmAgent.tools` 按名称查找工具，不会查询
 * `LlmAgent.toolsets`。本工具不向模型追加声明，避免重复暴露 schema；真正执行前
 * 再按当前请求上下文查询 [source]，确保会话勾选、全局授权和确认工具开关仍然生效。
 */
internal class ToolsetConfirmationResumeTool(
    private val source: Toolset,
    tool: BaseTool,
) : BaseTool(
    name = tool.name,
    description = tool.description,
    isLongRunning = tool.isLongRunning,
    customMetadata = tool.customMetadata,
) {
    override fun declaration(): FunctionDeclaration? = null

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest

    override suspend fun run(
        context: ToolContext,
        args: Map<String, Any?>,
    ): Any {
        val activeTool = source.getTools(context.context).firstOrNull { candidate ->
            candidate.name == name
        }
        return activeTool?.run(context, args)
            ?: mapOf("error" to "Tool $name is no longer available for this request.")
    }
}
