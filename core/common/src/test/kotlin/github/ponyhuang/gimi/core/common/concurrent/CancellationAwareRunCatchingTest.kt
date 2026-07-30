package github.ponyhuang.gimi.core.common.concurrent

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationAwareRunCatchingTest {

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`() {
        cancellationAwareRunCatching<Unit> {
            throw CancellationException("cancel")
        }
    }

    @Test
    fun `recoverable failure remains available to the caller`() {
        val result = cancellationAwareRunCatching<Unit> {
            throw IOException("offline")
        }

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }
}
