/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm

import github.ponyhuang.gimi.domain.conversation.model.webCitationUtf16Offset
import github.ponyhuang.gimi.domain.conversation.model.webCitationHost
import github.ponyhuang.gimi.domain.conversation.model.WebSource
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.serialization.Json
import com.google.adk.kt.tools.toJsonSchema
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionCallingConfigMode
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Tool
import com.google.adk.kt.types.ToolConfig
import com.google.adk.kt.types.Type
import com.google.adk.kt.types.UsageMetadata
import github.ponyhuang.gimi.domain.conversation.model.VendorFileReference
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private val logger = LoggerFactory.getLogger(OpenAiCompatibleChatCompletions::class)

// Each is logged once per process so a long session does not repeat it on every call.
private val warnedSampling = AtomicBoolean()
private val warnedTools = AtomicBoolean()
private val warnedThinking = AtomicBoolean()

/**
 * Matches OpenAI reasoning ("o-series") model names, which take a `developer` role, not `system`.
 */
private val O_SERIES_MODEL = Regex("(?:^|/)o\\d", RegexOption.IGNORE_CASE)

/**
 * Matches OpenAI reasoning models: the o-series and gpt-5 or any later major version, unlike the
 * Python ADK, which lists only gpt-5 and gpt-6. It skips the `-chat` variants and Azure's `gpt-35`
 * names for GPT-3.5. These models need `max_completion_tokens` and accept only default sampling.
 */
private val REASONING_MODEL =
  Regex(
    "(?:^|/)(?:o\\d+|gpt-(?!35(?:-|$))(?:[5-9]|[1-9]\\d+)(?!.*-chat))(?:\\..*|-.*|$)",
    RegexOption.IGNORE_CASE,
  )

/** The name pattern the API requires of a `json_schema` response format. */
private val SCHEMA_NAME = Regex("[a-zA-Z0-9_-]{1,64}")

/** The keys a tool's parameter schema always has, which a declared schema extends. */
private val EMPTY_OBJECT_SCHEMA =
  JsonObject(mapOf("type" to JsonPrimitive("object"), "properties" to JsonObject(emptyMap())))

/**
 * Builds the Chat Completions request from an ADK [LlmRequest]. The client owns the stream flags.
 */
internal fun LlmRequest.toChatCompletionRequest(
  modelName: String,
  audioEncoding: InputAudioDataEncoding = InputAudioDataEncoding.OPENAI_BASE64,
): ChatCompletionRequest {
  val toolCallIds = ToolCallIds()
  val systemRole = systemRoleFor(modelName)
  val messages = buildList {
    config.systemInstruction?.textContent()?.let {
      add(ChatMessage(role = systemRole, content = it))
    }
    for (content in contents) addAll(content.toChatCompletionsMessages(toolCallIds, systemRole, audioEncoding))
  }
  val tools =
    config.tools
      .orEmpty()
      .flatMap { it.functionDeclarations.orEmpty() }
      .map { it.toChatTool() }
      .ifEmpty { null }
  if (config.tools.orEmpty().any { it.copy(functionDeclarations = null) != Tool() }) {
    warnOnce(warnedTools) { "Chat Completions sends only function tools; dropping the others." }
  }
  // As in Python, only a thinking level or budget asks for something this backend cannot send.
  val thinking = config.thinkingConfig
  if (thinking?.thinkingLevel != null || thinking?.thinkingBudget != null) {
    warnOnce(warnedThinking) { "Chat Completions does not send a thinking level or budget." }
  }
  val reasoning = REASONING_MODEL.containsMatchIn(modelName)
  return ChatCompletionRequest(
    model = modelName,
    messages = messages,
    tools = tools,
    toolChoice = if (tools != null) config.toolConfig.toToolChoice() else null,
    responseFormat = config.toResponseFormat(),
    temperature = config.temperature.unlessRejected(reasoning),
    topP = config.topP.unlessRejected(reasoning),
    // As in Python: reasoning models reject `max_tokens`, which other servers expect.
    maxTokens = config.maxOutputTokens.takeUnless { reasoning },
    maxCompletionTokens = config.maxOutputTokens.takeIf { reasoning },
    stop = config.stopSequences?.ifEmpty { null },
    presencePenalty = config.presencePenalty.unlessRejected(reasoning, default = 0f),
    frequencyPenalty = config.frequencyPenalty.unlessRejected(reasoning, default = 0f),
    seed = config.seed,
  )
}

