package github.ponyhuang.gimi.data.agent.model

import com.anthropic.core.http.AsyncStreamResponse
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
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
import kotlinx.coroutines.flow.flow

/** 流内只增量发文本/思考；完整聚合保留服务返回的块顺序、工具入参和思考签名。 */
internal fun AsyncStreamResponse<RawMessageStreamEvent>.toClaudeResponses(): Flow<LlmResponse> = flow {
    val accumulator = MessageAccumulator.create()
    var modelVersion: String? = null
    asEventFlow().collect { event ->
        accumulator.accumulate(event)
        event.messageStart().orElse(null)?.let { modelVersion = it.message().model().asString() }
        event.contentBlockStart().orElse(null)?.takeIf { it.contentBlock().isToolUse() }?.let {
            // SDK 聚合器要求出现参数增量；无参函数可能只有 start/stop，补空增量使其还原为 {}。
            accumulator.accumulate(RawMessageStreamEvent.ofContentBlockDelta(
                RawContentBlockDeltaEvent.builder().index(it.index())
                    .delta(RawContentBlockDelta.ofInputJson("")).build(),
            ))
        }
        val delta = event.contentBlockDelta().orElse(null)?.delta()
        val part = when {
            delta?.isThinking() == true -> Part(text = delta.asThinking().thinking(), thought = true)
            delta?.isText() == true -> Part(text = delta.asText().text(), thought = false)
            else -> null
        }
        if (part != null) emit(LlmResponse(
            content = Content(role = Role.MODEL, parts = listOf(part)),
            modelVersion = modelVersion,
            partial = true,
        ))
    }
    emit(accumulator.message().toClaudeLlmResponse())
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
