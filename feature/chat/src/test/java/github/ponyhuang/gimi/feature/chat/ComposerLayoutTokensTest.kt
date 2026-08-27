package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerLayoutTokensTest {

    @Test
    fun compactComposerUsesFourDpVerticalPadding() {
        assertEquals(4.dp, composerVerticalPadding(isExpanded = false))
    }

    @Test
    fun expandedComposerKeepsEightDpVerticalPadding() {
        assertEquals(8.dp, composerVerticalPadding(isExpanded = true))
    }
}