private fun warnOnce(warned: AtomicBoolean, message: () -> String) {
  if (warned.compareAndSet(false, true)) logger.warn { message() }
}

private fun systemRoleFor(model: String): String =
  if (O_SERIES_MODEL.containsMatchIn(model)) "developer" else "system"

/** Drops a sampling value other than its [default], which a reasoning model rejects. */
private fun Float?.unlessRejected(reasoning: Boolean, default: Float = 1f): Float? {
  if (this == null || !reasoning || this == default) return this
  warnOnce(warnedSampling) {
    "Reasoning models accept only default sampling values; dropping others."
  }
  return null
}

/**
 * Maps the function-calling mode as the Python ADK does; allowed function names are not applied.
 */
private fun ToolConfig?.toToolChoice(): String =
  when (this?.functionCallingConfig?.mode) {
    FunctionCallingConfigMode.ANY -> "required"
    FunctionCallingConfigMode.NONE -> "none"
    else -> "auto"
  }

/**
 * Maps an output schema to a strict `json_schema` format, or a JSON mime type without one to
 * `json_object`, as the Python ADK does.
 */
@OptIn(FrameworkInternalApi::class)
private fun GenerateContentConfig.toResponseFormat(): ChatResponseFormat? {
  val schema = responseSchema
  return when {
    schema != null ->
      ChatResponseFormat(
        type = "json_schema",
        jsonSchema =
          ChatJsonSchema(
            name = schema.title?.takeIf { SCHEMA_NAME.matches(it) } ?: "response",
            schema = schema.toJsonSchema().toStrictSchema(),
            strict = true,
          ),
      )
    responseMimeType == "application/json" -> ChatResponseFormat(type = "json_object")
    else -> null
  }
}

/**
 * Adapts a JSON Schema for strict structured output as the Python ADK does: each object requires
 * all its properties and forbids others. OpenAPI `nullable` becomes a `null` type, the only way a
 * strict schema can leave a value out.
 */
private fun JsonObject.toStrictSchema(): JsonObject {
  val schema = toMutableMap()
  (schema["properties"] as? JsonObject)?.let { properties ->
    schema["properties"] = JsonObject(properties.mapValues { it.value.toStrictSchema() })
    schema["required"] = JsonArray(properties.keys.map { JsonPrimitive(it) })
    schema["additionalProperties"] = JsonPrimitive(false)
  }
  schema["items"]?.let { schema["items"] = it.toStrictSchema() }
  (schema["anyOf"] as? JsonArray)?.let { members ->
    schema["anyOf"] = JsonArray(members.map { it.toStrictSchema() })
  }
  if ((schema.remove("nullable") as? JsonPrimitive)?.booleanOrNull == true) {
    val type = schema["type"]
    val anyOf = schema["anyOf"] as? JsonArray
    when {
      type != null -> schema["type"] = JsonArray(listOf(type, JsonPrimitive("null")))
      anyOf != null ->
        schema["anyOf"] = JsonArray(anyOf + JsonObject(mapOf("type" to JsonPrimitive("null"))))
    }
    (schema["enum"] as? JsonArray)?.let { schema["enum"] = JsonArray(it + JsonNull) }
  }
  return JsonObject(schema)
}

private fun JsonElement.toStrictSchema(): JsonElement =
  (this as? JsonObject)?.toStrictSchema() ?: this

/** `input_audio.data` 的线格式：OpenAI 契约用裸 base64 + format，MiMo 要求 data-URI。 */
enum class InputAudioDataEncoding { OPENAI_BASE64, MIMO_DATA_URI }

