package github.ponyhuang.gimi.domain.memory.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryModelsTest {
    @Test
    fun defaultConfigurationUsesLocalMemoryWithoutCredential() {
        val configuration = MemoryConfiguration()

        assertTrue(configuration.memoryEnabled)
        assertFalse(configuration.mem0Enabled)
        assertEquals("", configuration.apiKey)
    }

    @Test
    fun runtimeFailureCarriesOnlyOperationCategory() {
        assertEquals(
            MemoryOperation.SEARCH,
            MemoryRuntimeFailure(MemoryOperation.SEARCH).operation,
        )
        assertEquals(
            MemoryOperation.WRITE,
            MemoryRuntimeFailure(MemoryOperation.WRITE).operation,
        )
    }
}
