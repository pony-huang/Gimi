package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.GetSessionConfig
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.plugins.ConversationGenerateTitlePlugin
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标题写入会话状态的时机与可见性。
 *
 * 抽屉行标题取自会话状态，而逐行刷新只读最近窗口，因此固定两点：一轮结束后标题状态确实
 * 已经写好，且带窗口的读取仍然能看到它（状态存在会话行里，与加载了多少事件无关）。
 */
class ConversationTitleStateTest {

    @Test
    fun titleStateIsReadyAfterTheTurnAndVisibleThroughAWindowedRead() = runTest {
        val sessions: SessionService = InMemorySessionService()
        var modelCalls = 0
        val model = object : Model {
            override val name = "title-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                // 第一次是对话本身，之后是标题插件发起的独立请求。
                val text = if (modelCalls++ == 0) "第一次回答" else "标题A"
                emit(LlmResponse(content = Content(role = Role.MODEL, parts = listOf(Part(text = text)))))
            }
        }
        // 未配置快速模型时标题请求回落到会话模型；relaxed mock 会返回假模型，必须显式返回 null。
        val factory = mockk<AgentLLMModelFactory>(relaxed = true) {
            every { selectFastModelConfig() } returns null
        }
        val runner = InMemoryRunner(
            App(
                appName = AgentChatRunner.APP_NAME,
                rootAgent = LlmAgent(name = "agent", model = model),
                plugins = listOf(ConversationGenerateTitlePlugin(factory)),
            ),
            sessionService = sessions,
        )
        val execution = AgentChatExecution("user", "session", runner, emptyMap())

        execution.send("你好").toList()

        val key = SessionKey(AgentChatRunner.APP_NAME, "user", "session")
        val full = sessions.getSession(key)!!
        val windowed = sessions.getSession(key, GetSessionConfig(numRecentEvents = 20))!!
        assertEquals(2, modelCalls)
        assertEquals("标题A", full.state[TITLE_STATE_KEY])
        assertEquals("标题A", windowed.state[TITLE_STATE_KEY])
        // 预览与标题回退都需要窗口内的用户消息。
        assertTrue(
            windowed.events.any { event ->
                event.author == "user" && event.content?.parts.orEmpty().any { it.text == "你好" }
            },
        )
    }

    private companion object {
        const val TITLE_STATE_KEY = "conversation.title"
    }
}
