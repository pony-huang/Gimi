package github.ponyhuang.gimi.data.agent.plugins

import com.google.adk.kt.plugins.ReflectAndRetryToolPlugin
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** 仅让模型修正无副作用的技能读取和工具检索，执行类工具不进入重试预算。 */
internal object AgentToolRetryPolicy {
    private val readTools = setOf("list_skills", "load_skill", "load_skill_resource", "tool_search")

    fun isRetryable(toolName: String, error: Any): Boolean =
        toolName in readTools && error !is CancellationException &&
            (error is IllegalArgumentException || error is IOException || error is String)

    fun plugin(): Plugin {
        val delegate = ReflectAndRetryToolPlugin(
            maxRetries = 2,
            throwExceptionIfRetryExceeded = false,
            resultErrorExtractor = { _, tool, _, result ->
                // 不修改非白名单工具的业务结果，尤其不能把授权拒绝包装成重试指导。
                if (tool.name in readTools) (result["error"] as? String)?.takeIf(String::isNotBlank) else null
            },
            retryPredicate = { _, tool, _, error -> isRetryable(tool.name, error) },
        )
        return object : Plugin by delegate {
            override suspend fun onToolError(
                context: ToolContext,
                tool: BaseTool,
                args: Map<String, Any?>,
                error: Throwable,
            ): CallbackChoice<Unit, Map<String, Any?>> =
                if (isRetryable(tool.name, error)) delegate.onToolError(context, tool, args, error)
                else CallbackChoice.Continue(Unit)

            override suspend fun afterTool(
                context: ToolContext,
                tool: BaseTool,
                args: Map<String, Any?>,
                result: Map<String, Any?>,
            ): Map<String, Any?> =
                if (tool.name in readTools) delegate.afterTool(context, tool, args, result) else result
        }
    }
}
