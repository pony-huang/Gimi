package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

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
 * DeepSeek Messages 协议适配器；沿用共享 Anthropic 转换链并叠加厂商特例：
 * 可选在首轮请求注入 `title` 参数，标题随响应透传给标题插件（customMetadata）。
 * latest_reminder 由请求转换器按调用方提供的策略注入。
 *
 * 会话标题默认**关闭**（[conversationTitleEnabled] = false）：title 与 DeepSeek 内置
 * 工具保留名互斥，且回答结束后流最多多等 5 秒；适合工具少、重视标题即时性的
 * 轻聊场景按需开启。关闭或注入被跳过时，标题回落标题插件的独立快速模型请求。
 *
 * 仅官方 `https://api.deepseek.com/anthropic` 端点使用本适配器（见
 * [isOfficialDeepSeekAnthropicEndpoint]）；第三方代理回落通用 Claude 适配器。
 *
 * @param client 由调用方配置和管理的 SDK 客户端，支持自定义端点。
 * @param maxTokens 请求未指定输出上限时的默认值，沿用原有 8196；思考预算另行校验。
 * @param conversationTitleEnabled 是否在首轮注入 `title` 参数（默认关闭）。
 * @param requestConverter 可选的上层请求转换策略；默认仅处理通用协议。
 */
class DeepSeekMessageChat(
    override val name: String,
    private val client: AnthropicClient,
    maxTokens: Int = 8196,
    private val conversationTitleEnabled: Boolean = false,
    private val requestConverter: (LlmRequest) -> MessageCreateParams =
        DeepSeekMessageRequestConverter(name, maxTokens)::convert,
) : Model {

    init {
        require(maxTokens > 0) { "DeepSeekMessageChat maxTokens must be positive" }
    }

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        var params = requestConverter(request)
        // 标题只在开启且首轮时注入：回答结束后流最多多等 5 秒，后续轮次不再付出该等待。
        // 请求声明了 DeepSeek 保留工具名（如用户开启官方 web 搜索）时同样跳过，
        // 否则 DeepSeek 以"保留名与 title 互斥"整请求 400。
        if (conversationTitleEnabled && isFirstModelCall(request) && !requestDeclaresReservedTools(request)) {
            params = params.withConversationTitleRequest()
        }
        logger.debug { "DeepSeek Messages request: ${request.contents.size} contents, stream=$stream" }
        val messages = client.async().messages()
        if (stream) {
            emitAll(messages.createStreaming(params).toDeepSeekMessageResponses())
        } else {
            // 与官方 JVM 一致：取消立即停止等待；SDK future 本身不保证取消底层单次 HTTP。
            val message = messages.create(params).await()
            emit(message.toDeepSeekMessageLlmResponse(message.conversationTitle()))
        }
    }.catch { error ->
        // 保留应用现有的错误响应契约；Flow.catch 不吞掉协程取消，参数/转换异常仍向外传播。
        if (error is AnthropicException) emit(error.toDeepSeekMessageErrorResponse()) else throw error
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DeepSeekMessageChat::class)
    }
}

private fun AnthropicException.toDeepSeekMessageErrorResponse() = LlmResponse(
    finishReason = if (this is AnthropicServiceException) FinishReason.SAFETY
    else FinishReason.FINISH_REASON_UNSPECIFIED,
    errorMessage = if (this is AnthropicServiceException) "${statusCode()}: $message"
    else message ?: this::class.simpleName ?: "Unknown error",
)
