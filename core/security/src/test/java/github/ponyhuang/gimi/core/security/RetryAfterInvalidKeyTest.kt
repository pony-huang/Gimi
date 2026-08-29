package github.ponyhuang.gimi.core.security

import java.security.InvalidKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryAfterInvalidKeyTest {
    @Test
    fun retriesOnceAfterInvalidKey() {
        var attempts = 0
        var resetCount = 0

        val result = retryAfterInvalidKey(
            resetKey = { resetCount++ },
            operation = {
                attempts++
                if (attempts == 1) throw InvalidKeyException("key invalidated")
                "encrypted"
            },
        )

        assertEquals("encrypted", result)
        assertEquals(2, attempts)
        assertEquals(1, resetCount)
    }

    @Test
    fun doesNotResetForOtherFailures() {
        var resetCount = 0

        assertThrows(IllegalStateException::class.java) {
            retryAfterInvalidKey(
                resetKey = { resetCount++ },
                operation = { throw IllegalStateException("storage unavailable") },
            )
        }

        assertEquals(0, resetCount)
    }
}
