package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileOcrElementTest {
    @Test
    fun textBoxesDoNotClaimNativeClickOrTextInputCapability() {
        val element = ocrElement("o1", "搜索", MobileBounds(-10, 10, 200, 300), 100, 200)!!
        assertEquals(MobileBounds(0, 10, 100, 200), element.bounds)
        assertEquals("ocr", element.source)
        assertNull(element.clickable)
        assertEquals(listOf("tap"), element.actions)
    }

    @Test
    fun invisibleAndEmptyTextBoxesAreDropped() {
        assertNull(ocrElement("o1", "", MobileBounds(0, 0, 20, 20), 100, 200))
        assertNull(ocrElement("o1", "搜索", MobileBounds(200, 0, 300, 20), 100, 200))
    }
}
