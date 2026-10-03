package github.ponyhuang.gimi.feature.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAppWindowGeometryTest {
    private val bounds = BackgroundAppWindowBounds(8f, 24f, 400f, 880f, 200f)
    private val initial = BackgroundAppWindowGeometry(40f, 80f, 280f, 0.5f, 112f)

    @Test
    fun slowMovementAccumulatesSubpixelDeltas() {
        val moved = (1..10).fold(initial) { geometry, _ -> geometry.moveBy(0.2f, 0.3f, bounds) }
        assertEquals(42f, moved.left, 0.001f)
        assertEquals(83f, moved.top, 0.001f)
    }

    @Test
    fun cornerResizeKeepsTopLeftAndContentAspectRatio() {
        val resized = initial.resizeBy(20f, 40f, bounds)
        assertEquals(300f, resized.width, 0.001f)
        assertEquals(initial.left, resized.left, 0f)
        assertEquals(initial.top, resized.top, 0f)
        assertEquals(0.5f, resized.width / (resized.height - resized.chromeHeight), 0.001f)
    }

    @Test
    fun oversizedResizeStaysInsideSafeArea() {
        val resized = initial.resizeBy(1000f, 1000f, bounds)
        assertTrue(resized.left + resized.width <= bounds.right)
        assertTrue(resized.top + resized.height <= bounds.bottom)
        assertEquals(initial.left, resized.left, 0f)
    }

    @Test
    fun keyboardConstraintDoesNotDestroyRememberedSize() {
        val keyboard = bounds.copy(bottom = 430f)
        val visible = initial.constrain(keyboard)
        assertTrue(visible.top + visible.height <= keyboard.bottom)
        assertEquals(initial, initial.constrain(bounds))
        assertEquals(280f, initial.width, 0f)
    }

    @Test
    fun shrinkStopsAtMinimumWidthWhenSpaceAllows() {
        assertEquals(200f, initial.resizeBy(-1000f, -1000f, bounds).width, 0f)
    }
}
