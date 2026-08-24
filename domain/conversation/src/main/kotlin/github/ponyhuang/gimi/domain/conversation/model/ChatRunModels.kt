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
)

/**
 * Tool response metadata and any structured local-file result needed by chat presentation.
 *
 * @property id Identifier paired with the originating tool call.
 * @property name Tool function name.
 * @property localFileSearchResult Validated local files for supported search tools.
 */
data class ChatFunctionResponse(
    val id: String?,
    val name: String,
    val localFileSearchResult: LocalFileSearchResult? = null,
)

data class ToolConfirmationRequest(
    val toolName: String,
    val args: Map<String, Any?>,
)
