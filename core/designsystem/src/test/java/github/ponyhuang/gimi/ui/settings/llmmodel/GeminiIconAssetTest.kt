package github.ponyhuang.gimi.ui.settings.llmmodel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class GeminiIconAssetTest {
    @Test
    fun usesLobeHubGeminiColorVector() {
        val vector = File("src/main/res/drawable/ic_model_provider_gemini.xml")
        val bitmap = File("src/main/res/drawable-nodpi/ic_model_provider_gemini.png")

        assertTrue("Gemini VectorDrawable 资源不存在", vector.isFile)
        assertFalse("Gemini 不应继续使用 PNG", bitmap.exists())

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(vector)
        val root = document.documentElement
        val paths = document.getElementsByTagName("path")
        val gradients = document.getElementsByTagName("gradient")

        assertEquals("24", root.getAttribute("android:viewportWidth"))
        assertEquals("24", root.getAttribute("android:viewportHeight"))
        assertEquals(4, paths.length)
        val pathData = (0 until paths.length)
            .map { index -> paths.item(index).attributes.getNamedItem("android:pathData").nodeValue }
        assertEquals(1, pathData.distinct().size)
        assertEquals(LOBE_HUB_ANDROID_PATH_DATA, pathData.first())
        assertEquals(3, gradients.length)
        assertEquals("#3186FF", paths.item(0).attributes.getNamedItem("android:fillColor").nodeValue)
    }

    private companion object {
        const val LOBE_HUB_ANDROID_PATH_DATA =
            "M20.616,10.835a14.147,14.147 0,0 1,-4.45,-3.001a14.111,14.111 0,0 1,-3.678,-6.452" +
                "a.503,.503 0,0 0,-.975,0a14.134,14.134 0,0 1,-3.679,6.452a14.155,14.155 0,0 1,-4.45,3.001" +
                "c-.65,.28 -1.318,.505 -2.002,.678a.502,.502 0,0 0,0,.975c.684,.172 1.35,.397 2.002,.677" +
                "a14.147,14.147 0,0 1,4.45,3.001a14.112,14.112 0,0 1,3.679,6.453a.502,.502 0,0 0,.975,0" +
                "c.172,-.685 .397,-1.351 .677,-2.003a14.145,14.145 0,0 1,3.001,-4.45" +
                "a14.113,14.113 0,0 1,6.453,-3.678a.503,.503 0,0 0,0,-.975" +
                "a13.245,13.245 0,0 1,-2.003,-.678z"
    }
}
