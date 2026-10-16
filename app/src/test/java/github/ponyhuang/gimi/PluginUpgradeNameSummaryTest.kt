package github.ponyhuang.gimi

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginUpgradeNameSummaryTest {
    @Test
    fun shortNamesRemainComplete() {
        assertEquals("天气、地图", pluginUpgradeNameSummary(listOf("天气", "地图"), "、"))
    }

    @Test
    fun longNamesAreLimitedIncludingEllipsis() {
        assertEquals("a".repeat(23) + "…", pluginUpgradeNameSummary(listOf("a".repeat(24), "地图"), "、"))
    }

    @Test
    fun exactLimitDoesNotAddEllipsis() {
        assertEquals("a".repeat(22) + "、b", pluginUpgradeNameSummary(listOf("a".repeat(22), "b"), "、"))
    }

    @Test
    fun emojiIsNotSplitByTruncation() {
        assertEquals("😀".repeat(23) + "…", pluginUpgradeNameSummary(listOf("😀".repeat(25), "地图"), "、"))
    }
}
