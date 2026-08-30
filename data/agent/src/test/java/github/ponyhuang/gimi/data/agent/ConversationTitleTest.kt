package github.ponyhuang.gimi.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTitleTest {
    @Test
    fun provisionalNormalizesWhitespaceAndTruncates() {
        assertEquals("first message with spaces", ConversationTitle.provisional(" first\n message   with spaces "))
        assertEquals(
            "a".repeat(ConversationTitle.PROVISIONAL_MAX_LENGTH) + "…",
            ConversationTitle.provisional("a".repeat(ConversationTitle.PROVISIONAL_MAX_LENGTH + 1)),
        )
    }

    @Test
    fun generatedRemovesQuotesAndLineBreaks() {
        assertEquals("A compact title", ConversationTitle.generated("\"A compact\ntitle\""))
        assertNull(ConversationTitle.generated("   "))
    }
}
