package github.ponyhuang.gimi.data.memory

import app.cash.turbine.test
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRuntimeStatusTest {

    @Test
    fun `deduplicates consecutive failures until the operation recovers`() = runTest {
        val status = DefaultMemoryRuntimeStatus()

        status.failures.test {
            status.reportFailure(MemoryOperation.SEARCH)
            assertEquals(MemoryOperation.SEARCH, awaitItem().operation)
            status.reportFailure(MemoryOperation.SEARCH)
            expectNoEvents()
            status.reportSuccess(MemoryOperation.SEARCH)
            status.reportFailure(MemoryOperation.SEARCH)
            assertEquals(MemoryOperation.SEARCH, awaitItem().operation)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
