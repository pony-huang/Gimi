package github.ponyhuang.gimi.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerImeFocusPolicyTest {

    @Test
    fun clearsFocusAsSoonAsVisibleImeStartsClosing() {
        assertTrue(
            shouldClearComposerFocus(
                wasImeVisible = true,
                isFocused = true,
                previousImeBottom = 840,
                currentImeBottom = 780,
            ),
        )
    }

    @Test
    fun keepsFocusBeforeImeHasEverOpened() {
        assertFalse(
            shouldClearComposerFocus(
                wasImeVisible = false,
                isFocused = true,
                previousImeBottom = 0,
                currentImeBottom = 0,
            ),
        )
    }

    @Test
    fun keepsFocusWhileImeIsOpening() {
        assertFalse(
            shouldClearComposerFocus(
                wasImeVisible = true,
                isFocused = true,
                previousImeBottom = 780,
                currentImeBottom = 840,
            ),
        )
    }

    @Test
    fun restoresFocusWhenChildSurfaceReleasesExpandedComposer() {
        assertTrue(
            shouldRestoreComposerFocus(
                wasRetainExpanded = true,
                retainExpanded = false,
            ),
        )
    }

    @Test
    fun doesNotRestoreFocusWhileChildSurfaceRemainsOpen() {
        assertFalse(
            shouldRestoreComposerFocus(
                wasRetainExpanded = true,
                retainExpanded = true,
            ),
        )
    }

    @Test
    fun pendingRestoreWaitsUntilPreviousImeAnimationFinishes() {
        assertFalse(
            shouldPerformPendingComposerFocusRestore(
                isPending = true,
                imeBottom = 420,
            ),
        )
    }

    @Test
    fun pendingRestoreRunsAfterPreviousImeIsFullyHidden() {
        assertTrue(
            shouldPerformPendingComposerFocusRestore(
                isPending = true,
                imeBottom = 0,
            ),
        )
    }
}
