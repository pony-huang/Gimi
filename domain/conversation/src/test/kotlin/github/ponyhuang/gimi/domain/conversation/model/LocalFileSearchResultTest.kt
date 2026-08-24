package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFileSearchResultTest {

    @Test
    fun `parses ordered local file results from supported search response`() {
        val parsed = parseLocalFileSearchResult(
            toolName = "search_documents",
            response = mapOf(
                "success" to true,
                "query" to "报告",
                "results" to listOf(
                    mapOf(
                        "displayName" to "photo.jpg",
                        "mimeType" to "image/jpeg",
                        "sizeBytes" to 12L,
                        "modifiedTimeMillis" to 34L,
                        "category" to "document",
                        "contentUri" to "content://documents/photo",
                    ),
                    mapOf(
                        "displayName" to "report.pdf",
                        "mimeType" to "application/pdf",
                        "sizeBytes" to 56,
                        "modifiedTimeMillis" to 78,
                        "category" to "document",
                        "contentUri" to "content://documents/report",
                    ),
                ),
            ),
        )

        assertEquals("报告", parsed?.query)
        assertEquals(listOf("photo.jpg", "report.pdf"), parsed?.files?.map { it.displayName })
        assertEquals(56L, parsed?.files?.last()?.sizeBytes)
    }

    @Test
    fun `drops malformed and non content uri entries`() {
        val parsed = parseLocalFileSearchResult(
            toolName = "search_media_files",
            response = mapOf(
                "success" to true,
                "query" to "photo",
                "results" to listOf(
                    mapOf(
                        "displayName" to "unsafe.jpg",
                        "mimeType" to "image/jpeg",
                        "contentUri" to "file:///sdcard/unsafe.jpg",
                    ),
                    mapOf(
                        "displayName" to "safe.jpg",
                        "mimeType" to "image/jpeg",
                        "sizeBytes" to 1L,
                        "modifiedTimeMillis" to 2L,
                        "category" to "image",
                        "contentUri" to "content://media/safe",
                    ),
                ),
            ),
        )

        assertEquals(listOf("safe.jpg"), parsed?.files?.map { it.displayName })
    }

    @Test
    fun `ignores failed and unrelated tool responses`() {
        assertNull(parseLocalFileSearchResult("clock", mapOf("success" to true, "results" to emptyList<Any>())))
        assertNull(parseLocalFileSearchResult("search_documents", mapOf("success" to false)))
    }
}
