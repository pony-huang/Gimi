package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileDisplayGeometryTest {
    @Test
    fun coordinatesFollowCurrentDisplaySize() {
        val geometry = MobileDisplayGeometry(1080, 2400, 440)
        assertTrue(geometry.contains(1079, 2399))
        assertFalse(geometry.contains(1080, 2399))
        assertFalse(geometry.contains(1079, 2400))
    }

    @Test
    fun rotatedDisplayUsesNewBounds() {
        val geometry = MobileDisplayGeometry(2400, 1080, 440)
        assertTrue(geometry.contains(2399, 1079))
        assertFalse(geometry.contains(1079, 2399))
    }
}
