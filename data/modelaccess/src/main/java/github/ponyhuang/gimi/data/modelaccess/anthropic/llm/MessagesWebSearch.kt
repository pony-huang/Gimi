package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.RawMessageStreamEvent
import com.fasterxml.jackson.databind.JsonNode
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import github.ponyhuang.gimi.domain.conversation.model.WebSource
import github.ponyhuang.gimi.domain.conversation.model.webCitationHost

private const val REPLAY_SCOPE = "gimi.messages_web_scope.v1"
private const val REPLAY_BLOCKS = "gimi.messages_web_blocks.v1"
private val mapper = jsonMapper()

private fun JsonNode.field(name: String): String? =
    path(name).takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)

private fun JsonNode.webSource(): WebSource? {
    if (field("type") != "web_search_result_location") return null
    val url = field("url")?.takeIf { webCitationHost(it) != null } ?: return null
    return WebSource(url, field("title"))
}

/** 按内容块索引收集引用，位置随正文增量确定，不依赖引用文本的语言或是否重复。 */
internal class MessagesWebSearchStream {
    private var textLength = 0
    private val blockEnds = mutableMapOf<Long, Int>()
    private val citations = linkedSetOf<WebCitation>()
    private val icons = mutableMapOf<String, String>()

    fun beginMessage() { blockEnds.clear() }

    fun accept(event: RawMessageStreamEvent): Map<String, Any>? {
        var changed = false
        event.contentBlockStart().orElse(null)?.let { start ->
            val block = mapper.valueToTree<JsonNode>(start.contentBlock())
            readIcons(block)
            if (block.field("type") == "text") {
                textLength += block.path("text").asText().length
                blockEnds[start.index()] = textLength
                block.path("citations").forEach { changed = add(it, textLength) || changed }
            }
        }
        event.contentBlockDelta().orElse(null)?.let { eventDelta ->
            val delta = eventDelta.delta()
            if (delta.isText()) {
                textLength += delta.asText().text().length
                blockEnds[eventDelta.index()] = textLength
            }
            delta.citations().orElse(null)?.let citation@{ citationDelta ->
                val end = blockEnds[eventDelta.index()] ?: return@citation
                changed = add(mapper.valueToTree<JsonNode>(citationDelta).path("citation"), end)
            }
        }
        return if (changed) citationMetadata(citations.map(::withIcon)) else null
    }

    fun complete(blocks: List<ContentBlock>): List<WebCitation> {
        var end = 0
        val raw = blocks.map { mapper.valueToTree<JsonNode>(it) }
        raw.forEach(::readIcons)
        for (block in raw) {
            if (block.field("type") != "text") continue
            val start = end
            end += block.path("text").asText().length
            for (citation in block.path("citations")) {
                val source = citation.webSource() ?: continue
                // 聚合响应把块内引用集中在末尾。流式路径保留已采集的精确位置。
                if (citations.none { it.source.url == source.url && it.endOffset in (start + 1)..end }) add(citation, end)
            }
        }
        return citations.map(::withIcon).sortedBy(WebCitation::endOffset).distinct()
    }

    private fun add(source: JsonNode, end: Int): Boolean =
        end > 0 && source.webSource()?.let { citations.add(WebCitation(it, end)) } == true

    private fun readIcons(block: JsonNode) {
        if (block.field("type") != "web_search_tool_result") return
        block.path("content").forEach { result ->
            val url = result.field("url") ?: return@forEach
            result.field("site_icon")?.takeIf { webCitationHost(it) != null }?.let { icons[url] = it }
        }
    }

    private fun withIcon(citation: WebCitation): WebCitation =
        citation.copy(source = citation.source.copy(iconUrl = icons[citation.source.url]))
}

private fun citationMetadata(citations: List<WebCitation>): Map<String, Any>? =
    citations.takeIf { it.isNotEmpty() }?.let { mapOf(WebCitationMetadata.KEY to WebCitationMetadata.encode(it)) }

internal fun LlmResponse.withMessagesWebSearch(
    message: Message,
    replayScope: String?,
    stream: MessagesWebSearchStream = MessagesWebSearchStream(),
): LlmResponse {
    if (replayScope == null) return this
    val blocks = message.content()
    val metadata = customMetadata.orEmpty() + citationMetadata(stream.complete(blocks)).orEmpty()
    val result = copy(customMetadata = metadata.takeIf { it.isNotEmpty() })
    if (blocks.none { mapper.valueToTree<JsonNode>(it).field("type") == "server_tool_use" }) return result
    val modelContent = content ?: return result
    val replayJson = mapper.writeValueAsString(blocks)
    return result.copy(content = modelContent.copy(parts = modelContent.parts.mapIndexed { index, part ->
        part.copy(partMetadata = part.partMetadata.orEmpty() + buildMap {
            put(REPLAY_SCOPE, replayScope)
            if (index == 0) put(REPLAY_BLOCKS, replayJson)
        })
    }))
}

/** 请求端按 part 展开，兼容 ADK 将相邻的 assistant 消息合并为单个 Content。 */
internal fun Content.messagesRequestBlocks(
    scope: String?,
    convert: (Part) -> ContentBlockParam?,
): List<ContentBlockParam> = parts.flatMap { part ->
    val savedScope = part.partMetadata?.get(REPLAY_SCOPE) as? String
    if (savedScope == null) return@flatMap listOfNotNull(convert(part))
    if (savedScope != scope) {
        return@flatMap if (part.thought == true) emptyList() else listOfNotNull(convert(part))
    }
    val raw = part.partMetadata?.get(REPLAY_BLOCKS) as? String ?: return@flatMap emptyList()
    mapper.readTree(raw).map { mapper.treeToValue(it, ContentBlockParam::class.java) }
}

internal fun Message.asMessagesContinuation(): MessageParam =
    MessageParam.builder().role(MessageParam.Role.ASSISTANT)
        .contentOfBlockParams(content().map { mapper.convertValue(it, ContentBlockParam::class.java) })
        .build()
