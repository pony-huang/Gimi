package github.ponyhuang.gimi.domain.recommendation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationModelsTest {
    @Test
    fun settingsDefaultToEnabledTwoHourRefresh() {
        val settings = RecommendationSettings()

        assertTrue(settings.enabled)
        assertEquals(2, settings.intervalHours)
        assertEquals(listOf(1, 2, 6, 12, 24), RecommendationSettings.SUPPORTED_INTERVAL_HOURS)
    }

    @Test
    fun snapshotRequiresExactlyFiveUniqueNonBlankRecommendations() {
        val items = (1..5).map { index -> recommendation("task-$index") }

        val snapshot = RecommendationSnapshot(items, generatedAtEpochMillis = 100L)

        assertEquals(items, snapshot.items)
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationSnapshot(items.take(4), generatedAtEpochMillis = 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationSnapshot(items.dropLast(1) + recommendation("task-1"), 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationSnapshot(items.dropLast(1) + recommendation("  "), 100L)
        }
    }

    private fun recommendation(prompt: String) = AgentRecommendation(
        id = prompt,
        prompt = prompt,
        category = RecommendationCategory.GENERAL,
    )
}
