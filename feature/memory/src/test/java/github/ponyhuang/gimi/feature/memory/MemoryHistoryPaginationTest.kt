package github.ponyhuang.gimi.feature.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryHistoryPaginationTest {
    @Test
    fun `requests the next page when the last visible memory approaches the end`() {
        assertTrue(
            shouldLoadNextMemoryPage(
                lastVisibleMemoryIndex = 8,
                memoryCount = 10,
                hasNextPage = true,
                loadingNextPage = false,
            ),
        )
    }

    @Test
    fun `does not request another page while the current request is running`() {
        assertFalse(
            shouldLoadNextMemoryPage(
                lastVisibleMemoryIndex = 9,
                memoryCount = 10,
                hasNextPage = true,
                loadingNextPage = true,
            ),
        )
    }
}
