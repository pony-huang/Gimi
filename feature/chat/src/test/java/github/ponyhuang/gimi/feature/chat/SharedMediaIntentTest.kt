package github.ponyhuang.gimi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedMediaIntentTest {

    @Test
    fun `dedupeAndLimitUris keeps order and removes duplicates`() {
        val input = listOf(
            "content://a",
            "content://b",
            "content://a",
            "content://c",
        )

        val result = dedupeAndLimitUris(input)

        assertEquals(
            listOf("content://a", "content://b", "content://c"),
            result,
        )
    }

    @Test
    fun `dedupeAndLimitUris caps items at maxItems`() {
        val input = (1..5).map { "content://image-$it" }

        val result = dedupeAndLimitUris(input, maxItems = 3)

        assertEquals(
            listOf("content://image-1", "content://image-2", "content://image-3"),
            result,
        )
    }

    @Test
    fun `dedupeAndLimitUris ignores blank entries`() {
        val input = listOf("", "content://a", " ", "content://a")

        val result = dedupeAndLimitUris(input)

        assertEquals(listOf("content://a"), result)
    }
}
