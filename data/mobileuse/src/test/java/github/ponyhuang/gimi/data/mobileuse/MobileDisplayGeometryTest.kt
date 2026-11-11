package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun manualOrientationKeepsDensityAndIgnoresMainScreenRotation() {
        val portraitMain = MobileDisplayGeometry(1080, 2400, 440)
        val landscapeMain = MobileDisplayGeometry(2400, 1080, 440)
        val landscape = portraitMain.oriented(true)
        assertEquals(2400, landscape.width)
        assertEquals(1080, landscape.height)
        assertEquals(440, landscape.densityDpi)
        assertTrue(landscape.sameAs(landscapeMain.oriented(true)))
        assertTrue(portraitMain.sameAs(landscape.oriented(false)))
        assertTrue(portraitMain.sameAs(landscapeMain.oriented(false)))
        assertTrue(landscape.contains(2399, 1079))
        assertFalse(landscape.contains(1079, 2399))
    }

    @Test
    fun squareDisplayAndDensityChangesKeepValidGeometry() {
        assertTrue(MobileDisplayGeometry(1000, 1000, 320).oriented(true)
            .sameAs(MobileDisplayGeometry(1000, 1000, 320).oriented(false)))
        assertEquals(480, MobileDisplayGeometry(1200, 2600, 480).oriented(true).densityDpi)
    }

}
