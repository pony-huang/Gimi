package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageReference
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalFileSearchResultsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun carouselShowsPreviewAndShowAllAction() {
        var shownResponseId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LocalFileSearchCarousel(
                    responseId = "response-1",
                    result = result(),
                    onOpenFile = {},
                    onShowAll = { shownResponseId = it },
                )
            }
        }

        composeRule.onNodeWithTag("local_file_search_carousel").assertIsDisplayed()
        composeRule.onNodeWithTag("local_file_search_show_all").performClick()
        composeRule.runOnIdle { assertEquals("response-1", shownResponseId) }
    }

    @Test
    fun allResultsPageShowsFilesAndOpensPdf() {
        var opened: LocalFileReference? = null
        composeRule.setContent {
            MaterialTheme {
                LocalFileSearchResultsScreen(
                    result = result(),
                    onBack = {},
                    onOpenFile = { opened = it },
                )
            }
        }

        composeRule.onNodeWithTag("local_file_search_all_results").assertIsDisplayed()
        composeRule.onNodeWithTag("local_file_search_item_report.pdf").performClick()
        composeRule.runOnIdle { assertEquals("report.pdf", opened?.displayName) }
    }

    @Test
    fun carouselImageOpensFullScreenPreview() {
        composeRule.setContent {
            MaterialTheme {
                LocalFileSearchCarousel(
                    responseId = "response-1",
                    result = result(),
                    onOpenFile = {},
                    onShowAll = {},
                )
            }
        }

        composeRule.onNodeWithTag("local_file_search_item_photo.jpg").performClick()

        composeRule.onNodeWithTag("chat_image_preview").assertIsDisplayed()
    }

    @Test
    fun remoteImageCarouselDisplaysStructuredImagesAndOpensPreview() {
        composeRule.setContent {
            MaterialTheme {
                RemoteImageCarousel(
                    result = RemoteImageResult(
                        images = listOf(
                            RemoteImageReference("https://example.com/photo"),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("remote_image_carousel").assertIsDisplayed()
        composeRule.onNodeWithTag("remote_image_item_0").performClick()
        composeRule.onNodeWithTag("chat_image_preview").assertIsDisplayed()
    }

    private fun result() = LocalFileSearchResult(
        query = "files",
        files = listOf(
            LocalFileReference(
                displayName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 100L,
                modifiedTimeMillis = 1L,
                category = "image",
                contentUri = "content://test/photo",
            ),
            LocalFileReference(
                displayName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 200L,
                modifiedTimeMillis = 2L,
                category = "document",
                contentUri = "content://test/report",
            ),
        ),
    )
}
