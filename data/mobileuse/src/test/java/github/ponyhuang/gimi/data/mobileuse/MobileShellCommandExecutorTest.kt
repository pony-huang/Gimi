package github.ponyhuang.gimi.data.mobileuse

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileShellCommandExecutorTest {
    @Test
    fun executesOnceWithDisplayEnvironmentAndReportsNonzeroExit() {
        val process = process("permission denied", exitCode = 7)
        var starts = 0
        val executor = MobileShellCommandExecutor(startProcess = { builder ->
            starts++
            assertEquals(listOf("/system/bin/sh", "-c", "input -d \"\$GIMI_DISPLAY_ID\" keyevent BACK"), builder.command())
            assertEquals("42", builder.environment()["GIMI_DISPLAY_ID"])
            assertTrue(builder.redirectErrorStream())
            process
        })
        val result = executor.execute(42, "input -d \"\$GIMI_DISPLAY_ID\" keyevent BACK")
        assertEquals(1, starts)
        assertEquals(7, result.exitCode)
        assertEquals("permission denied", result.output)
        assertFalse(result.timedOut)
        assertFalse(result.outputTruncated)
    }

    @Test
    fun drainsLargeOutputWhileReturningOnlyTheBudgetedPrefix() {
        val executor = MobileShellCommandExecutor(outputLimit = 16, startProcess = { process("x".repeat(100_000)) })
        val result = executor.execute(42, "some-command")
        assertEquals("x".repeat(16), result.output)
        assertTrue(result.outputTruncated)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun killsTimedOutProcessAndNeverClaimsAnExitCode() {
        val process = process("partial output", finished = false)
        val result = MobileShellCommandExecutor(startProcess = { process }).execute(42, "some-command")
        assertTrue(result.timedOut)
        assertNull(result.exitCode)
        assertEquals("partial output", result.output)
        verify(exactly = 1) { process.destroyForcibly() }
    }

    @Test
    fun invalidCommandsNeverStartAProcess() {
        var starts = 0
        val executor = MobileShellCommandExecutor(startProcess = { starts++; process("") })
        for (command in listOf(" ", "x".repeat(4_001), "echo\u0000bad")) {
            try {
                executor.execute(42, command)
                throw AssertionError("Invalid command accepted")
            } catch (_: IllegalArgumentException) {
                // 输入校验必须先于创建特权进程。
            }
        }
        assertEquals(0, starts)
    }

    @Test
    fun startFailureIsNotRetried() {
        var starts = 0
        try {
            MobileShellCommandExecutor(startProcess = { starts++; throw IOException("cannot start") })
                .execute(42, "some-command")
            throw AssertionError("Expected start failure")
        } catch (_: IOException) {
            assertEquals(1, starts)
        }
    }

    private fun process(output: String, exitCode: Int = 0, finished: Boolean = true): Process = mockk {
        every { inputStream } returns ByteArrayInputStream(output.toByteArray())
        every { outputStream } returns ByteArrayOutputStream()
        every { errorStream } returns ByteArrayInputStream(byteArrayOf())
        every { waitFor(any(), TimeUnit.MILLISECONDS) } returns finished
        every { exitValue() } returns exitCode
        every { destroyForcibly() } answers { this@mockk }
    }
}
