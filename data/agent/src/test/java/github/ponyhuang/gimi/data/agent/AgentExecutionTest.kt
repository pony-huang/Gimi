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
        val execution = AgentChatRunner.Execution("user", "session", native, metadata)

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
