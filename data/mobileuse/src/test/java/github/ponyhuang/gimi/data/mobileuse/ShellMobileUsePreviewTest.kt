package github.ponyhuang.gimi.data.mobileuse

import android.hardware.display.VirtualDisplay
import android.os.Process
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShellMobileUsePreviewTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun switchingPreviewPreservesDisplayGeometryAndTheFullResolutionCapture() {
        mockkStatic(Process::class)
        every { Process.myUid() } returns 2000
        val service = ShellMobileUseService()
        val display = mockk<VirtualDisplay>(relaxed = true) {
            every { getDisplay() } returns mockk { every { displayId } returns 10 }
        }
        val renderer = mockk<MobileDisplayRenderer>(relaxed = true)
        val geometry = MobileDisplayGeometry(1080, 2340, 480)
        fun field(name: String) = ShellMobileUseService::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("display").set(service, display)
        field("displayGeometry").set(service, geometry)
        field("renderer").set(service, renderer)
        val frames = field("frames").get(service) as MobileFrameBuffer
        frames.publish(frames.reset(), 1080, 2340, 100, byteArrayOf(1), intArrayOf(1))
        val captured = frames.latest()
        val surface = mockk<Surface> { every { isValid } returns true }

        // 预览切换不需要输入法 Binder，也不能调整副屏、替换截图 Surface 或清空最近一帧。
        service.setPreview(10, "expanded", surface, 264, 572, true)
        service.setPreview(10, "compact", surface, 112, 243, false)
        service.clearPreview(10, "expanded")
        service.clearPreview(10, "compact")

        assertArrayEquals(intArrayOf(1080, 2340, 480), service.geometry(10))
        assertSame(captured, frames.latest())
        verify(exactly = 0) { display.resize(any(), any(), any()) }
        verify(exactly = 0) { renderer.resize(any(), any(), any()) }
        verify(exactly = 1) { renderer.preview(surface, 264, 572) }
        verify(exactly = 1) { renderer.preview(surface, 112, 243) }
        verify(exactly = 1) { renderer.preview(null, 0, 0) }
    }
}