/**
 * Converts an ADK [Content] to Chat Completions messages. A turn's function responses each become a
 * standalone `tool` message; its text and images fold into one `user` or [systemRole] message, and
 * its text and function calls into one `assistant` message.
 */
private fun Content.toChatCompletionsMessages(
  toolCallIds: ToolCallIds,
  systemRole: String,
  audioEncoding: InputAudioDataEncoding,
): List<ChatMessage> {
  val chatRole =
    when {
      role.equals(Role.MODEL, ignoreCase = true) || role.equals("assistant", ignoreCase = true) ->
        "assistant"
      role.equals(Role.SYSTEM, ignoreCase = true) -> systemRole
      else -> "user"
    }
  val isAssistant = chatRole == "assistant"
  val textOnly = chatRole != "user"
  // 本 fork 不抛异常：用户回合里不支持的媒体段（音频、本地文档等）在 userContent 里
  // 降级为占位文本，历史重放中一处坏附件不能让整轮请求失败。
  val messages = mutableListOf<ChatMessage>()
  parts
    .mapNotNull { it.functionResponse }
    .forEach { response ->
      messages +=
        ChatMessage(
          role = "tool",
          toolCallId = toolCallIds.forResult(response.id, response.name),
          content = JsonPrimitive(response.response.toToolResultText()),
        )
    }
  val content = if (textOnly) textContent() else userContent(audioEncoding)
  // Only an assistant message may carry tool calls; Python drops them from other turns too.
  val toolCalls =
    parts
      .mapNotNull { part ->
        val call = part.functionCall?.takeIf { isAssistant } ?: return@mapNotNull null
        ChatToolCall(
          id = toolCallIds.forCall(call.id, call.name),
          type = "function",
          function = ChatFunctionCall(name = call.name, arguments = Json.toJsonString(call.args)),
          extraContent = part.thoughtSignature?.toExtraContent(),
        )
      }
      .ifEmpty { null }
  if (content != null || toolCalls != null) {
    messages += ChatMessage(role = chatRole, content = content, toolCalls = toolCalls)
  }
  return messages
}

/** A part's text, or code execution from another model as text, as the Claude backend sends it. */
private fun Part.chatText(): String? =
  text?.ifEmpty { null }
    ?: executableCode?.let { "Code:```python\n${it.code.orEmpty()}\n```" }
    ?: codeExecutionResult?.let { "Execution Result:```code_output\n${it.output.orEmpty()}\n```" }

/** The non-thought parts' text, code execution included, joined by newlines, or null if none. */
private fun Content.textContent(): JsonPrimitive? =
  parts
    .filter { it.thought != true }
    .mapNotNull { it.chatText() }
    .ifEmpty { null }
    ?.let { JsonPrimitive(it.joinToString("\n")) }

/**
 * A user turn's content: a string when it is only text, otherwise a list of text, image, and
 * vendor-file blocks.
 *
 * 厂商 file 引用（gimi-file://，见 VendorFileReference）直通转换为 `file` 块；其余不支持的
 * 媒体按占位文本降级而不是抛异常，保证历史重放的容错性。
 */
private fun Content.userContent(audioEncoding: InputAudioDataEncoding): JsonElement? {
  val visible = parts.filter { it.thought != true }
  // 任何媒体段（含不支持的音频/本地文档）都要求走块数组，让占位降级生效；
  // 纯文本回合仍折叠为字符串。
  val hasMedia = visible.any { it.inlineData != null || it.fileData != null }
  if (!hasMedia) return textContent()
  return JsonArray(
    visible.mapNotNull { part ->
      part.chatText()?.let(::textBlock)
        ?: part.vendorFileId()?.let(::fileBlock)
        ?: part.audioBlock(audioEncoding)
        ?: part.imageUrl()?.let(::imageUrlBlock)
        ?: unsupportedMediaPlaceholder(part)?.let(::textBlock)
    }
  )
}

private fun textBlock(text: String): JsonObject =
  JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive(text)))

