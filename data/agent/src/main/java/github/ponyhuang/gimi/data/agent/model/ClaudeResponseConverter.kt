package github.ponyhuang.gimi.data.agent.model

import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.Usage
import com.fasterxml.jackson.core.type.TypeReference
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata

/** 完整响应转换；工具名原样透传，思考签名留给下一轮请求重放。 */
internal fun Message.toClaudeLlmResponse(): LlmResponse = LlmResponse(
    content = Content(
        role = Role.MODEL,
        parts = content().asSequence()
            .mapNotNull { it.toPart() }
            .toList()
    ),
    modelVersion = model().asString(),
    partial = false,
    usageMetadata = usage().toUsageMetadata()
)

private fun Usage.toUsageMetadata() = UsageMetadata(
    promptTokenCount = inputTokens().toInt(),
    candidatesTokenCount = outputTokens().toInt(),
    totalTokenCount = inputTokens().toInt() + outputTokens().toInt()
)

private fun ContentBlock.toPart(): Part? {
    return when {
        isText() -> Part(text = asText().text())
        isToolUse() -> Part(
            functionCall = FunctionCall(
                id = asToolUse().id(),
                name = asToolUse().name(),
                args = asToolUse()._input().convert(
                    object : TypeReference<Map<String, Any?>>() {}
                ) ?: emptyMap()
            )
        )

        isThinking() -> Part(
            text = asThinking().thinking(),
            thought = true,
            thoughtSignature = asThinking().signature().ifEmpty { null }?.toByteArray(Charsets.UTF_8)
        )

        isRedactedThinking() -> Part(
            thought = true,
            thoughtSignature = asRedactedThinking().data().toByteArray(Charsets.UTF_8),
        )

        else -> null
    }
}
