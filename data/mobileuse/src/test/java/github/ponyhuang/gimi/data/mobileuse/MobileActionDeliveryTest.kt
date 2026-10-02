package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileActionDeliveryTest {
    @Test
    fun observationTimeoutDoesNotEraseDeliveredAction() {
        val delivery = MobileActionDelivery(true)
        var calls = 0
        var invalidated = false
        assertTrue(delivery.attempt({ 100 }, { invalidated = true }) { calls++; true })
        assertTrue(invalidated)
        assertEquals("delivered", delivery.status)
        assertEquals(100L, delivery.completedAtMs)
        assertEquals(1, calls)
    }

    @Test
    fun ambiguousCommandFailureIsNotRetriedAndConsumesObservation() {
        val delivery = MobileActionDelivery(true)
        var calls = 0
        var invalidated = false
        try {
            delivery.attempt({ 100 }, { invalidated = true }) { calls++; throw IllegalStateException("transport") }
        } catch (_: IllegalStateException) { }
        assertTrue(invalidated)
        assertEquals("unknown", delivery.status)
        assertNull(delivery.completedAtMs)
        assertEquals(1, calls)
    }

    @Test
    fun rejectedNodeActionDoesNotClaimDeliveryButPriorFocusStillCounts() {
        val delivery = MobileActionDelivery(true)
        delivery.attempt({ 100 }, {}) { false }
        assertEquals("rejected", delivery.status)
        delivery.attempt({ 100 }, {}) { true }
        delivery.attempt({ 200 }, {}) { false }
        assertEquals("delivered", delivery.status)
    }
}
