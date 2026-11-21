package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.modelaccess.DEFERRED_CATALOG

/**
 * 首轮模型调用判定：请求历史里没有任何模型回复。
 *
 * 工具循环轮次天然含 MODEL 内容，不会误判；子任务单轮调用同样视为首轮，
 * 代价仅是标题参数（不计费）与最多 5 秒的流尾等待。
 */
internal fun isFirstModelCall(request: LlmRequest): Boolean =
    request.contents.none { it.role == Role.MODEL }

/**
 * 请求是否声明了 DeepSeek 保留工具名。
 *
 * DeepSeek 的保留名（`deepseek_web_search` / `deepseek_read_url` / `safety_check`）
 * 在请求使用了同名内置能力或 `title` 参数后即被占用：此时再声明同名**自定义**工具，
 * 整请求 400：
 * `tools[0]: the name 'deepseek_web_search' is reserved when 'deepseek_web_search',
 * 'deepseek_read_url', 'safety_check' or 'title' is used`。
 *
 * 内置工具自带同名声明不受影响（官方示例即内置工具 + title 共存）；被拒的是同名自定义
 * 工具。命中时跳过 title 注入，由标题插件的独立请求兜底。
 */
@OptIn(FrameworkInternalApi::class)
internal fun requestDeclaresReservedTools(request: LlmRequest): Boolean {
    val active = request.config.tools.orEmpty()
        .flatMap { it.functionDeclarations.orEmpty() }.map { it.name }
    val deferred = request.config.systemInstruction?.parts.orEmpty().flatMap { part ->
        (part.partMetadata?.get(DEFERRED_CATALOG) as? List<*>).orEmpty().filterIsInstance<String>()
    }.mapNotNull { snapshot ->
        runCatching { adkJson.decodeFromString(FunctionDeclaration.serializer(), snapshot).name }.getOrNull()
    }
    return (active + deferred).any { it in RESERVED_TOOL_NAMES }
}

private val RESERVED_TOOL_NAMES = setOf("deepseek_web_search", "deepseek_read_url", "safety_check")

/** 首轮请求注入 `title` 扩展参数；DeepSeek 会在回答顺带生成标题并随响应返回（不计费）。 */
internal fun MessageCreateParams.withConversationTitleRequest(): MessageCreateParams =
    toBuilder().putAdditionalBodyProperty("title", JsonValue.from(mapOf("enabled" to true))).build()

/** 流式：最后一个 message_delta 事件的 `delta.title` 扩展字段。 */
internal fun RawMessageStreamEvent.conversationTitle(): String? =
    messageDelta().orElse(null)?.delta()?._additionalProperties()?.get("title")
        ?.asString()?.orElse(null)?.takeIf(String::isNotBlank)

/** 非流式：响应顶层的 `title` 扩展字段。 */
internal fun Message.conversationTitle(): String? =
    _additionalProperties()["title"]?.asString()?.orElse(null)?.takeIf(String::isNotBlank)
