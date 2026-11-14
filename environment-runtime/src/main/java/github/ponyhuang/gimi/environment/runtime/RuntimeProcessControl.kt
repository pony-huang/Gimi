package github.ponyhuang.gimi.environment.runtime

import android.os.Process as AndroidProcess
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 先终止被追踪的入口 Shell，让 PRoot 清理子进程，再回收宿主追踪进程。 */
internal class RuntimeProcessControl(
    val process: Process,
    private val pidFile: File,
    private val guestPidPath: String,
) {
    @Synchronized
    fun terminate() {
        val interrupted = Thread.interrupted()
        try {
            // 启动后立刻取消时，入口 Shell 可能尚未写出 PID，给它一个有界登记窗口。
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (process.isAlive && !signalShell() && System.nanoTime() < deadline) Thread.sleep(10)
            if (process.isAlive && !process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        } catch (_: InterruptedException) {
            signalShell()
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        } finally {
            pidFile.delete()
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun signalShell(): Boolean = try {
        val pid = pidFile.inputStream().use { input ->
            val buffer = ByteArray(32)
            val count = input.read(buffer)
            if (count <= 0) null else String(buffer, 0, count, Charsets.US_ASCII).trim().toIntOrNull()
        }
        if (pid == null || pid <= 1 || pid == AndroidProcess.myPid()) false else {
            // 对照进程启动参数中的唯一票据，避免向陈旧 PID 或被改写的登记项发送信号。
            val command = File("/proc/$pid/cmdline").readText()
            if (command.split('\u0000').contains(guestPidPath)) {
                AndroidProcess.sendSignal(pid, OsConstants.SIGTERM)
                true
            } else false
        }
    } catch (_: IOException) { false }
}
