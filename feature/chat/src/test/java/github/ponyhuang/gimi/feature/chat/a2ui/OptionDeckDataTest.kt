package github.ponyhuang.gimi.feature.chat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OptionDeck 解析边界：场景文案、链接安全、数量上限、id 唯一性与事件载荷。 */
class OptionDeckDataTest {
    @Test
    fun parsesContextLabelsAndOptionalFields() {
        val data = requireNotNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "title" to "旅行方案",
                    "acceptLabel" to "想去",
                    "rejectLabel" to "换个地方",
                    "items" to listOf(
                        mapOf(
                            "id" to "hangzhou",
                            "title" to "杭州两日路线",
                            "meta" to "2 天 1 夜",
                            "description" to "西湖与九溪的慢游路线。",
                            "tags" to listOf("旅行", "慢节奏"),
                            "emoji" to "🧭",
                            "imageUrl" to "https://example.com/hangzhou.jpg",
                            "link" to mapOf(
                                "label" to "查看完整路线",
                                "url" to "https://example.com/routes/hangzhou",
                            ),
                        ),
                        mapOf("id" to "design", "title" to "视觉方向"),
                    ),
                ),
            ),
        )

        assertEquals("旅行方案", data.title)
        assertEquals("想去", data.acceptLabel)
        assertEquals("换个地方", data.rejectLabel)
        assertEquals(2, data.items.size)
        val travel = data.items.first()
        assertEquals("hangzhou", travel.id)
        assertEquals("2 天 1 夜", travel.meta)
        assertEquals(listOf("旅行", "慢节奏"), travel.tags)
        assertEquals("🧭", travel.emoji)
        assertEquals("https://example.com/hangzhou.jpg", travel.imageUrl)
        assertEquals(
            OptionDeckLink(
                label = "查看完整路线",
                url = "https://example.com/routes/hangzhou",
            ),
            travel.link,
        )
        val design = data.items.last()
        assertNull(design.meta)
        assertNull(design.description)
        assertEquals(emptyList<String>(), design.tags)
        assertNull(design.emoji)
        assertNull(design.imageUrl)
        assertNull(design.link)
    }

    @Test
    fun missingTitleFallsBackToHostDefaultLabel() {
        val data = requireNotNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "赞同",
                    "rejectLabel" to "不赞同",
                    "items" to listOf(
                        mapOf("id" to "a", "title" to "A"),
                        mapOf("id" to "b", "title" to "B"),
                    ),
                ),
            ),
        )
        assertNull(data.title)
    }

    @Test
    fun rejectsMissingContextActionLabels() {
        val items = listOf(
            mapOf("id" to "a", "title" to "A"),
            mapOf("id" to "b", "title" to "B"),
        )
        assertNull(OptionDeckData.fromA2ui(mapOf("items" to items)))
        assertNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "赞同",
                    "items" to items,
                ),
            ),
        )
    }

    @Test
    fun rejectsDeckWithFewerThanTwoItems() {
        assertNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "喜欢",
                    "rejectLabel" to "不喜欢",
                    "items" to listOf(mapOf("id" to "a", "title" to "A")),
                ),
            ),
        )
        assertNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "喜欢",
                    "rejectLabel" to "不喜欢",
                    "items" to emptyList<Any>(),
                ),
            ),
        )
    }

    @Test
    fun truncatesItemsToMaximum() {
        val items = (0 until OptionDeckData.MAX_ITEMS + 5).map { index ->
            mapOf("id" to "item-$index", "title" to "Item $index")
        }
        val data = requireNotNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "Keep",
                    "rejectLabel" to "Skip",
                    "items" to items,
                ),
            ),
        )

        assertEquals(OptionDeckData.MAX_ITEMS, data.items.size)
    }

    @Test
    fun rejectsDuplicateItemIds() {
        assertNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "赞同",
                    "rejectLabel" to "不赞同",
                    "items" to listOf(
                        mapOf("id" to "a", "title" to "A"),
                        mapOf("id" to "a", "title" to "A2"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun skipsInvalidItemsAndCleansTags() {
        val data = requireNotNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "喜欢",
                    "rejectLabel" to "不喜欢",
                    "items" to listOf(
                        mapOf("title" to "缺 id"),
                        mapOf("id" to " ", "title" to "空白 id"),
                        mapOf(
                            "id" to "b",
                            "title" to "B",
                            "tags" to listOf("有效", "  ", 42, null, "第三", "第四"),
                        ),
                        mapOf("id" to "a", "title" to "A"),
                    ),
                ),
            ),
        )

        assertEquals(2, data.items.size)
        assertEquals(listOf("有效", "第三", "第四"), data.items.first { it.id == "b" }.tags)
    }

    @Test
    fun invalidOrUnsafeLinksAreDroppedWithoutDroppingTheCard() {
        val data = requireNotNull(
            OptionDeckData.fromA2ui(
                mapOf(
                    "acceptLabel" to "同意",
                    "rejectLabel" to "不同意",
                    "items" to listOf(
                        mapOf(
                            "id" to "script",
                            "title" to "Script link",
                            "link" to mapOf(
                                "label" to "Open",
                                "url" to "javascript:alert(1)",
                            ),
                        ),
                        mapOf(
                            "id" to "relative",
                            "title" to "Relative link",
                            "link" to mapOf(
                                "label" to "Open",
                                "url" to "/details",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, data.items.size)
        assertNull(data.items[0].link)
        assertNull(data.items[1].link)
    }

}
