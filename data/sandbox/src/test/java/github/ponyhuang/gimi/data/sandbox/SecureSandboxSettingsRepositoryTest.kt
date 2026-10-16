package github.ponyhuang.gimi.data.sandbox

import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SecureSandboxSettingsRepositoryTest {
    private class Storage(var value: SandboxConfiguration? = null) : SandboxSettingsStorage {
        var fail = false
        var writes = 0
        override fun read() = value
        override fun write(configuration: SandboxConfiguration) {
            if (fail) throw IOException("test storage failure")
            writes++; value = configuration
        }
    }
    @Test fun enablingWithoutKeyPersistsPendingState() = runTest {
        val storage = Storage()
        val repository = SecureSandboxSettingsRepository(storage)
        repository.setEnabled(true)
        assertTrue(repository.configuration.value.enabled)
        assertFalse(repository.configuration.value.isReady)
        assertEquals(1L, repository.revision.value)
        assertTrue(storage.value!!.enabled)
    }
    @Test fun savesNormalizedConnectionAndPreservesItWhenDisabledOrReloaded() = runTest {
        val storage = Storage()
        val repository = SecureSandboxSettingsRepository(storage)
        repository.setEnabled(true)
        repository.saveConnection(SandboxConnection(apiUrl = " http://localhost:3000/Control ", sandboxUrl = " http://localhost:3002/Proxy ", apiKey = " key ", envs = mapOf("EMPTY" to "", "TOKEN" to " secret ")))
        assertEquals("http://localhost:3000/Control", repository.configuration.value.connection.apiUrl)
        assertEquals("http://localhost:3002/Proxy", repository.configuration.value.connection.sandboxUrl)
        assertEquals("key", repository.configuration.value.connection.apiKey)
        assertEquals(" secret ", repository.configuration.value.connection.envs["TOKEN"])
        assertTrue(repository.configuration.value.isReady)
        val connection = repository.configuration.value.connection
        repository.setEnabled(false)
        assertEquals(connection, repository.configuration.value.connection)
        assertEquals(repository.configuration.value, SecureSandboxSettingsRepository(storage).configuration.value)
    }
    @Test fun failedWriteDoesNotPublishConfigurationOrRevision() = runTest {
        val storage = Storage(SandboxConfiguration(true, SandboxConnection(apiKey = "old")))
        val repository = SecureSandboxSettingsRepository(storage)
        storage.fail = true
        try { repository.saveConnection(SandboxConnection(apiKey = "new")); fail("Expected storage failure") }
        catch (_: IOException) { }
        assertEquals("old", repository.configuration.value.connection.apiKey)
        assertEquals(0L, repository.revision.value)
    }
    @Test fun unchangedCommitDoesNotInvalidateAgentCache() = runTest {
        val storage = Storage(SandboxConfiguration(true, SandboxConnection(apiKey = "key")))
        val repository = SecureSandboxSettingsRepository(storage)
        repository.setEnabled(true)
        repository.saveConnection(SandboxConnection(apiKey = "key"))
        assertEquals(0, storage.writes)
        assertEquals(0L, repository.revision.value)
    }
    @Test fun rejectsNamesThatBecomeDuplicatesAfterNormalization() = runTest {
        val repository = SecureSandboxSettingsRepository(Storage())
        try { repository.saveConnection(SandboxConnection(apiKey = "key", envs = mapOf("A" to "one", " A " to "two"))); fail("Expected duplicate rejection") }
        catch (_: IllegalArgumentException) { }
        assertFalse(repository.configuration.value.isConfigured)
    }
}
