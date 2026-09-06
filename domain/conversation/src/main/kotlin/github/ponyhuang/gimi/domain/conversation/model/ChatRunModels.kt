package github.ponyhuang.gimi.domain.conversation.model

data class ChatRunEvent(
    val id: String,
    val invocationId: String?,
    val author: String,
    val parts: List<ChatRunPart>,
    val functionCalls: List<ChatFunctionCall>,
    val functionResponses: List<ChatFunctionResponse>,
    val partial: Boolean,
    val turnComplete: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
    val timestamp: Long,
)

data class ChatRunPart(
    val text: String? = null,
    val thought: Boolean = false,
    val attachment: FileAttachment? = null,
)

data class ChatFunctionCall(
    val id: String?,
    val name: String,
    val args: Map<String, Any?>,
    val confirmationRequest: ToolConfirmationRequest? = null,
    val inputRequest: UserInputRequest? = null,
)

/** 挂起的用户输入请求的交互形态。 */
enum class UserInputKind {
    /** 从给定选项中选择一个（ADK `get_user_choice`）。 */
    CHOICE,

    /** 自由文本回复（ADK `adk_request_input`）。 */
    FREE_TEXT,
}

/**
 * Agent 在长时运行工具（`get_user_choice` / `adk_request_input`）上挂起等待的用户输入请求。
 *
 * invocation 在该调用上暂停，宿主渲染对应交互界面收集用户答复后，用同 `callId` 的
 * `FunctionResponse` 恢复运行；恢复时工具不重跑，答复直接作为调用结果进入模型上下文。
 *
 * @property callId 挂起的 ADK function call id，恢复时用于定位请求。
 * @property toolName 触发挂起的工具名（`get_user_choice` 或 `adk_request_input`）。
 * @property kind 交互形态：选项选择或自由文本。
 * @property message 向用户展示的问题；选项选择形态下模型可能只输出选项而无文本，为空串。
 * @property options 选项列表，仅 [UserInputKind.CHOICE] 形态非空。
 */
data class UserInputRequest(
    val callId: String,
    val toolName: String,
    val kind: UserInputKind,
    val message: String,
    val options: List<String> = emptyList(),
)

/**
 * Tool response metadata and any structured local-file result needed by chat presentation.
 *
 * @property id Identifier paired with the originating tool call.
 * @property name Tool function name.
 * @property localFileSearchResult Validated local files for supported search tools.
 * @property remoteImageResult Validated remote images exposed by the tool response.
 */
data class ChatFunctionResponse(
    val id: String?,
    val name: String,
    val localFileSearchResult: LocalFileSearchResult? = null,
    val remoteImageResult: RemoteImageResult? = null,
)

data class ToolConfirmationRequest(
    val toolName: String,
    val args: Map<String, Any?>,
)
