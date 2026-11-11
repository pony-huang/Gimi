package github.ponyhuang.gimi.domain.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileScreenCoordinatesTest {
    @Test
    fun endpointsStayInsideTheCurrentScreenshotAcrossSizes() {
        for (length in listOf(1, 1080, 2400, Int.MAX_VALUE)) {
            assertEquals(0, MobileScreenCoordinates.pixel(0, length))
            assertEquals(length - 1, MobileScreenCoordinates.pixel(999, length))
        }
        assertEquals(540, MobileScreenCoordinates.pixel(500, 1080))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oldPermilleEndpointIsRejectedInsteadOfClamped() {
        MobileScreenCoordinates.pixel(1000, 1080)
    }
}
