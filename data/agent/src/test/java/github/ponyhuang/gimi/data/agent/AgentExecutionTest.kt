package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgentExecutionTest {
    @Test
    fun cloudReferencesCannotSilentlyEnterTheInlineOnlyAdapter() = runTest {
        val runner = mockk<InMemoryRunner>()
        val execution = AgentChatExecution("user", "session", runner, emptyMap())
        val file = github.ponyhuang.gimi.domain.conversation.model.FileAttachment("application/pdf", "file", 10,
            "report.pdf", cloudReference = github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference("remote", "scope"))
        try {
            execution.send("read", listOf(file))
            throw AssertionError("Unmapped file reference must not be omitted or read as a local file")
        } catch (_: IllegalArgumentException) {
            io.mockk.verify { runner wasNot io.mockk.Called }
        }
    }

    @Test
    fun sendAndBothResumePathsUseTheSameRunnerAndMetadata() = runTest {
        val configs = mutableListOf<RunConfig>()
        val messages = mutableListOf<Content>()
        val native = mockk<InMemoryRunner>()
        coEvery {
            native.runAsync(
                userId = "user",
                sessionId = "session",
                invocationId = null,
                newMessage = capture(messages),
                stateDelta = null,
                runConfig = capture(configs),
            )
        } returns emptyFlow()
        val metadata = mapOf<String, Any>("original-tools" to listOf("clock"))
        val execution = AgentChatExecution("user", "session", native, metadata)

        execution.send("hello")
        execution.respondToToolConfirmation("confirm", true)
        execution.respondToInputRequest("input", "get_user_choice", mapOf("value" to "yes"))

        assertEquals(3, configs.size)
        configs.forEach { assertSame(metadata, it.customMetadata) }
        assertEquals("hello", messages[0].parts.single().text)
        assertEquals("confirm", messages[1].parts.single().functionResponse?.id)
        assertEquals("input", messages[2].parts.single().functionResponse?.id)
    }
}
