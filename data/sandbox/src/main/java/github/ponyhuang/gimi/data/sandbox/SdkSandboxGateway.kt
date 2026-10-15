package github.ponyhuang.gimi.data.sandbox

import dev.e2b.sdk.AuthenticationException
import dev.e2b.sdk.CodeOptions
import dev.e2b.sdk.CommandExitException
import dev.e2b.sdk.CommandOptions
import dev.e2b.sdk.CreateOptions
import dev.e2b.sdk.E2BClient
import dev.e2b.sdk.E2BConfig
import dev.e2b.sdk.E2BException
import dev.e2b.sdk.FileSizeLimitException
import dev.e2b.sdk.NotFoundException
import dev.e2b.sdk.ProtocolException
import dev.e2b.sdk.PartialOutputException
import dev.e2b.sdk.RateLimitException
import dev.e2b.sdk.Sandbox
import dev.e2b.sdk.TransportException
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxFileEntry
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** 固定适配仓库内独立 JVM SDK，业务调用者不依赖其公开类型。 */
@Singleton
class SdkSandboxGatewayFactory @Inject constructor(@SandboxRuntimeScope private val scope: CoroutineScope) : SandboxGatewayFactory {
    override fun open(connection: SandboxConnection): SandboxGateway = SdkSandboxGateway(connection, scope)
}

/** 聊天独占客户端；取消 HTTP 与销毁沙盒分别处理，不关闭其他聊天的请求。 */
internal class SdkSandboxGateway(private val connection: SandboxConnection, private val scope: CoroutineScope, clientOverride: E2BClient? = null) : SandboxGateway {
    private val client = clientOverride ?: E2BClient(config())
    private val closed = AtomicBoolean(false)
    private val resourceLock = Any()
    private var sandbox: Sandbox? = null

    private fun config(requestTimeout: Int = 60): E2BConfig = E2BConfig.builder()
        .apiKey(connection.apiKey).domain(connection.domain).requestTimeoutSeconds(requestTimeout).build()

