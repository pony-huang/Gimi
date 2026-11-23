package github.ponyhuang.gimi.domain.conversation.model

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** 用户显式停止 Chatbot，当轮可见内容应随会话保存。 */
class ChatbotStopped : CancellationException("Chatbot reply stopped")

/** 网页引用的展示信息。标题和图标可能由厂商省略。 */
@Serializable
data class WebSource(val url: String, val title: String? = null, val iconUrl: String? = null)

/** 正文中的引用位置，以原始正文的 UTF-16 下标保存，供历史回放使用。 */
@Serializable
data class WebCitation(val source: WebSource, val endOffset: Int)

/** 运行事件与历史事件共用的 JSON 元数据格式。 */
object WebCitationMetadata {
    const val KEY = "gimi.web_citations.v1"
    const val STOPPED_REPLY_KEY = "gimi.web_citations.stopped_reply"

    fun encode(citations: List<WebCitation>): List<Map<String, Any>> = citations.map { citation ->
        buildMap {
            put("url", citation.source.url)
            put("endOffset", citation.endOffset)
            citation.source.title?.let { put("title", it) }
            citation.source.iconUrl?.let { put("iconUrl", it) }
        }
    }

    fun decode(value: Any?): List<WebCitation> = (value as? List<*>).orEmpty().mapNotNull { entry ->
        val fields = entry as? Map<*, *> ?: return@mapNotNull null
        val url = (fields["url"] as? String)?.takeIf { webCitationHost(it) != null } ?: return@mapNotNull null
        val number = fields["endOffset"] as? Number ?: return@mapNotNull null
        val offset = number.toInt().takeIf { it > 0 && number.toDouble() == it.toDouble() } ?: return@mapNotNull null
        val title = (fields["title"] as? String)?.takeIf(String::isNotBlank)
        val icon = (fields["iconUrl"] as? String)?.takeIf { webCitationHost(it) != null }
        WebCitation(WebSource(url, title, icon), offset)
    }.distinct()
}

fun webCitationHost(url: String): String? = try {
    val uri = URI(url)
    uri.host?.lowercase()?.takeIf {
        uri.userInfo == null && (uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
    }
} catch (_: java.net.URISyntaxException) {
    null
}

fun webCitationUtf16Offset(text: String, codePoints: Int): Int? =
    if (codePoints in 0..text.codePointCount(0, text.length)) text.offsetByCodePoints(0, codePoints) else null
