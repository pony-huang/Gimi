package dev.e2b.sdk

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.Base64

/** Command API. Foreground runs throw CommandExitException on non-zero exit. */
class Commands internal constructor(private val envd: Envd) {
    @JvmOverloads fun run(command: String, options: CommandOptions = CommandOptions()): CommandResult =
        start(command, options).use { it.await() }

    /** Starts a remote process without waiting; callers must close the returned stream handle. */
    @JvmOverloads fun start(command: String, options: CommandOptions = CommandOptions()): CommandHandle {
        require(command.isNotBlank())
        val process = mutableMapOf<String, Any>(
            "cmd" to "/bin/bash", "args" to listOf("-l", "-c", command), "envs" to options.envs.toMap(),
        )
        options.cwd?.let { process["cwd"] = it }
        return handle(envd.stream("Start", mapOf("process" to process, "stdin" to options.stdin), options), options)
    }

    @JvmOverloads fun connect(pid: Int, options: CommandOptions = CommandOptions()): CommandHandle {
        require(pid > 0)
        return handle(envd.stream("Connect", mapOf("process" to mapOf("pid" to pid)), options), options)
    }

    @JvmOverloads fun list(user: String? = null): List<ProcessInfo> {
        val json = envd.unary("process.Process", "List", emptyMap<String, String>(), user)
        return json.getAsJsonArray("processes")?.map {
            val item = Json.obj(it)
            val config = item.getAsJsonObject("config") ?: throw ProtocolException("Missing process config")
            ProcessInfo(item.number("pid").toInt(), config.text("cmd") ?: "", config.getAsJsonArray("args")?.map { arg -> arg.asString } ?: emptyList(), config.text("cwd"), config.strings("envs"))
        } ?: emptyList()
    }

    @JvmOverloads fun sendStdin(pid: Int, text: String, user: String? = null) = sendStdin(pid, text.toByteArray(Charsets.UTF_8), user)
    @JvmOverloads fun sendStdin(pid: Int, bytes: ByteArray, user: String? = null) {
        require(pid > 0)
        envd.unary("process.Process", "SendInput", mapOf("process" to mapOf("pid" to pid), "input" to mapOf("stdin" to Base64.getEncoder().encodeToString(bytes))), user)
    }

    @JvmOverloads fun closeStdin(pid: Int, user: String? = null) {
        require(pid > 0)
        envd.unary("process.Process", "CloseStdin", mapOf("process" to mapOf("pid" to pid)), user)
    }

    @JvmOverloads fun kill(pid: Int, user: String? = null): Boolean {
        require(pid > 0)
        return try {
            envd.unary("process.Process", "SendSignal", mapOf("process" to mapOf("pid" to pid), "signal" to "SIGNAL_SIGKILL"), user)
            true
        } catch (_: NotFoundException) { false }
    }

    private fun handle(stream: ConnectStream, options: CommandOptions): CommandHandle {
        try {
            val event = stream.next()?.getAsJsonObject("event") ?: throw ProtocolException("Missing command start event")
            val pid = event.getAsJsonObject("start")?.number("pid")?.toInt()
                ?: throw ProtocolException("Expected command start event")
            if (pid <= 0) throw ProtocolException("Invalid command PID")
            return CommandHandle(pid, stream, this, options)
        } catch (error: Throwable) { stream.close(); throw error }
    }
}

/** Owns the local command stream. close() cancels listening; kill() terminates the remote process.
 * await() is single-reader and retains at most CommandOptions.maxOutputBytes of combined output.
 */
class CommandHandle internal constructor(
    val pid: Int,
    private val stream: ConnectStream,
    private val commands: Commands,
    private val options: CommandOptions,
) : Closeable {
    @Volatile private var closed = false
    private var result: CommandResult? = null

    @Synchronized fun await(): CommandResult {
        result?.let { return checked(it) }
        check(!closed) { "Command handle is closed" }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var exitCode: Int? = null
        try {
            while (true) {
                val message = stream.next() ?: break
                val event = message.getAsJsonObject("event") ?: throw ProtocolException("Missing process event")
                event.getAsJsonObject("data")?.let { data ->
                    if (exitCode != null) throw ProtocolException("Output received after command end")
                    for ((name, output) in listOf("stdout" to stdout, "stderr" to stderr)) {
                        data.text(name)?.let {
                            val bytes = try { Base64.getDecoder().decode(it) }
                            catch (_: IllegalArgumentException) { throw ProtocolException("Invalid base64 command output") }
                            if (stdout.size().toLong() + stderr.size() + bytes.size > options.maxOutputBytes) {
                                throw ProtocolException("Command output exceeds configured limit")
                            }
                            output.write(bytes)
                        }
                    }
                }
                event.getAsJsonObject("end")?.let { end ->
                    if (exitCode != null) throw ProtocolException("Duplicate command end event")
                    if (end.text("error")?.isNotEmpty() == true && end.number("exitCode") == 0L) {
                        throw E2BException("Remote process did not exit normally")
                    }
                    exitCode = end.number("exitCode").toInt()
                }
            }
            val code = exitCode ?: throw ProtocolException("Stream ended without a command exit event")
            // 字节片段可能切在 UTF-8 字符中间，全部收齐后再解码。
            val completed = CommandResult(pid, stdout.toByteArray().toString(Charsets.UTF_8), stderr.toByteArray().toString(Charsets.UTF_8), code)
            result = completed
            return checked(completed)
        } finally { close() }
    }

    fun sendStdin(text: String) = commands.sendStdin(pid, text, options.user)
    fun closeStdin() = commands.closeStdin(pid, options.user)
    fun kill(): Boolean = commands.kill(pid, options.user)
    override fun close() { closed = true; stream.close() }
    private fun checked(result: CommandResult): CommandResult {
        if (result.exitCode != 0) throw CommandExitException(result)
        return result
    }
}
