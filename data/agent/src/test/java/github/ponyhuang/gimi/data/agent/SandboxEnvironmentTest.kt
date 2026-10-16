@file:OptIn(com.google.adk.kt.annotations.ExperimentalEnvironmentApi::class)

package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.Context
import com.google.adk.kt.sessions.SessionKey
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxEnvironment
import github.ponyhuang.gimi.domain.sandbox.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** 官方 Environment 适配必须保留调用会话、原始字节与非零退出，不能吞掉取消。 */
class SandboxEnvironmentTest {
    @Test
    fun routesCommandToCallingSessionAndPreservesNonzeroExit() = runTest {
        var observed: SandboxSessionKey? = null
        var operation: SandboxOperation? = null
        val environment = SandboxEnvironment(object : SandboxExecutionRepository {
            override suspend fun execute(session: SandboxSessionKey, value: SandboxOperation): SandboxExecutionResult {
                observed = session
                operation = value
                return SandboxExecutionResult(true, SandboxOutput.Command("false", "out", "err", 7))
            }
        })
        val result = environment.execute(context("chat-a"), "false", 30.seconds).getOrThrow()
        assertEquals(SandboxSessionKey("user", "chat-a"), observed)
        assertEquals(SandboxOperation.Command("false", timeoutSeconds = 30), operation)
        assertEquals(7, result.exitCode)
        assertEquals("err", result.stderr)
    }

    @Test
    fun binaryFilesRoundTripWithoutTextConversion() = runTest {
        val bytes = byteArrayOf(0, -1, 4)
        var stored = byteArrayOf()
        val environment = SandboxEnvironment(object : SandboxExecutionRepository {
            override suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult =
                when (operation) {
                    is SandboxOperation.WriteBytes -> {
                        stored = operation.content
                        SandboxExecutionResult(true, SandboxOutput.WrittenFile(operation.path))
                    }
                    is SandboxOperation.ReadBytes -> SandboxExecutionResult(true, SandboxOutput.ReadBytes(operation.path, stored))
                    else -> error("Unexpected operation")
                }
        })
        environment.writeFile(context("chat-b"), "asset.bin", bytes).getOrThrow()
        assertArrayEquals(bytes, environment.readFile(context("chat-b"), "asset.bin").getOrThrow())
    }

    @Test
    fun cancellationPropagatesWithoutRetry() = runTest {
        var calls = 0
        val environment = SandboxEnvironment(object : SandboxExecutionRepository {
            override suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult {
                calls++
                throw CancellationException("Cancelled")
            }
        })
        try {
            environment.execute(context("chat-a"), "sleep 10")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    private fun context(id: String): Context = mockk {
        every { session.key } returns SessionKey("gimi", "user", id)
    }
}
