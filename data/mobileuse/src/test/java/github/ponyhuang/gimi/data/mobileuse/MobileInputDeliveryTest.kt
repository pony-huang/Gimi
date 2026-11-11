package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MobileInputDeliveryTest {
    @Test
    fun onlyRejectedDownUsesTheCommandFallback() {
        val calls = mutableListOf<String>()
        assertTrue(MobileInputDelivery.pair({ calls += "down"; false }, { calls += "up"; true }, { calls += "fallback"; true }))
        assertEquals(listOf("down", "fallback"), calls)
        calls.clear()
        var releases = 0
        assertTrue(MobileInputDelivery.pair({ calls += "down"; true }, { calls += "up"; ++releases == 2 }, { calls += "fallback"; true }))
        assertEquals(listOf("down", "up", "up"), calls)
    }

    @Test
    fun partialReleaseAndUnknownDownNeverReplayTheAction() {
        var fallbacks = 0
        assertThrows(IllegalStateException::class.java) {
            MobileInputDelivery.pair({ true }, { false }, { fallbacks++; true })
        }
        assertThrows(IllegalStateException::class.java) {
            MobileInputDelivery.pair({ throw IllegalStateException("binder lost") }, { true }, { fallbacks++; true })
        }
        assertEquals(0, fallbacks)
    }
}
