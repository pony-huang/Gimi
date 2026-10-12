package github.ponyhuang.gimi.data.agent.tools.sandbox

import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.domain.sandbox.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SandboxToolsTest {
    private class Settings : SandboxSettingsRepository {
        override val configuration = MutableStateFlow(SandboxConfiguration(true, SandboxConnection(apiKey = "private-setting-key")))
        override val revision = MutableStateFlow(0L)
        override suspend fun setEnabled(enabled: Boolean) { configuration.value = configuration.value.copy(enabled = enabled) }
        override suspend fun saveConnection(connection: SandboxConnection) { configuration.value = configuration.value.copy(connection = connection) }
    }
    private class Execution : SandboxExecutionRepository {
        var calls = 0
        var session: SandboxSessionKey? = null
        var operation: SandboxOperation? = null
        var cancelled = false
        override suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult {
            calls++; this.session = session; this.operation = operation
            if (cancelled) throw CancellationException("cancelled")
            return SandboxExecutionResult(true, SandboxOutput.Command("echo", "ok", "", 0))
        }
    }
    private fun context(id: String? = "chat-1") = mockk<ToolContext> {
        every { this@mockk.context.session } returns Session(SessionKey("Gimi", "user-1", id))
    }

    @Test fun exposesExactlyFiveToolsWithoutCredentialsOrSandboxCreation() = runTest {
        val execution = Execution(); val tools = SandboxTools(Settings(), execution).getTools(null)
        assertEquals(setOf("sandbox_run_command", "sandbox_run_code", "sandbox_write_file", "sandbox_read_file", "sandbox_list_files"), tools.map { it.name }.toSet())
        assertEquals(0, execution.calls)
        assertFalse(tools.map { it.declaration() }.toString().contains("private-setting-key"))
        assertEquals(listOf("path", "content"), tools.first { it.name == "sandbox_write_file" }.declaration()!!.parameters!!.required)
    }
    @Test fun usesPersistentUserAndConversationIdentityFromContext() = runTest {
        val execution = Execution()
        val tool = SandboxTools(Settings(), execution).getTools(null).first { it.name == "sandbox_run_command" }
        val response = tool.run(context(), mapOf("command" to "echo ok", "cwd" to "/tmp", "envs" to mapOf("A" to "B"), "timeout" to 0)) as Map<*, *>
        assertEquals(true, response["success"])
        assertEquals(SandboxSessionKey("user-1", "chat-1"), execution.session)
        assertEquals(SandboxOperation.Command("echo ok", "/tmp", mapOf("A" to "B"), 0), execution.operation)
    }
    @Test fun cachedToolIsBlockedAfterDisablingWithoutCallingExecution() = runTest {
        val settings = Settings(); val execution = Execution(); val toolset = SandboxTools(settings, execution)
        val cached = toolset.getTools(null).first()
        settings.setEnabled(false)
        assertTrue(toolset.getTools(null).isEmpty())
        assertEquals(false, (cached.run(context(), mapOf("code" to "1")) as Map<*, *>)["success"])
        assertEquals(0, execution.calls)
    }
    @Test fun invalidArgumentsAndMissingSessionAreReturnedFailures() = runTest {
        val execution = Execution(); val tool = SandboxTools(Settings(), execution).getTools(null).first { it.name == "sandbox_run_command" }
        listOf(mapOf("command" to "x", "timeout" to -1), mapOf("command" to "x", "timeout" to 1.5), mapOf("command" to "x", "envs" to mapOf("A" to 1))).forEach {
            assertEquals(false, (tool.run(context(), it) as Map<*, *>)["success"])
        }
        assertEquals(false, (tool.run(context(null), mapOf("command" to "x")) as Map<*, *>)["success"])
        assertEquals(0, execution.calls)
    }
    @Test(expected = CancellationException::class)
    fun cancellationPropagatesInsteadOfBecomingToolFailure() = runTest {
        val execution = Execution().apply { cancelled = true }
        SandboxTools(Settings(), execution).getTools(null).first().run(context(), mapOf("command" to "x"))
    }
    @Test fun emptyWritesDefaultDirectoryAndPythonDefaultsArePreserved() {
        assertEquals(SandboxOperation.WriteFile("empty", ""), parseSandboxOperation("sandbox_write_file", mapOf("path" to "empty", "content" to "")))
        assertEquals(SandboxOperation.ListFiles("."), parseSandboxOperation("sandbox_list_files", emptyMap()))
        assertEquals(SandboxOperation.Code("1", "python"), parseSandboxOperation("sandbox_run_code", mapOf("code" to "1")))
        assertEquals(SandboxOperation.Code("1", "javascript"), parseSandboxOperation("sandbox_run_code", mapOf("code" to "1", "language" to "JavaScript")))
    }
    @Test fun nonzeroCommandAndKernelErrorKeepSuccessFlag() {
        val command = SandboxOperation.Command("exit 5")
        val commandResponse = SandboxExecutionResult(true, SandboxOutput.Command("exit 5", "before", "warning", 5)).toToolResponse(command)
        assertEquals(true, commandResponse["success"])
        assertEquals(5, commandResponse["exit_code"])
        val codeResponse = SandboxExecutionResult(true, SandboxOutput.Code("before", "", "", "ValueError: bad")).toToolResponse(SandboxOperation.Code("raise ValueError()"))
        assertEquals(true, codeResponse["success"])
        assertEquals("ValueError: bad", codeResponse["error"])
    }
    @Test fun failureCarriesRelevantPathAndBoundedPartialOutput() {
        val response = SandboxExecutionResult(false, error = "timeout", partialOutput = "你".repeat(4000))
            .toToolResponse(SandboxOperation.ReadFile("file.txt"))
        assertEquals(false, response["success"])
        assertEquals("file.txt", response["path"])
        assertTrue(response["partial_output"].toString().endsWith("…[truncated 2001 bytes]"))
    }
    @Test fun readOutputIsBoundedAndEnvironmentResetIsReported() {
        val response = SandboxExecutionResult(true, SandboxOutput.ReadFile("file", "x".repeat(10001)), environmentReset = true)
            .toToolResponse(SandboxOperation.ReadFile("file"))
        assertTrue(response["content"].toString().endsWith("…[truncated 1 bytes]"))
        assertEquals(true, response["environment_reset"])
    }
}
