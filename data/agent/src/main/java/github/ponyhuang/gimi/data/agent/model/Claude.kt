package github.ponyhuang.gimi.data.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.FinishReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await

/**
 * Android 的 Anthropic 协议适配器；借鉴官方 JVM 将请求转换、思考策略和流式传输独立维护。
 *
 * @param client 由调用方配置和管理的 SDK 客户端，支持自定义端点。
 * @param maxTokens 请求未指定输出上限时的默认值，沿用原有 8196；思考预算另行校验。
 * @param requestConverter 可选的上层请求转换策略；默认仅处理通用 Anthropic 协议。
 */
class Claude(
    override val name: String,
    private val client: AnthropicClient,
    maxTokens: Int = 8196,
    private val requestConverter: (LlmRequest) -> MessageCreateParams = ClaudeRequestConverter(name, maxTokens)::convert,
) : Model {

    init {
        require(maxTokens > 0) { "Claude maxTokens must be positive" }
    }

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val params = requestConverter(request)
        logger.debug { "Claude request: ${request.contents.size} contents, stream=$stream" }
        val messages = client.async().messages()
        if (stream) {
            emitAll(messages.createStreaming(params).toClaudeResponses())
        } else {
            // 与官方 JVM 一致：取消立即停止等待；SDK future 本身不保证取消底层单次 HTTP。
            emit(messages.create(params).await().toClaudeLlmResponse())
        }
    }.catch { error ->
        // 保留应用现有的错误响应契约；Flow.catch 不吞掉协程取消，参数/转换异常仍向外传播。
        if (error is AnthropicException) emit(error.toClaudeErrorResponse()) else throw error
    }

    private companion object {
        val logger = LoggerFactory.getLogger(Claude::class)
    }
}

private fun AnthropicException.toClaudeErrorResponse() = LlmResponse(
    finishReason = if (this is AnthropicServiceException) FinishReason.SAFETY
    else FinishReason.FINISH_REASON_UNSPECIFIED,
    errorMessage = if (this is AnthropicServiceException) "${statusCode()}: $message"
    else message ?: this::class.simpleName ?: "Unknown error",
)
