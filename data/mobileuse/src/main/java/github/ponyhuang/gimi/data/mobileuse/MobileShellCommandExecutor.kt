package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileShellResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 有界的 shell 子进程执行器；持续排空管道，避免输出写满后卡住 Binder 调用。 */
internal class MobileShellCommandExecutor(
    private val outputLimit: Int = 32_768,
    private val startProcess: (ProcessBuilder) -> Process = { it.start() },
) {
    @Throws(IOException::class, InterruptedException::class)
    fun execute(displayId: Int, command: String): MobileShellResult {
        require(command.isNotBlank() && command.length <= 4_000 && '\u0000' !in command) { "Invalid shell command" }
        val builder = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true)
        // 仅提供显式选屏所需的编号，不把环境变量误当作命令的权限隔离。
        builder.environment()["GIMI_DISPLAY_ID"] = displayId.toString()
        return execute(builder)
    }

    /** 固定参数命令直接创建进程，不能把组件名、坐标等参数重新拼成 shell 脚本。 */
    @Throws(IOException::class, InterruptedException::class)
    fun executeArguments(arguments: Array<String>): MobileShellResult =
        execute(ProcessBuilder(*arguments).redirectErrorStream(true))

    private fun execute(builder: ProcessBuilder): MobileShellResult {
        val process = startProcess(builder)
        val output = ByteArrayOutputStream()
        val truncated = AtomicBoolean(false)
        val reader = Thread({
            try {
                process.inputStream.use { stream ->
                    val buffer = ByteArray(4_096)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            val retained = minOf(count, outputLimit - output.size())
                            output.write(buffer, 0, retained)
                            if (retained < count) truncated.set(true)
                        }
                    }
                }
            } catch (_: IOException) {
                truncated.set(true)
            }
        }, "gimi-shell-output").apply { isDaemon = true }
        var terminationRequested = false
        try {
            reader.start()
            process.outputStream.close()
            val finished = process.waitFor(10_000, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                terminationRequested = true
                // destroyForcibly 只表示已发出强制终止信号：短暂等待确认进程真正退出，
                // 避免"仍在运行的子进程"被当作已终止上报。宽限期内仍未退出的顽固进程
                // （或已继承管道的后台后代）无法在此安全追杀，交由 truncated 标记提示输出不完整。
                process.waitFor(1_000, TimeUnit.MILLISECONDS)
            }
            // 子进程可能把管道传给后台后代；不能无限等 EOF，也不能无限积累输出。
            reader.join(500)
            if (reader.isAlive) truncated.set(true)
            val text = synchronized(output) { output.toString(Charsets.UTF_8.name()) }
            return MobileShellResult(if (finished) process.exitValue() else null, text, !finished, truncated.get())
        } finally {
            // 中断或异常也必须终止直接子进程；不自动再执行可能已部分生效的命令。
            if (!terminationRequested) process.destroyForcibly()
            try { process.inputStream.close() } catch (_: IOException) { }
            try { process.errorStream.close() } catch (_: IOException) { }
        }
    }
}
