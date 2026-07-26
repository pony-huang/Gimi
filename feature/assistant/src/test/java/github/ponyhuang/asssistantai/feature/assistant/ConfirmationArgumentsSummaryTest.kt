package github.ponyhuang.asssistantai.feature.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationArgumentsSummaryTest {
    @Test
    fun `confirmation summary masks phone-like values and is bounded`() {
        val summary = confirmationArgumentsSummary(
            mapOf(
                "phoneNumber" to "13812345678",
                "message" to "a".repeat(200),
            ),
        )

        assertFalse(summary.contains("13812345678"))
        assertTrue(summary.contains("5678"))
        assertTrue(summary.length <= 160)
    }
}
