package github.ponyhuang.gimi.data.agent.tools.mcp

import java.io.BufferedReader
import java.io.Reader

/**
 * 单个 Server-Sent Event，保留恢复连接所需的元数据。
 *
 * @property event 可选事件类型；缺省时按普通 message 处理。
 * @property data 合并多行后的事件负载。
 * @property id 用于断线恢复的最后事件 ID。
 * @property retryMillis 服务端建议的重连等待时间。
 */
internal data class SseEvent(
  val event: String?,
  val data: String,
  val id: String?,
  val retryMillis: Long?,
)

/** 按 WHATWG SSE 行规则解析 OkHttp 的长连接响应。 */
internal object SseEventParser {
  fun parse(reader: Reader): Sequence<SseEvent> = sequence {
    val buffered = if (reader is BufferedReader) reader else reader.buffered()
    var event: String? = null
    var id: String? = null
    var retryMillis: Long? = null
    val data = mutableListOf<String>()

    fun reset() {
      event = null
      retryMillis = null
      data.clear()
    }

    while (true) {
      val line = buffered.readLine()
      if (line == null || line.isEmpty()) {
        if (data.isNotEmpty()) yield(SseEvent(event, data.joinToString("\n"), id, retryMillis))
        reset()
        if (line == null) break
        continue
      }
      if (line.startsWith(':')) continue

      val separator = line.indexOf(':')
      val field = if (separator < 0) line else line.substring(0, separator)
      val rawValue = if (separator < 0) "" else line.substring(separator + 1)
      val value = rawValue.removePrefix(" ")
      when (field) {
        "event" -> event = value
        "data" -> data += value
        "id" -> if ('\u0000' !in value) id = value
        "retry" -> retryMillis = value.toLongOrNull()?.takeIf { it >= 0 }
      }
    }
  }
}
