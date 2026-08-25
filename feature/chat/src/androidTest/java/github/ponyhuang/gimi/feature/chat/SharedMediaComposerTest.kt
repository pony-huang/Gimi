package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SharedMediaComposerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedUrisAreConsumedAfterTheComposerProcessesThem() {
        var consumed = 0

        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { true },
                    onStopClick = {},
                    isGenerating = false,
                    sharedMediaUris = listOf(Uri.parse("content://missing")),
                    onSharedMediaConsumed = { consumed++ },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, consumed)
    }

    @Test
    fun emptySharedUrisAreNotConsumed() {
        var consumed = 0

        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { true },
                    onStopClick = {},
                    isGenerating = false,
                    sharedMediaUris = emptyList(),
                    onSharedMediaConsumed = { consumed++ },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, consumed)
    }
}