    override suspend fun create() = sdk(SandboxGatewayFailure.TEMPLATE) {
        if (closed.get()) throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)
        // 旧资源可能被外部暂停而非真正过期，替换前按已知 ID 清理，不能遗留暂停实例。
        synchronized(resourceLock) { sandbox }?.let { client.kill(it.sandboxId) }
        val created = client.create(CreateOptions(template = connection.template, timeoutSeconds = connection.idleTimeoutSeconds, envs = connection.envs.toMap()))
        synchronized(resourceLock) {
            if (closed.get()) {
                // 取消与创建响应竞争时，已知 ID 仍须清理；无法取得 ID 时由远端 TTL 兜底。
                E2BClient(config(5)).use { it.kill(created.sandboxId) }
                throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)
            }
            sandbox = created
        }
    }

    override suspend fun isRunning(): Boolean = sdk { current().isRunning() }
    override suspend fun refreshTimeout() = sdk { current().setTimeout(connection.idleTimeoutSeconds) }

    override suspend fun execute(operation: SandboxOperation): SandboxOutput = sdk(
        when (operation) {
            is SandboxOperation.Code -> SandboxGatewayFailure.INTERPRETER
            is SandboxOperation.ReadFile, is SandboxOperation.ReadBytes, is SandboxOperation.ListFiles -> SandboxGatewayFailure.FILE_NOT_FOUND
            else -> SandboxGatewayFailure.FAILED
        },
    ) {
        val resource = current()
        when (operation) {
            is SandboxOperation.WriteBytes -> {
                if (operation.content.size > MAX_FILE_BYTES) throw SandboxGatewayException(SandboxGatewayFailure.TOO_LARGE)
                resource.files.write(operation.path, operation.content)
                SandboxOutput.WrittenFile(operation.path)
            }
            is SandboxOperation.ReadBytes -> SandboxOutput.ReadBytes(operation.path, resource.files.readBytes(operation.path, operation.maxBytes))
            is SandboxOperation.BackgroundCommand -> {
                // 收到 start/PID 后只关闭本地流，不 await、不 kill；远端进程由沙盒生命周期回收。
                resource.commands.start(operation.command, CommandOptions(cwd = operation.cwd, envs = operation.envs, timeoutSeconds = operation.timeoutSeconds)).use { handle ->
                    SandboxOutput.BackgroundCommand(operation.command, handle.pid, operation.port?.let { "https://${resource.getHost(it)}" })
                }
            }
            is SandboxOperation.Command -> {
                val result = try {
                    resource.commands.run(operation.command, CommandOptions(cwd = operation.cwd, envs = operation.envs, timeoutSeconds = operation.timeoutSeconds))
                } catch (exited: CommandExitException) {
                    exited.result
                }
                SandboxOutput.Command(operation.command, result.stdout, result.stderr, result.exitCode)
            }
            is SandboxOperation.Code -> {
                val result = resource.code.run(operation.code, CodeOptions(operation.language, operation.envs, operation.timeoutSeconds))
                SandboxOutput.Code(result.stdout, result.stderr, result.text, result.error)
            }
            is SandboxOperation.WriteFile -> {
                val bytes = operation.content.toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_FILE_BYTES) throw SandboxGatewayException(SandboxGatewayFailure.TOO_LARGE)
                resource.files.write(operation.path, bytes)
                SandboxOutput.WrittenFile(operation.path)
            }
            is SandboxOperation.ReadFile -> {
                val bytes = resource.files.readBytes(operation.path, MAX_FILE_BYTES)
                val text = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }
                catch (_: CharacterCodingException) { throw SandboxGatewayException(SandboxGatewayFailure.TEXT_ENCODING) }
                SandboxOutput.ReadFile(operation.path, text)
            }
            is SandboxOperation.ListFiles -> SandboxOutput.Files(operation.path, resource.files.list(operation.path).map {
                SandboxFileEntry(it.name, when (it.type) { "FILE_TYPE_FILE" -> "file"; "FILE_TYPE_DIRECTORY" -> "dir"; else -> "unknown" })
            })
        }
    }

    private fun current(): Sandbox = synchronized(resourceLock) {
        if (closed.get()) throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)
        sandbox ?: throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)
    }

    override fun cancelRequests() = client.cancelRequests()

    override suspend fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        client.cancelRequests()
        val resource = synchronized(resourceLock) { sandbox.also { sandbox = null } }
        try {
            if (resource != null) blocking { E2BClient(config(5)).use { it.kill(resource.sandboxId) } }
        } finally {
            client.close()
        }
    }

    private suspend fun <T> sdk(notFound: SandboxGatewayFailure = SandboxGatewayFailure.UNAVAILABLE, block: () -> T): T = blocking {
        try { block() }
        catch (error: E2BException) {
            val failure = if (error is PartialOutputException) error.failure else error
            throw SandboxGatewayException(when (failure) {
                is AuthenticationException -> SandboxGatewayFailure.AUTHENTICATION
                is RateLimitException -> SandboxGatewayFailure.RATE_LIMIT
                is FileSizeLimitException -> SandboxGatewayFailure.TOO_LARGE
                is NotFoundException -> notFound
                is ProtocolException -> SandboxGatewayFailure.PROTOCOL
                is TransportException -> if (failure.cause is InterruptedIOException) SandboxGatewayFailure.TIMEOUT else SandboxGatewayFailure.UNAVAILABLE
                else -> if (failure.statusCode == 502) SandboxGatewayFailure.UNAVAILABLE else SandboxGatewayFailure.FAILED
            }, (error as? PartialOutputException)?.partialOutput.orEmpty())
        }
        catch (error: IllegalStateException) {
            if (closed.get()) throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)
            throw error
        }
    }

    private suspend fun <T> blocking(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val worker = scope.launch {
            // 桥接同步 SDK 时必须把所有结果和异常回送调用协程；此处不把未知异常当作可恢复失败。
            try {
                val result = block()
                continuation.resume(result) { _, _, _ -> }
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation { client.cancelRequests(); worker.cancel() }
    }

    private companion object { const val MAX_FILE_BYTES = 1024 * 1024 }
}
