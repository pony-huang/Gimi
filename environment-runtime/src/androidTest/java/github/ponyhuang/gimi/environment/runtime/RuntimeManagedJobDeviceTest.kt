package github.ponyhuang.gimi.environment.runtime

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真实 PRoot 的进程树、实时输出与 npm PATH 集成验证；仅使用独立临时会话。 */
@RunWith(AndroidJUnit4::class)
class RuntimeManagedJobDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = RuntimeEngine.get(context)
    private val session = "runtime-device-test-${UUID.randomUUID()}"
    private val guestPath = "/home/workspace/${RuntimeEngine.digest(session.toByteArray())}"
    private val workspace = File(context.filesDir, "rootfs${guestPath}")
    private val jobs = mutableListOf<String>()
    private var enabledBefore = false

    @Before fun prepare() {
        assumeTrue("An installed rootfs is required", File(context.filesDir, "installed").isFile)
        enabledBefore = engine.call("status", Bundle()).getBoolean("enabled")
        engine.enable()
        workspace.mkdirs()
    }

    @After fun cleanup() {
        jobs.forEach { id -> engine.call("stopJob", args().apply { putString("id", id) }) }
        if (!enabledBefore) engine.disable()
        // PRoot 在取消后的清理窗口内可能仍持有工作目录。
        SystemClock.sleep(2500)
        workspace.deleteRecursively()
    }

    private fun args() = Bundle().apply { putString("session", session) }

    private fun start(command: String, keepAlive: Boolean): String =
        engine.call("startJob", args().apply { putString("command", command); putBoolean("keepAlive", keepAlive) })
            .getString("id")!!.also { jobs += it }

    private fun query(id: String) = engine.call("queryJob", args().apply { putString("id", id) })

    private fun await(id: String, predicate: (Bundle) -> Boolean): Bundle {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val result = query(id)
            if (predicate(result)) return result
            SystemClock.sleep(100)
        }
        throw AssertionError("Job did not reach expected state")
    }

    @Test fun loginStyleDaemonRemainsAliveAndStopsWithJob() {
        val id = start("printf 'LOGIN_READY\n'; (while :; do printf x >> progress; sleep 1; done) >/dev/null 2>&1 &", true)
        val live = await(id) { it.getString("stdout").orEmpty().contains("LOGIN_READY") }
        assertTrue(live.getBoolean("running"))
        SystemClock.sleep(2200)
        val progress = File(workspace, "progress")
        assertTrue(progress.length() >= 2)
        engine.call("stopJob", args().apply { putString("id", id) })
        jobs.remove(id)
        SystemClock.sleep(3000)
        val stoppedSize = progress.length()
        SystemClock.sleep(2200)
        assertEquals("Child process must stop with its job", stoppedSize, progress.length())
    }

    @Test fun immediatelyStoppedJobsCannotWriteAfterCancellation() {
        repeat(3) { index ->
            val id = start("sleep 2; printf unexpected > late-$index", true)
            engine.call("stopJob", args().apply { putString("id", id) })
            jobs.remove(id)
        }
        SystemClock.sleep(3500)
        repeat(3) { index -> assertFalse(File(workspace, "late-$index").exists()) }
    }

    @Test fun failedStartCompletesEvenWithKeepAliveEnabled() {
        val id = start("printf 'failed' >&2; exit 7", true)
        val result = await(id) { !it.getBoolean("running") }
        assertEquals(7, result.getInt("exitCode"))
        assertEquals("failed", result.getString("stderr"))
    }

    @Test fun discoversCustomNpmPrefixAndPropagatesPipelineFailure() {
        val npm = engine.call("execute", args().apply { putString("command", "command -v npm") })
        assumeTrue("npm is required for the prefix test", npm.getInt("exitCode") == 0)
        val bin = File(workspace, "custom-prefix/bin").apply { mkdirs() }
        File(bin, "gimi-path-probe").apply {
            writeText("#!/bin/sh\nprintf 'PATH_OK'\n")
            assertTrue(setExecutable(true))
        }
        File(workspace, ".npmrc").writeText("prefix=$guestPath/custom-prefix\n")
        val result = engine.call("execute", args().apply { putString("command", "gimi-path-probe") })
        assertEquals(0, result.getInt("exitCode"))
        assertEquals("PATH_OK", result.getString("stdout"))
        val pipeline = engine.call("execute", args().apply { putString("command", "exit_command_that_does_not_exist | cat") })
        assertTrue("Pipeline failure must be observable", pipeline.getInt("exitCode") != 0)
    }
}