/** OpenAI 兼容协议族的 `file` 内容块；fileId 由上传链路在发送前换取，重放直接复用。 */
private fun fileBlock(fileId: String): JsonObject =
  JsonObject(
    mapOf(
      "type" to JsonPrimitive("file"),
      "file_id" to JsonPrimitive(fileId),
    )
  )

private fun imageUrlBlock(url: String): JsonObject =
  JsonObject(
    mapOf(
      "type" to JsonPrimitive("image_url"),
      "image_url" to JsonObject(mapOf("url" to JsonPrimitive(url))),
    )
  )

private fun Part.audioBlock(audioEncoding: InputAudioDataEncoding): JsonObject? {
  val (bytes, mime) = when {
    inlineData?.data != null && inlineData?.audioMimeType() != null ->
      inlineData!!.data!! to inlineData!!.audioMimeType()!!
    fileData?.fileUri != null && fileData?.mimeType?.startsWith("audio/") == true -> {
      val uri = fileData!!.fileUri!!
      val file = java.io.File(uri)
      if (file.exists()) file.readBytes() to fileData!!.mimeType!! else null
    }
    else -> null
  } ?: return null

  val format = when (mime.lowercase()) {
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/mp3", "audio/mpeg" -> "mp3"
    else -> "wav"
  }
  val base64 = Base64.getEncoder().encodeToString(bytes)
  // OpenAI 兼容协议族要求裸 base64 + 独立 format；MiMo 是唯一要求 data-URI 的厂商，
  // 由工厂按 templateId 显式传入，不能让其它后端收到服务端不接受的前缀。
  val data = if (audioEncoding == InputAudioDataEncoding.MIMO_DATA_URI) {
    "data:$mime;base64,$base64"
  } else {
    base64
  }
  return JsonObject(
    mapOf(
      "type" to JsonPrimitive("input_audio"),
      "input_audio" to JsonObject(
        mapOf(
          "data" to JsonPrimitive(data),
          "format" to JsonPrimitive(format),
        )
      )
    )
  )
}

private fun Blob.audioMimeType(): String? =
  mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.startsWith("audio/") }


/** `gimi-file://` 私有引用的 fileId；本地路径等其他 fileData 形态返回 null。 */
private fun Part.vendorFileId(): String? =
  fileData?.fileUri?.let(VendorFileReference::parse)?.second

/** 不支持媒体的占位文本；既无名字也无 mime 的静默段返回 null（丢弃该段）。 */
private fun unsupportedMediaPlaceholder(part: Part): String? {
  val name = part.inlineData?.displayName ?: part.fileData?.displayName
  val mime = part.inlineData?.mimeType ?: part.fileData?.mimeType
  if (name == null && mime == null) return null
  return "[附件 ${name ?: mime} 不受当前模型支持，未包含在本次请求中]"
}

private fun Part.isImage(): Boolean {
  val blob = inlineData ?: return fileData?.isRemoteImage() == true
  return blob.data != null && blob.imageMimeType() != null
}

/** Inline image bytes as a base64 `data:` URL, or a remote image file's own URL; else null. */
private fun Part.imageUrl(): String? {
  val blob = inlineData ?: return fileData?.takeIf { it.isRemoteImage() }?.fileUri
  val mimeType = blob.imageMimeType() ?: return null
  val data = blob.data ?: return null
  return "data:$mimeType;base64,${Base64.getEncoder().encodeToString(data)}"
}

private fun Blob.imageMimeType(): String? =
  mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.startsWith("image/") }

/**
 * An image file a server can fetch: http(s) with an image or no mime type, as Python sends it, or
 * `gs://` with an image mime type, which Vertex AI reads.
 */
private fun FileData.isRemoteImage(): Boolean {
  val uri = fileUri ?: return false
  val mime = mimeType?.lowercase()
  return when {
    uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true) ->
      mime == null || mime.startsWith("image/")
    uri.startsWith("gs://", ignoreCase = true) -> mime?.startsWith("image/") == true
    else -> false
  }
}

/**
 * Pairs tool calls with their results when history ids are missing, such as a call made by another
 * model, so each `tool` message still names its call. One instance covers one request's history.
 */
