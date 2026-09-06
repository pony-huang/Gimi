package github.ponyhuang.gimi.data.agent.conversation

import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserInputToolProtocolTest {

    @Test
    fun parsesRequestInputCallIntoFreeTextRequest() {
        val request = UserInputToolProtocol.parseRequest(
            callId = "call-1",
            name = UserInputToolProtocol.REQUEST_INPUT_NAME,
            args = mapOf("message" to "出发日期是哪天？", "response_schema" to mapOf("type" to "string")),
        )

        assertEquals(UserInputKind.FREE_TEXT, request?.kind)
        assertEquals("call-1", request?.callId)
        assertEquals("出发日期是哪天？", request?.message)
        assertEquals(emptyList<String>(), request?.options)
    }

    @Test
    fun parsesGetUserChoiceCallIntoChoiceRequest() {
        val request = UserInputToolProtocol.parseRequest(
            callId = "call-2",
            name = UserInputToolProtocol.GET_USER_CHOICE_NAME,
            args = mapOf("options" to listOf("导出 PDF", "保存到工作文件")),
        )

        assertEquals(UserInputKind.CHOICE, request?.kind)
        assertEquals(listOf("导出 PDF", "保存到工作文件"), request?.options)
    }

    @Test
    fun missingCallIdOrMandatoryArgsYieldNull() {
        assertNull(UserInputToolProtocol.parseRequest(null, UserInputToolProtocol.REQUEST_INPUT_NAME, mapOf("message" to "hi")))
        assertNull(UserInputToolProtocol.parseRequest("", UserInputToolProtocol.REQUEST_INPUT_NAME, mapOf("message" to "hi")))
        // 参数存在但形状异常按降级解析，只有必填参数整体缺失才是 null。
        assertNull(UserInputToolProtocol.parseRequest("call-3", UserInputToolProtocol.REQUEST_INPUT_NAME, emptyMap()))
    }

    @Test
    fun malformedOptionShapesAreDroppedInsteadOfFailing() {
        val request = UserInputToolProtocol.parseRequest(
            callId = "call-4",
            name = UserInputToolProtocol.GET_USER_CHOICE_NAME,
            args = mapOf("options" to listOf("有效项", 42, null)),
        )

        assertEquals(listOf("有效项"), request?.options)
    }

    @Test
    fun unrelatedToolsAreNotInputRequests() {
        assertNull(UserInputToolProtocol.parseRequest("call-5", "web_search", emptyMap()))
    }

    @Test
    fun responsePayloadUsesStableProtocolKeys() {
        assertEquals(mapOf("choice" to "导出 PDF"), UserInputToolProtocol.responsePayload(UserInputToolProtocol.GET_USER_CHOICE_NAME, "导出 PDF"))
        assertEquals(mapOf("input" to "下周二"), UserInputToolProtocol.responsePayload(UserInputToolProtocol.REQUEST_INPUT_NAME, "下周二"))
        // 未知工具名兜底走自由文本键，避免恢复路径抛异常。
        assertEquals(mapOf("input" to "ok"), UserInputToolProtocol.responsePayload("unknown", "ok"))
    }
}
