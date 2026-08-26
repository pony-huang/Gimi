package github.ponyhuang.gimi.plugin.xiaohongshu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XiaohongshuContractsTest {

    @Test
    fun profileTabAcceptsReferenceAliases() {
        assertEquals(ProfileTab.NOTE, ProfileTab.parse("笔记"))
        assertEquals(ProfileTab.FAVORITE, ProfileTab.parse("favorites"))
        assertEquals(ProfileTab.LIKED, ProfileTab.parse("liked"))
    }

    @Test
    fun profileTabRejectsUnknownValue() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileTab.parse("unknown")
        }
    }

    @Test
    fun searchFiltersRejectUnsupportedValuesBeforeOpeningBrowser() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchFilters(sortBy = "随机").validate()
        }
        SearchFilters(
            sortBy = "最多点赞",
            noteType = "图文",
            publishTime = "一周内",
            searchScope = "未看过",
            location = "同城",
        ).validate()
    }
}
