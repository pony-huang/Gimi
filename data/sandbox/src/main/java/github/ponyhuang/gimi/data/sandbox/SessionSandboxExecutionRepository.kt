package github.ponyhuang.gimi.data.sandbox

import android.util.Log
import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionResult
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxSessionKey
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 按聊天隔离环境并在执行时复查开关；缓存过的工具不能绕过关闭或配置变更。 */
@Singleton
class SessionSandboxExecutionRepository @Inject constructor(
    private val settings: SandboxSettingsRepository,
    private val factory: SandboxGatewayFactory,
    @SandboxRuntimeScope private val scope: CoroutineScope,
) : SandboxExecutionRepository {
    private class Session(val connection: SandboxConnection, val gateway: SandboxGateway) {
        val mutex = Mutex()
        var created = false
        @Volatile var invalidated = false
    }
    private val registryLock = Any()
    private val sessions = mutableMapOf<SandboxSessionKey, Session>()

    init {
        scope.launch { settings.configuration.collect { evictChanged(settings.configuration.value) } }
    }

    override suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult {
        val configuration = settings.configuration.value
        if (!configuration.enabled) return failure("E2B sandbox is disabled. Enable it in sandbox settings.")
        if (!configuration.isConfigured) return failure("E2B sandbox is not configured. Complete sandbox settings first.")
        if (session.sessionId.isBlank() || session.userId.isBlank()) return failure("A persistent conversation identity is required.")
        evictChanged(settings.configuration.value)
        val resource = synchronized(registryLock) {
            if (settings.configuration.value != configuration) return@synchronized null
            sessions.getOrPut(session) { Session(configuration.connection, factory.open(configuration.connection)) }
        } ?: return failure("Sandbox settings changed. Request was not started.")
        var reset = false
        try {
            return resource.mutex.withLock {
                if (!isCurrent(resource)) return@withLock failure("Sandbox settings changed. Request was not started.")
                if (!resource.created) {
                    resource.gateway.create()
                    resource.created = true
                } else if (!resource.gateway.isRunning()) {
                    resource.gateway.create()
                    reset = true
                }
                if (!isCurrent(resource)) return@withLock failure("Sandbox settings changed. Request was not started.")
                refresh(resource)
                coroutineScope {
                    val heartbeat = launch {
                        while (true) {
                            delay(maxOf(resource.connection.idleTimeoutSeconds.toLong() * 500, 100))
                            refresh(resource)
                        }
                    }
                    try {
                        val output = resource.gateway.execute(operation)
                        if (!isCurrent(resource)) failure("Sandbox settings changed during execution. Do not automatically replay the operation.")
                        else SandboxExecutionResult(success = true, output = output, environmentReset = reset)
                    } finally {
                        heartbeat.cancelAndJoin()
                        if (isCurrent(resource)) refresh(resource)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            invalidate(session, resource)
            withContext(NonCancellable) { shutdown(resource) }
            throw cancelled
        } catch (error: SandboxGatewayException) {
            return SandboxExecutionResult(false, error = error.failure.message, partialOutput = error.partialOutput, environmentReset = reset)
        }
    }

    private fun isCurrent(session: Session): Boolean {
        val current = settings.configuration.value
        return !session.invalidated && current.isReady && current.connection == session.connection
    }

    private fun evictChanged(configuration: SandboxConfiguration) {
        val old = synchronized(registryLock) {
            sessions.entries.filter { !configuration.isReady || it.value.connection != configuration.connection }
                .map { (key, value) -> sessions.remove(key); value.invalidated = true; value }
        }
        old.forEach { session ->
            session.gateway.cancelRequests()
            scope.launch { shutdown(session) }
        }
    }

    private fun invalidate(key: SandboxSessionKey, session: Session) {
        synchronized(registryLock) {
            session.invalidated = true
            if (sessions[key] === session) sessions.remove(key)
        }
        session.gateway.cancelRequests()
    }

    private suspend fun refresh(session: Session) {
        if (!isCurrent(session)) return
        try { session.gateway.refreshTimeout() }
        catch (_: SandboxGatewayException) { Log.d("Sandbox", "Sandbox keep-alive failed.") }
    }

    private suspend fun shutdown(session: Session) {
        try { session.gateway.shutdown() }
        catch (_: dev.e2b.sdk.E2BException) { Log.d("Sandbox", "Sandbox cleanup failed; remote timeout will reclaim it.") }
        catch (_: SandboxGatewayException) { Log.d("Sandbox", "Sandbox cleanup failed; remote timeout will reclaim it.") }
    }

    private fun failure(message: String) = SandboxExecutionResult(false, error = message)
}
