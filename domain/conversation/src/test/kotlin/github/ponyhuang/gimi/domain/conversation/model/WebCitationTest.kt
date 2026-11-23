package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WebCitationTest {
    @Test fun metadataSurvivesRoundTripAndSkipsMalformedEntries() {
        val citation = WebCitation(WebSource("https://example.org/story", "Story"), 4)
        val encoded = WebCitationMetadata.encode(listOf(citation))
        assertEquals(listOf(citation), WebCitationMetadata.decode(
            encoded + listOf(mapOf("url" to "javascript:alert(1)", "endOffset" to 4)),
        ))
        assertEquals(emptyList<WebCitation>(), WebCitationMetadata.decode(null))
    }

    @Test fun convertsCodePointOffsetsWithoutSplittingEmoji() {
        assertEquals(3, webCitationUtf16Offset("中🙂文", 2))
        assertEquals(null, webCitationUtf16Offset("中🙂文", 4))
    }
}
