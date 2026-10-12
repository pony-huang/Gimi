package dev.e2b.sdk

import java.io.Closeable

/** Sandbox resource with official-style command/file namespaces.
 * close() destroys the remote sandbox; client.close() only releases local connections.
 */
class Sandbox internal constructor(
    private val client: E2BClient,
    val sandboxId: String,
    private val domain: String,
    envd: Envd,
) : Closeable {
    val commands = Commands(envd)
    val files = Filesystem(envd)
    internal var ownsClient = false

    fun getInfo(): SandboxInfo = client.getInfo(sandboxId)
    fun setTimeout(timeoutSeconds: Int) = client.setTimeout(sandboxId, timeoutSeconds)
    @JvmOverloads fun pause(keepMemory: Boolean = true): Boolean = client.pause(sandboxId, keepMemory)
    fun kill(): Boolean = client.kill(sandboxId)
    fun isRunning(): Boolean = try {
        getInfo().state == "running"
    } catch (_: NotFoundException) { false }

    /** Application port host; does not include a scheme or envd credentials. */
    fun getHost(port: Int): String {
        require(port in 1..65535)
        return "$port-$sandboxId.$domain"
    }

    override fun close() {
        try { kill() } finally { if (ownsClient) client.close() }
    }

    companion object {
        @JvmStatic @JvmOverloads
        fun create(config: E2BConfig = E2BConfig.builder().build(), options: CreateOptions = CreateOptions()): Sandbox {
            val client = E2BClient(config)
            try { return client.create(options).apply { ownsClient = true } }
            catch (error: Throwable) { client.close(); throw error }
        }

        @JvmStatic @JvmOverloads
        fun connect(sandboxId: String, config: E2BConfig = E2BConfig.builder().build(), timeoutSeconds: Int? = null): Sandbox {
            val client = E2BClient(config)
            try { return client.connect(sandboxId, timeoutSeconds).apply { ownsClient = true } }
            catch (error: Throwable) { client.close(); throw error }
        }
    }
}
