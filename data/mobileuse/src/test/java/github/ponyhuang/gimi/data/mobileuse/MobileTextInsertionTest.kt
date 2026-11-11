package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileTextInsertionTest {
    @Test
    fun insertsAtCursorAndPreservesBothSides() {
        assertEquals(MobileTextInsertion("你好世界", 2), MobileTextInsertion.plan("你世界", 1, 1, "好"))
        assertEquals(MobileTextInsertion("abc!", 4), MobileTextInsertion.plan("abc", 3, 3, "!"))
    }

    @Test
    fun replacesOnlySelectedTextIncludingReversedSelection() {
        assertEquals(MobileTextInsertion("a新d", 2), MobileTextInsertion.plan("abcd", 3, 1, "新"))
        assertEquals(MobileTextInsertion("ad", 1), MobileTextInsertion.plan("abcd", 1, 3, ""))
    }

    @Test
    fun keepsAndroidUtf16CursorForSupplementaryCharacters() {
        assertEquals(MobileTextInsertion("a😀b", 3), MobileTextInsertion.plan("ab", 1, 1, "😀"))
    }

    @Test
    fun unknownTextOrSelectionDoesNotGuessAnInsertionPoint() {
        assertNull(MobileTextInsertion.plan(null, 0, 0, "text"))
        assertNull(MobileTextInsertion.plan("abc", -1, -1, "text"))
        assertNull(MobileTextInsertion.plan("abc", 0, 4, "text"))
    }
}
