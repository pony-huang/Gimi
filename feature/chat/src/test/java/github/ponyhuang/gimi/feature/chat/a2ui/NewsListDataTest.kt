package github.ponyhuang.gimi.feature.chat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsListDataTest {
    @Test
    fun parsesEditorialOrderAndCapsLargeDigests() {
        val data = NewsListData.fromA2ui(
            mapOf(
                "heading" to "Today",
                "updatedAt" to "12:11",
                "items" to List(24) { index ->
                    mapOf(
                        "title" to "Headline $index",
                        "source" to "Source",
                        "timeLabel" to "12:0${index % 10}",
                        "category" to "News",
                        "url" to "https://example.com/$index",
                    )
                },
            ),
        )

        requireNotNull(data)
        assertEquals("Today", data.heading)
        assertEquals(NewsListData.MAX_ITEMS, data.items.size)
        assertEquals("Headline 0", data.items.first().title)
        assertEquals("Headline 19", data.items.last().title)
        assertEquals(NewsListIcon.NEWSPAPER, data.icon)
    }

    @Test
    fun skipsItemsWithoutTitlesAndRejectsEmptyDigest() {
        val data = NewsListData.fromA2ui(
            mapOf(
                "items" to listOf(
                    mapOf("source" to "Missing title"),
                    mapOf("title" to "Valid headline", "source" to "Reuters"),
                ),
            ),
        )

        requireNotNull(data)
        assertEquals(1, data.items.size)
        assertEquals("Valid headline", data.items.single().title)
        assertNull(NewsListData.fromA2ui(mapOf("items" to emptyList<Any>())))
    }

    @Test
    fun parsesOptionalHeaderIconWithSafeDefault() {
        val globe = requireNotNull(
            NewsListData.fromA2ui(
                mapOf(
                    "icon" to "globe",
                    "items" to listOf(mapOf("title" to "Headline")),
                ),
            ),
        )
        val fallback = requireNotNull(
            NewsListData.fromA2ui(
                mapOf(
                    "icon" to "unknown",
                    "items" to listOf(mapOf("title" to "Headline")),
                ),
            ),
        )

        assertEquals(NewsListIcon.GLOBE, globe.icon)
        assertEquals(NewsListIcon.NEWSPAPER, fallback.icon)
    }
}
