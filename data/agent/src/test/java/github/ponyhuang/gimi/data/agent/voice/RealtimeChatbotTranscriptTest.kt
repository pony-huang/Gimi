package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Chatbot 终稿策略：只写终稿、打断半轮不写、空轮不写、写入失败不中断通话。 */
class RealtimeChatbotTranscriptTest {
    private val writer = mockk<ChatbotConversationWriter.Call>(relaxed = true)
    private val transcript = RealtimeChatbotTranscript(writer)

    private fun userContent(text: String) = Content(role = Role.USER, parts = listOf(Part(text = text)))
    private fun modelContent(text: String) = Content(role = Role.MODEL, parts = listOf(Part(text = text)))

    @Test fun `turn completion persists the final user and model transcripts in order`() = runTest {
        transcript.onUserTranscript("你在")
        transcript.onUserTranscript("你在说什么") // 输入转写以最新累计文本替换
        transcript.onAssistantDelta("我没听")
        transcript.onAssistantDelta("清，再说一遍")

        transcript.commit(withAssistant = true)

        verify(exactly = 1) { writer.enqueue(userContent("你在说什么"), isNativeRealtimeTranscript = true) }
        verify(exactly = 1) { writer.enqueue(modelContent("我没听清，再说一遍")) }
        coVerify(exactly = 1) { writer.flush() }
    }

    @Test fun `interruption persists the user utterance only and drops the partial answer`() = runTest {
        transcript.onUserTranscript("讲个故事")
        transcript.onAssistantDelta("从前有")

        transcript.commit(withAssistant = false)

        verify(exactly = 1) { writer.enqueue(userContent("讲个故事"), isNativeRealtimeTranscript = true) }
        verify(exactly = 0) { writer.enqueue(match<Content> { it.role == Role.MODEL }, any()) }
        // cancelled 的 response.done 仍会发 turnComplete：状态已清空，成为空轮。
        transcript.commit(withAssistant = true)
        coVerify(exactly = 1) { writer.flush() }
    }

    @Test fun `empty turns never touch the writer`() = runTest {
        transcript.commit(withAssistant = true)
        transcript.commit(withAssistant = false)
        verify(exactly = 0) { writer.enqueue(any<Content>(), any()) }
        coVerify(exactly = 0) { writer.flush() }
    }

    @Test fun `manual submit rides the current turn`() = runTest {
        transcript.onManualSubmit("看这张图")
        transcript.commit(withAssistant = false)
        verify(exactly = 1) { writer.enqueue(userContent("看这张图"), isNativeRealtimeTranscript = true) }
    }

    @Test fun `persistence failure is swallowed so the call survives`() = runTest {
        coEvery { writer.flush() } throws VoiceAudioBackendException(
            github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure.Persistence,
        )
        transcript.onUserTranscript("问题")
        transcript.commit(withAssistant = true)
        // 写入失败被吞掉：不抛出，状态已复位（重试不产生重复事件）。
        transcript.commit(withAssistant = true)
        coVerify(exactly = 1) { writer.flush() }
    }
}
