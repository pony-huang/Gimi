package github.ponyhuang.gimi.data.recommendation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecommendationContextCollectorTest {
    @Test
    fun mergesAvailableContributorsAndSkipsFailures() = runTest {
        val collector = RecommendationContextCollector(
            contributors = setOf(
                RecommendationContextContributor { mapOf("locale" to "zh-CN") },
                RecommendationContextContributor { error("permission denied") },
                RecommendationContextContributor { emptyMap() },
            ),
        )

        val context = collector.currentContext()

        assertEquals("zh-CN", context.values["locale"])
        assertFalse(context.values.containsKey("permission denied"))
    }
}
