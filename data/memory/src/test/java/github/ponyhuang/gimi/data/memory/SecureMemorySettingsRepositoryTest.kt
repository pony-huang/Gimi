package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureMemorySettingsRepositoryTest {

    @Test
    fun `defaults to local memory when storage is empty`() {
        val repository = SecureMemorySettingsRepository(FakeMemorySettingsStorage())

        assertEquals(MemoryConfiguration(), repository.configuration.value)
    }

    @Test
    fun `cannot enable Mem0 without a token`() = runTest {
        val repository = SecureMemorySettingsRepository(FakeMemorySettingsStorage())

        var failure: Throwable? = null
        try {
            repository.save(memoryEnabled = true, mem0Enabled = true, apiKey = null)
        } catch (error: Throwable) {
            failure = error
        }
        assertEquals(IllegalArgumentException::class, failure?.let { it::class })
    }

    @Test
    fun `blank token update preserves encrypted stored token`() = runTest {
        val storage = FakeMemorySettingsStorage()
        val repository = SecureMemorySettingsRepository(storage)
        repository.save(memoryEnabled = true, mem0Enabled = false, apiKey = " secret ")

        repository.save(memoryEnabled = true, mem0Enabled = true, apiKey = null)

        assertEquals(MemoryConfiguration(mem0Enabled = true, apiKey = "secret"), repository.configuration.value)
        assertEquals("secret", storage.value?.apiKey)
    }

    @Test
    fun `master switch off is persisted and re-read`() = runTest {
        val storage = FakeMemorySettingsStorage()
        val repository = SecureMemorySettingsRepository(storage)

        repository.save(memoryEnabled = false, mem0Enabled = true, apiKey = "secret")

        assertEquals(false, repository.configuration.value.memoryEnabled)
        assertEquals(false, storage.value?.memoryEnabled)

        val reloaded = SecureMemorySettingsRepository(FakeMemorySettingsStorage(storage.value))
        assertEquals(false, reloaded.configuration.value.memoryEnabled)
    }
}

private class FakeMemorySettingsStorage(
    initialValue: StoredMemorySettings? = null,
) : MemorySettingsStorage {
    var value: StoredMemorySettings? = initialValue

    override fun read(): StoredMemorySettings? = value

    override fun write(value: StoredMemorySettings) {
        this.value = value
    }
}
