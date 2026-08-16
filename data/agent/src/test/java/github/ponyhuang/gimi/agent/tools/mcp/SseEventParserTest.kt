package github.ponyhuang.gimi.agent.tools.mcp

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class SseEventParserTest {
  @Test
  fun `parses comments multiline data ids and retry hints`() {
    val events =
      SseEventParser.parse(
          StringReader(
            ": heartbeat\r\n" +
              "event: message\r\n" +
              "id: 42\r\n" +
              "retry: 1500\r\n" +
              "data: {\"jsonrpc\":\"2.0\",\r\n" +
              "data: \"id\":1}\r\n\r\n"
          )
        )
        .toList()

    assertEquals(
      listOf(SseEvent("message", "{\"jsonrpc\":\"2.0\",\n\"id\":1}", "42", 1500L)),
      events,
    )
  }

  @Test
  fun `does not dispatch blocks without data`() {
    val events = SseEventParser.parse(StringReader("event: primer\n\n: ping\n\n")).toList()

    assertEquals(emptyList<SseEvent>(), events)
  }
}
