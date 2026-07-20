package github.ponyhuang.asssistantai.domain.conversation.model

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
    val image: ImageAttachment? = null,
)

data class ChatFunctionCall(
    val id: String?,
    val name: String,
    val args: Map<String, Any?>,
    val confirmationRequest: ToolConfirmationRequest? = null,
)

data class ChatFunctionResponse(
    val id: String?,
    val name: String,
)

data class ToolConfirmationRequest(
    val toolName: String,
    val args: Map<String, Any?>,
)
