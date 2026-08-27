package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteImageResultTest {

    @Test
    fun `parses nested structured image collection`() {
        val parsed = parseRemoteImageResult(
            response = mapOf(
                "data" to mapOf(
                    "note" to mapOf(
                        "imageList" to listOf(
                            mapOf(
                                "urlDefault" to
                                    "http://sns-webpic-qc.xhscdn.com/path/image-one!webp",
                                "width" to 1242,
                                "height" to 1656,
                            ),
                            mapOf(
                                "width" to 800,
                                "height" to 600,
                                "infoList" to listOf(
                                    mapOf(
                                        "imageScene" to "WB_PRV",
                                        "url" to "https://example.com/preview",
                                    ),
                                    mapOf(
                                        "imageScene" to "WB_DFT",
                                        "url" to "https://example.com/original",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, parsed?.images?.size)
        assertEquals(
            "https://sns-webpic-qc.xhscdn.com/path/image-one!webp",
            parsed?.images?.first()?.url,
        )
        assertEquals(1242, parsed?.images?.first()?.width)
        assertEquals(1656, parsed?.images?.first()?.height)
        assertEquals("https://example.com/original", parsed?.images?.last()?.url)
    }

    @Test
    fun `unwraps generated result wrapper and filters unsafe duplicate images`() {
        val parsed = parseRemoteImageResult(
            response = mapOf(
                "result" to mapOf(
                    "imageList" to listOf(
                        mapOf("urlDefault" to "javascript:alert(1)"),
                        mapOf("urlDefault" to "https://example.com/photo"),
                        mapOf("urlDefault" to "https://example.com/photo"),
                    ),
                ),
            ),
        )

        assertEquals(listOf("https://example.com/photo"), parsed?.images?.map { it.url })
    }

    @Test
    fun `ignores responses without structured image collection`() {
        assertNull(parseRemoteImageResult(mapOf("avatar" to "https://example.com/avatar")))
        assertNull(parseRemoteImageResult(mapOf("imageList" to emptyList<Any>())))
    }
}
