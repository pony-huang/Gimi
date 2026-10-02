package github.ponyhuang.gimi.domain.mobileuse

import org.junit.Assert.*
import org.junit.Test

class MobileDisplayCoordinatesTest {
    @Test fun letterboxIsExcludedAndCenterMapsToRealPixels() {
        assertNull(MobileDisplayCoordinates.map(100f, 250f, 600, 500, 1000, 2000))
        assertEquals(MobilePoint(500f, 1000f), MobileDisplayCoordinates.map(300f, 250f, 600, 500, 1000, 2000))
        assertNull(MobileDisplayCoordinates.map(425f, 250f, 600, 500, 1000, 2000))
    }

    @Test fun rotationUsesNewGeometryAndRejectsInvalidSizesAndCoordinates() {
        assertEquals(MobilePoint(1000f, 500f), MobileDisplayCoordinates.map(250f, 300f, 500, 600, 2000, 1000))
        assertNull(MobileDisplayCoordinates.map(Float.NaN, 0f, 500, 600, 2000, 1000))
        assertNull(MobileDisplayCoordinates.map(0f, 0f, 0, 600, 2000, 1000))
    }
}
