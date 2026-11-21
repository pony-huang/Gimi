package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.Message
import com.google.adk.kt.types.FinishReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** Anthropic 协议族的停止原因归一：宿主要靠它区分拒答、截断与正常结束。 */
class AnthropicFinishReasonTest {

    @Test fun `refusal is reported as safety`() {
        assertEquals(FinishReason.SAFETY, message("refusal").toAdkFinishReason())
    }

    @Test fun `max tokens is reported as max tokens`() {
        assertEquals(FinishReason.MAX_TOKENS, message("max_tokens").toAdkFinishReason())
    }

    @Test fun `end turn and tool use are reported as stop`() {
        assertEquals(FinishReason.STOP, message("end_turn").toAdkFinishReason())
        assertEquals(FinishReason.STOP, message("tool_use").toAdkFinishReason())
    }

    @Test fun `unknown stop reason stays unspecified`() {
        assertEquals(FinishReason.FINISH_REASON_UNSPECIFIED, message("brand_new_reason").toAdkFinishReason())
    }

    @Test fun `missing stop reason stays absent`() {
        assertEquals(null, message(null).toAdkFinishReason())
    }

    private fun message(stopReason: String?): Message {
        val stop = stopReason?.let { ""","stop_reason":"$it"""" } ?: ""
        return jsonMapper().readValue(
            """{"id":"msg_1","type":"message","role":"assistant","model":"deepseek-flash",""" +
                """"content":[{"type":"text","text":"hi"}]$stop,""" +
                """"usage":{"input_tokens":1,"output_tokens":1}}""",
            Message::class.java,
        )
    }
}
