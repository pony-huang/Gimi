package github.ponyhuang.gimi.data.agent.conversation

import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest

/**
 * 用户输入类长时运行工具（ADK `RequestInputTool` / `GetUserChoiceTool`）的协议细节。
 *
 * 两个工具的 `run()` 返回 `Unit` 触发 invocation 暂停，宿主收集用户答复后以同 id 的
 * `FunctionResponse` 恢复运行；恢复时工具不重跑，响应负载直接进入模型上下文。
 * ADK 对响应负载的键没有约定，这里固化一套键名作为本应用的协议：
 * `get_user_choice` → `{"choice": <选项>}`，`adk_request_input` → `{"input": <文本>}`。
 */
internal object UserInputToolProtocol {

    /** ADK `RequestInputTool` 的 function call 名（字面量复制，data 层外不可见）。 */
    const val REQUEST_INPUT_NAME: String = "adk_request_input"

    /** ADK `GetUserChoiceTool` 的 function call 名。 */
    const val GET_USER_CHOICE_NAME: String = "get_user_choice"

    /**
     * 从 function call 参数解析挂起的用户输入请求。
     *
     * 非用户输入类工具、缺 call id、或必填参数（`message` / `options`）整体缺失时返回 null，
     * 不为畸形调用渲染空卡片。参数存在但形状异常（如 options 非字符串列表）时按能提取的
     * 内容降级解析，不让一次畸形调用把整个事件流打断。
     */
    fun parseRequest(callId: String?, name: String, args: Map<String, Any?>): UserInputRequest? {
        if (callId.isNullOrEmpty()) return null
        return when (name) {
            REQUEST_INPUT_NAME -> {
                if (!args.containsKey("message")) return null
                UserInputRequest(
                    callId = callId,
                    toolName = name,
                    kind = UserInputKind.FREE_TEXT,
                    message = args["message"] as? String ?: "",
                )
            }
            GET_USER_CHOICE_NAME -> {
                if (!args.containsKey("options")) return null
                val options = (args["options"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                UserInputRequest(
                    callId = callId,
                    toolName = name,
                    kind = UserInputKind.CHOICE,
                    message = "",
                    options = options,
                )
            }
            else -> null
        }
    }

    /** 用户答复 → `FunctionResponse.response` 负载；键约定见 [UserInputToolProtocol]。 */
    fun responsePayload(toolName: String, value: String): Map<String, Any?> = when (toolName) {
        GET_USER_CHOICE_NAME -> mapOf("choice" to value)
        else -> mapOf("input" to value)
    }
}
