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
    fun snapshotRequiresExactlySixUniqueNonBlankRecommendations() {
        val items = (1..6).map { index -> recommendation("task-$index") }

        val snapshot = RecommendationSnapshot(items, generatedAtEpochMillis = 100L)

        assertEquals(items, snapshot.items)
        // 数量不足触发 size require。
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationSnapshot(items.take(5), generatedAtEpochMillis = 100L)
        }
        // 6 条但文案重复触发 unique require。
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationSnapshot(items.dropLast(1) + recommendation("task-1"), 100L)
        }
        // 6 条但含空白文案触发 non-blank require。
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
