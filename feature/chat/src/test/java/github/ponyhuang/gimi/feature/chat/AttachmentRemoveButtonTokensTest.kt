package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentRemoveButtonTokensTest {

    @Test
    fun removeActionKeepsCompactVisualInsideAccessibleTouchTarget() {
        assertEquals(48.dp, AttachmentRemoveButtonTokens.touchTargetSize)
        assertEquals(28.dp, AttachmentRemoveButtonTokens.visualSize)
        assertEquals(16.dp, AttachmentRemoveButtonTokens.iconSize)
    }
}
