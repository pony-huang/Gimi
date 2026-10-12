package github.ponyhuang.gimi.data.sandbox

import github.ponyhuang.gimi.domain.sandbox.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSandboxExecutionRepositoryTest {
    private class Settings : SandboxSettingsRepository {
        override val configuration = MutableStateFlow(SandboxConfiguration(true, SandboxConnection(apiKey = "fake-key")))
        override val revision = MutableStateFlow(0L)
        override suspend fun setEnabled(enabled: Boolean) { configuration.value = configuration.value.copy(enabled = enabled); revision.value++ }
        override suspend fun saveConnection(connection: SandboxConnection) { configuration.value = configuration.value.copy(connection = connection); revision.value++ }
    }
    private class Gateway : SandboxGateway {
        var creates = 0; var refreshes = 0; var shutdowns = 0; var cancels = 0; var calls = 0
        var running = true; var createDelay = 0L; var executeDelay = 0L
        var wait: CompletableDeferred<Unit>? = null
        var refreshFailure = false
        var error: SandboxGatewayFailure? = null
        override suspend fun create() { delay(createDelay); creates++; running = true }
        override suspend fun isRunning() = running
        override suspend fun refreshTimeout() { refreshes++; if (refreshFailure) throw SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE) }
        override suspend fun execute(operation: SandboxOperation): SandboxOutput {
            calls++; delay(executeDelay); wait?.await()
            error?.let { throw SandboxGatewayException(it, "captured output") }
            return SandboxOutput.Command("echo", "ok", "", 0)
        }
        override fun cancelRequests() { cancels++; wait?.completeExceptionally(SandboxGatewayException(SandboxGatewayFailure.UNAVAILABLE)) }
        override suspend fun shutdown() { shutdowns++ }
    }
    private class Factory(private val configure: (Gateway, Int) -> Unit = { _, _ -> }) : SandboxGatewayFactory {
        val instances = mutableListOf<Gateway>()
        val connections = mutableListOf<SandboxConnection>()
        override fun open(connection: SandboxConnection): SandboxGateway = Gateway().also {
            configure(it, instances.size); instances += it; connections += connection
        }
    }
    private val key = SandboxSessionKey("user", "chat-1")
    private val command = SandboxOperation.Command("echo ok")

    @Test fun disabledAndPendingConfigurationsNeverOpenGateway() = runTest {
        val settings = Settings(); val factory = Factory()
        val repository = SessionSandboxExecutionRepository(settings, factory, backgroundScope)
        settings.setEnabled(false)
        assertFalse(repository.execute(key, command).success)
        settings.configuration.value = SandboxConfiguration(enabled = true)
        assertFalse(repository.execute(key, command).success)
        assertTrue(factory.instances.isEmpty())
    }
    @Test fun sharesEnvironmentAcrossTurnsAndSeparatesUsersAndChats() = runTest {
        val factory = Factory(); val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        assertTrue(repository.execute(key, command).success)
        assertTrue(repository.execute(key, SandboxOperation.ReadFile("file.txt")).success)
        repository.execute(SandboxSessionKey("user", "chat-2"), command)
        repository.execute(SandboxSessionKey("other-user", "chat-1"), command)
        assertEquals(3, factory.instances.size)
        assertEquals(1, factory.instances[0].creates)
        assertEquals(2, factory.instances[0].calls)
    }
    @Test fun concurrentFirstCallsCreateExactlyOneEnvironment() = runTest {
        val factory = Factory { gateway, _ -> gateway.createDelay = 1000 }
        val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        val first = async { repository.execute(key, command) }
        val second = async { repository.execute(key, command) }
        runCurrent(); advanceTimeBy(1000); runCurrent()
        assertTrue(first.await().success); assertTrue(second.await().success)
        assertEquals(1, factory.instances.size)
        assertEquals(1, factory.instances.single().creates)
    }
    @Test fun heartbeatsCoverLongOperationsAndRestartIdleWindow() = runTest {
        val settings = Settings()
        settings.configuration.value = settings.configuration.value.copy(connection = SandboxConnection(apiKey = "key", idleTimeoutSeconds = 1))
        val factory = Factory { gateway, _ -> gateway.executeDelay = 1800 }
        val repository = SessionSandboxExecutionRepository(settings, factory, backgroundScope)
        val result = async { repository.execute(key, command) }
        runCurrent(); advanceTimeBy(1800); runCurrent()
        assertTrue(result.await().success)
        assertTrue(factory.instances.single().refreshes >= 5)
        val refreshes = factory.instances.single().refreshes
        advanceTimeBy(5000); runCurrent()
        assertEquals(refreshes, factory.instances.single().refreshes)
    }
    @Test fun keepaliveFailureDoesNotChangeSuccessfulToolResult() = runTest {
        val factory = Factory { gateway, _ -> gateway.refreshFailure = true }
        val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        assertTrue(repository.execute(key, command).success)
    }
    @Test fun expiredEnvironmentIsRecreatedBeforeOperationAndReported() = runTest {
        val factory = Factory(); val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        repository.execute(key, command)
        factory.instances.single().running = false
        val result = repository.execute(key, command)
        assertTrue(result.environmentReset)
        assertEquals(2, factory.instances.single().creates)
        assertEquals(2, factory.instances.single().calls)
    }
    @Test fun missingFileDoesNotCauseEnvironmentRecreationOrReplay() = runTest {
        val factory = Factory(); val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        repository.execute(key, command)
        factory.instances.single().error = SandboxGatewayFailure.FILE_NOT_FOUND
        val result = repository.execute(key, SandboxOperation.ReadFile("missing.txt"))
        assertFalse(result.success)
        assertEquals("captured output", result.partialOutput)
        assertEquals(1, factory.instances.single().creates)
        assertEquals(2, factory.instances.single().calls)
    }
    @Test fun disablingCancelsAndClosesOldEnvironmentAndBlocksCachedCalls() = runTest {
        val settings = Settings(); val factory = Factory()
        val repository = SessionSandboxExecutionRepository(settings, factory, backgroundScope)
        repository.execute(key, command)
        settings.setEnabled(false); runCurrent()
        assertEquals(1, factory.instances.single().cancels)
        assertEquals(1, factory.instances.single().shutdowns)
        assertFalse(repository.execute(key, command).success)
        assertEquals(1, factory.instances.single().calls)
    }
    @Test fun configurationChangeInvalidatesAndCreatesWithNewEnvironmentVariables() = runTest {
        val settings = Settings(); val factory = Factory()
        val repository = SessionSandboxExecutionRepository(settings, factory, backgroundScope)
        repository.execute(key, command)
        settings.saveConnection(SandboxConnection(apiKey = "new-key", template = "custom", envs = mapOf("EMPTY" to "")))
        runCurrent()
        assertTrue(repository.execute(key, command).success)
        assertEquals(1, factory.instances[0].shutdowns)
        assertEquals("custom", factory.connections[1].template)
        assertEquals("", factory.connections[1].envs["EMPTY"])
    }
    @Test fun cancellationOnlyDestroysItsConversationAndNextCallStartsFresh() = runTest {
        val waiting = CompletableDeferred<Unit>()
        val factory = Factory { gateway, index -> if (index == 0) gateway.wait = waiting }
        val repository = SessionSandboxExecutionRepository(Settings(), factory, backgroundScope)
        val first = async { repository.execute(key, command) }
        runCurrent()
        repository.execute(SandboxSessionKey("user", "other"), command)
        first.cancel(); runCurrent()
        assertTrue(first.isCancelled)
        assertEquals(1, factory.instances[0].shutdowns)
        assertEquals(0, factory.instances[1].cancels)
        assertEquals(0, factory.instances[1].shutdowns)
        assertTrue(repository.execute(key, command).success)
        assertEquals(3, factory.instances.size)
    }
}
