package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart

object ChatRunEventMapper {
    fun fromEvent(event: ChatRunEvent): Message? {
        val error = event.errorMessage ?: event.errorCode
        if (!error.isNullOrBlank()) {
            return Messages.fromError(
                error = error,
                invocationId = event.invocationId,
                timestamp = event.timestamp,
            ).copy(id = event.id)
        }
        if (event.parts.isEmpty() && event.functionCalls.isEmpty() && event.functionResponses.isEmpty()) {
            return null
        }
        if (event.author == "user" &&
            event.parts.all { it.text.isNullOrEmpty() && it.attachment == null } &&
            event.functionCalls.isEmpty() &&
            event.functionResponses.isNotEmpty()
        ) {
            return null
        }
        if (event.author == "user") {
            val text = event.parts.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }.orEmpty()
            return Message(
                id = event.id,
                invocationId = event.invocationId,
                author = event.author,
                role = MessageRole.User,
                textParts = text.takeIf(String::isNotEmpty)?.let {
                    listOf(TextPart(id = "${event.id}:0", text = it))
                }.orEmpty(),
                fileAttachments = event.parts.mapNotNull { it.attachment },
                turnComplete = true,
                timestamp = event.timestamp,
            )
        }
        var message = Messages.fromAssistant(
            id = event.id,
            invocationId = event.invocationId,
            author = event.author,
            timestamp = event.timestamp,
        )
        event.parts.forEachIndexed { index, part ->
            val text = part.text?.takeIf(String::isNotEmpty) ?: return@forEachIndexed
            val current = message.textParts.toMutableList()
            val last = current.lastOrNull()
            if (last != null && last.thought == part.thought) {
                current[current.lastIndex] = last.copy(text = last.text + text)
            } else {
                current += TextPart("${event.id}:$index", text, part.thought)
            }
            message = message.copy(textParts = current)
        }
        return message.copy(
            functionCalls = event.functionCalls.map(ChatFunctionCall::toView),
            functionResponses = event.functionResponses.map(ChatFunctionResponse::toView),
            partial = event.partial,
            turnComplete = event.turnComplete,
        )
    }
}

fun ChatFunctionCall.toView(): FunctionCallView = FunctionCallView(
    id = id.orEmpty(),
    name = name,
    argsSummary = if (confirmationRequest != null || args.isEmpty()) "" else args.entries.joinToString(
        prefix = "(",
        postfix = ")",
        separator = ", ",
    ) { (key, value) -> "$key=${summarizeValue(value)}" },
)

fun ChatFunctionResponse.toView(): FunctionResponseView =
    FunctionResponseView(id = id.orEmpty(), name = name)

fun summarizeValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> if (value.length > 16) "\"${value.take(15)}…\"" else "\"$value\""
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> "{…}"
    is List<*> -> "[…]"
    else -> value.toString()
}