private class ToolCallIds {
  private val pendingByName = mutableMapOf<String, ArrayDeque<String>>()
  private var nextFallback = 0

  fun forCall(id: String?, name: String): String {
    if (!id.isNullOrEmpty()) return id
    val fallback = "call_fallback_${nextFallback++}"
    pendingByName.getOrPut(name) { ArrayDeque() }.addLast(fallback)
    return fallback
  }

  fun forResult(id: String?, name: String): String =
    id?.ifEmpty { null } ?: pendingByName[name]?.removeFirstOrNull() ?: name
}

/**
 * Renders a tool result map as a string. ADK wraps a non-map tool return under a single `result`
 * key (see BaseTool), so that wrapper is unwrapped to send the bare value; anything else is JSON.
 */
private fun Map<String, Any?>.toToolResultText(): String {
  if (keys != setOf("result")) return Json.toJsonString(this)
  val result = this["result"]
  return if (result is String) result else Json.toJsonString(result)
}

@OptIn(FrameworkInternalApi::class)
private fun FunctionDeclaration.toChatTool(): ChatTool =
  ChatTool(
    function =
      ChatFunctionDef(
        name = name,
        description = description,
        parameters =
          JsonObject(EMPTY_OBJECT_SCHEMA + parameters?.withObjectType()?.toJsonSchema().orEmpty()),
      )
  )

/**
 * This parameter schema typed as an object, as Python always sends it; `toJsonSchema` keeps
 * `properties` only for an object and types an untyped schema as a string.
 */
private fun Schema.withObjectType(): Schema =
  if (type == Type.OBJECT) this else copy(type = Type.OBJECT)

/** Converts a non-streaming Chat Completions response to an ADK [LlmResponse]. */
internal fun ChatCompletionResponse.toLlmResponse(includeWebCitations: Boolean = false): LlmResponse {
  // Some compatible servers return no choices, such as for a filtered request; Python reports it.
  val choice =
    choices.firstOrNull()
      ?: return LlmResponse(
        usageMetadata = usage?.toUsageMetadata(),
        finishReason = FinishReason.OTHER,
        errorCode = FinishReason.OTHER.name,
        errorMessage = "Chat Completions response contained no choices",
        modelVersion = model,
      )
  val message = choice.message
  val calls = message?.toolCalls.orEmpty().map { it.toFunctionCallPart() }
  val parts = buildList {
    message?.contentText()?.takeIf { it.isNotEmpty() }?.let { add(Part(text = it)) }
    addAll(calls.filterNotNull())
  }
  val finishReason = choice.finishReason.toFinishReason(droppedMalformedCall = null in calls)
  val hasUsableTruncatedSearchReply = includeWebCitations && choice.finishReason == "length" && parts.isNotEmpty()
  return LlmResponse(
    content = if (parts.isEmpty()) null else Content(role = Role.MODEL, parts = parts),
    usageMetadata = usage?.toUsageMetadata(),
    finishReason = finishReason,
    errorCode = finishReason?.takeIf { it != FinishReason.STOP && !hasUsableTruncatedSearchReply }?.name,
    errorMessage = if (hasUsableTruncatedSearchReply) null else finishErrorMessage(finishReason, choice.finishReason),
    modelVersion = model,
    customMetadata = if (includeWebCitations) message?.webCitations()?.takeIf { it.isNotEmpty() }
      ?.let { mapOf(WebCitationMetadata.KEY to WebCitationMetadata.encode(it)) } else null,
  )
}

