package github.ponyhuang.gimi.data.agent.model.files

import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.StreamingResponseAggregator
import com.openai.client.okhttp.OpenAIOkHttpClientAsync
import com.openai.core.jsonMapper
import com.openai.core.http.AsyncStreamResponse
import com.openai.errors.OpenAIException
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import java.io.IOException
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 仅含云文件的 Chat Completions 请求走官方 SDK；普通请求继续使用原生 ADK。
 * 请求／响应与流聚合映射基于 Apache-2.0 的 ADK 1.2.0 源码，扩展 file_id，
 * 保留工具调用、历史、结构化输出、用量和结束原因，不访问 ADK internal 转换器。
 */
internal class FileChatCompletions(
    override val name: String,
    private val apiKey: String,
    private val baseUrl: String,
) : Model {
    @OptIn(FrameworkInternalApi::class)
    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val wire = request.toChatCompletionRequest(name).copy(streamOptions = StreamOptions(true).takeIf { stream })
        val body = jsonMapper().readValue(chatCompletionsJson.encodeToString(wire), ChatCompletionCreateParams.Body::class.java)
        val params = ChatCompletionCreateParams.builder().body(body).build()
        val http = CancellableChatHttpClient()
        val originalClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey.trim()).baseUrl(baseUrl)
            .maxRetries(0).timeout(Duration.ofSeconds(120)).build()
        val client = originalClient.withOptions { it.httpClient(http) }
        try {
            if (stream) {
                val aggregator = StreamingResponseAggregator()
                val state = ChatCompletionsStreamState()
                suspend fun forward(responses: List<LlmResponse>) {
                    responses.forEach { response ->
                        val processed = aggregator.processResponse(response)
                        if (processed.content != null) emit(processed)
                    }
                }
                client.chat().completions().createStreaming(params).asFlow { http.cancelRequests() }.collect { chunk ->
                    forward(state.onChunk(chatCompletionsJson.decodeFromString(jsonMapper().writeValueAsString(chunk))))
                }
                forward(state.finish())
                aggregator.aggregate()?.let { emit(it) }
            } else {
                val completion = client.chat().completions().create(params).awaitCompletion()
                val response = chatCompletionsJson.decodeFromString<ChatCompletionResponse>(jsonMapper().writeValueAsString(completion))
                emit(response.toLlmResponse())
            }
        } finally {
            http.cancelRequests()
            client.close()
            originalClient.close()
        }
    }.catch { failure ->
        when (failure) {
            is CancellationException -> throw failure
            // SDK 异常可能携带请求或响应原文；只向业务层报告类型，不携带 cause。
            is com.openai.errors.OpenAIServiceException -> throw IllegalStateException("Cloud file Chat Completions request failed (HTTP ${failure.statusCode()})")
            is OpenAIException, is IOException -> throw IllegalStateException("Cloud file Chat Completions request failed")
            is com.fasterxml.jackson.core.JacksonException,
            is kotlinx.serialization.SerializationException -> throw IllegalStateException("Invalid cloud file Chat Completions response")
            else -> throw failure
        }
    }.flowOn(Dispatchers.IO)
}

private fun AsyncStreamResponse<ChatCompletionChunk>.asFlow(cancelRequest: () -> Unit): Flow<ChatCompletionChunk> = callbackFlow {
    val response = this@asFlow
    response.subscribe(object : AsyncStreamResponse.Handler<ChatCompletionChunk> {
        override fun onNext(value: ChatCompletionChunk) {
            // 阻塞 SDK 回调形成背压，不丢流片段；取消会关闭 channel 并解除阻塞。
            trySendBlocking(value)
        }
        override fun onComplete(error: Optional<Throwable>) { close(error.orElse(null)) }
    })
    awaitClose {
        // SDK 的 close 可能等待正在读取的响应体；先取消 socket，再关闭解析流。
        cancelRequest()
        response.close()
    }
}

private suspend fun <T> CompletableFuture<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(true) }
    whenComplete { value, error ->
        if (error == null) continuation.resume(value)
        else continuation.resumeWithException(if (error is CompletionException) error.cause ?: error else error)
    }
}
