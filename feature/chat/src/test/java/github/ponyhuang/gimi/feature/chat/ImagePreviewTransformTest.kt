package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewTransformTest {

    @Test
    fun `zoom is clamped and pan stays inside scaled viewport`() {
        val transformed = updateImagePreviewTransform(
            current = ImagePreviewTransform(scale = 4.5f, offset = Offset.Zero),
            zoomChange = 2f,
            panChange = Offset(1_000f, -1_000f),
            viewportSize = Size(300f, 200f),
        )

        assertEquals(5f, transformed.scale)
        assertEquals(600f, transformed.offset.x)
        assertEquals(-400f, transformed.offset.y)
    }

    @Test
    fun `returning to minimum zoom resets pan`() {
        val transformed = updateImagePreviewTransform(
            current = ImagePreviewTransform(scale = 2f, offset = Offset(80f, 40f)),
            zoomChange = 0.1f,
            panChange = Offset(20f, 20f),
            viewportSize = Size(300f, 200f),
        )

        assertEquals(ImagePreviewTransform(), transformed)
    }
}
