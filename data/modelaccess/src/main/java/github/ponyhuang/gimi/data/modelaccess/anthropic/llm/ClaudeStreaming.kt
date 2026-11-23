package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import com.anthropic.core.JsonValue
import com.anthropic.core.http.AsyncStreamResponse
import com.anthropic.core.jsonMapper
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.Message
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import java.util.Optional
import java.util.concurrent.CompletionException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

private val streamJsonMapper = jsonMapper()

/** 流内只增量发文本/思考；完整聚合保留服务返回的块顺序、工具入参和思考签名。 */
internal fun AsyncStreamResponse<RawMessageStreamEvent>.toClaudeResponses(): Flow<LlmResponse> = flow {
    emit(collectMessagesResponse(this@toClaudeResponses).first.toClaudeLlmResponse())
}

internal suspend fun FlowCollector<LlmResponse>.collectMessagesResponse(
    response: AsyncStreamResponse<RawMessageStreamEvent>,
    webSearch: MessagesWebSearchStream? = null,
    readTitle: (RawMessageStreamEvent) -> String? = { null },
): Pair<Message, String?> {
    webSearch?.beginMessage()
    var title: String? = null
    val accumulator = MessageAccumulator.create()
    val pendingToolInputs = mutableMapOf<Long, JsonValue>()
    var modelVersion: String? = null
    response.asEventFlow().collect { event ->
        event.contentBlockStart().orElse(null)?.let { start ->
            val block = start.contentBlock()
            val input = when {
                block.isToolUse() -> block.asToolUse()._input()
                block.isServerToolUse() -> block.asServerToolUse()._input()
                else -> null
            }
            if (input != null) pendingToolInputs[start.index()] = input
        }
        val deltaEvent = event.contentBlockDelta().orElse(null)
        val delta = deltaEvent?.delta()
        if (deltaEvent != null && delta?.isInputJson() == true && delta.asInputJson().partialJson().isNotBlank()) {
            pendingToolInputs.remove(deltaEvent.index())
        }
        event.contentBlockStop().orElse(null)?.let { stop ->
            pendingToolInputs.remove(stop.index())?.let { input ->
                // SDK 要求工具参数通过增量聚合。若厂商只在 start 发完整参数，则在 stop 前补入原值。
                val json = input.asObject().map(streamJsonMapper::writeValueAsString).orElse("")
                accumulator.accumulate(RawMessageStreamEvent.ofContentBlockDelta(
                    RawContentBlockDeltaEvent.builder().index(stop.index())
                        .delta(RawContentBlockDelta.ofInputJson(json)).build(),
                ))
            }
        }
        accumulator.accumulate(event)
        val citations = webSearch?.accept(event)
        readTitle(event)?.let { title = it }
        event.messageStart().orElse(null)?.let { modelVersion = it.message().model().asString() }
        val part = when {
            delta?.isThinking() == true -> Part(text = delta.asThinking().thinking(), thought = true)
            delta?.isText() == true -> Part(text = delta.asText().text(), thought = false)
            else -> null
        }
        if (part != null || citations != null) emit(LlmResponse(
            content = Content(role = Role.MODEL, parts = listOfNotNull(part)),
            customMetadata = citations,
            modelVersion = modelVersion,
            partial = true,
        ))
    }
    return accumulator.message() to title
}

/** 跟随官方 JVM 的异步回调桥接；收集取消或失败时关闭流，避免阻塞读取拖住停止操作。 */
private fun <T> AsyncStreamResponse<T>.asEventFlow(): Flow<T> = callbackFlow {
    subscribe(object : AsyncStreamResponse.Handler<T> {
        override fun onNext(value: T) {
            trySendBlocking(value)
        }

        override fun onComplete(error: Optional<Throwable>) {
            val failure = error.orElse(null)
            close(if (failure is CompletionException) failure.cause ?: failure else failure)
        }
    })
    awaitClose { this@asEventFlow.close() }
}