private fun ChatMessage.webCitations(): List<WebCitation> {
  val text = contentText().orEmpty()
  return (annotations as? JsonArray).orEmpty().mapNotNull { entry ->
    val annotation = entry as? JsonObject ?: return@mapNotNull null
    if ((annotation["type"] as? JsonPrimitive)?.content != "url_citation") return@mapNotNull null
    val citation = annotation["url_citation"] as? JsonObject ?: return@mapNotNull null
    val url = (citation["url"] as? JsonPrimitive)?.content
      ?.takeIf { webCitationHost(it) != null } ?: return@mapNotNull null
    val end = (citation["end_index"] as? JsonPrimitive)?.content?.toIntOrNull()
      ?.let { webCitationUtf16Offset(text, it) }?.takeIf { it > 0 } ?: return@mapNotNull null
    val title = (citation["title"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
    WebCitation(WebSource(url, title), end)
  }.sortedBy(WebCitation::endOffset).distinct()
}

private fun ChatToolCall.toFunctionCallPart(): Part? =
  functionCallPart(function?.name, function?.arguments, id, extraContent.thoughtSignature())

/**
 * A tool call as a part, or null when it has no name or malformed or truncated arguments, so the
 * caller reports it as malformed instead of running it.
 */
internal fun functionCallPart(
  name: String?,
  arguments: String?,
  id: String?,
  thoughtSignature: ByteArray?,
): Part? {
  if (name.isNullOrEmpty()) return null
  val args = parseToolArguments(arguments) ?: return null
  return Part(
    functionCall = FunctionCall(name = name, args = args, id = id?.ifEmpty { null }),
    thoughtSignature = thoughtSignature,
  )
}

private fun ByteArray.toExtraContent(): ChatExtraContent =
  ChatExtraContent(GoogleExtraContent(Base64.getEncoder().encodeToString(this)))

/** The decoded Gemini thought signature, or null when absent or not base64. */
internal fun ChatExtraContent?.thoughtSignature(): ByteArray? =
  this?.google?.thoughtSignature?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

/**
 * Parses a tool call's JSON-string arguments, or returns null when they are malformed or truncated.
 * Empty or literal `null` arguments mean the call takes none.
 */
private fun parseToolArguments(arguments: String?): Map<String, Any?>? =
  if (arguments.isNullOrBlank() || arguments.trim() == "null") {
    emptyMap()
  } else {
    runCatching { Json.fromJsonToMap(arguments) }.getOrNull()
  }

/**
 * Maps Chat Completions token counts to the genai-shaped [UsageMetadata], as the Python ADK does.
 * Providers differ on whether `completion_tokens` includes the reasoning tokens, so neither count
 * is adjusted.
 */
internal fun ChatUsage.toUsageMetadata(): UsageMetadata =
  UsageMetadata(
    promptTokenCount = promptTokens,
    candidatesTokenCount = completionTokens,
    totalTokenCount = totalTokens,
    thoughtsTokenCount = completionTokensDetails?.reasoningTokens,
    cachedContentTokenCount = promptTokensDetails?.cachedTokens,
  )

/**
 * Maps a Chat Completions `finish_reason` to an ADK [FinishReason] as the Python ADK does: an
 * absent or empty one gives null and an unrecognized one FINISH_REASON_UNSPECIFIED. A dropped
 * malformed tool call turns any of these, or STOP, into MALFORMED_FUNCTION_CALL.
 */
internal fun String?.toFinishReason(droppedMalformedCall: Boolean = false): FinishReason? {
  val reason =
    when (this) {
      null,
      "" -> null
      "stop",
      "tool_calls",
      "function_call" -> FinishReason.STOP
      "length" -> FinishReason.MAX_TOKENS
      "content_filter" -> FinishReason.SAFETY
      else -> FinishReason.FINISH_REASON_UNSPECIFIED
    }
  val overridable =
    reason == null ||
      reason == FinishReason.STOP ||
      reason == FinishReason.FINISH_REASON_UNSPECIFIED
  return if (droppedMalformedCall && overridable) FinishReason.MALFORMED_FUNCTION_CALL else reason
}

/** The error message for a finish other than a clean stop; [reason] is the provider's own value. */
internal fun finishErrorMessage(finishReason: FinishReason?, reason: String?): String? =
  when (finishReason) {
    null,
    FinishReason.STOP -> null
    FinishReason.MALFORMED_FUNCTION_CALL -> "Chat Completions returned a malformed tool call"
    else -> "Chat Completions finish reason: ${reason ?: finishReason}"
  }
