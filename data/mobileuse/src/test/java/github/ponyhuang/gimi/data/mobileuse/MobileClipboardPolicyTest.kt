package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileClipboardPolicyTest {
    @Test
    fun hiddenClipboardContentIsNeverTreatedAsEmpty() {
        assertEquals(MobileClipboardPolicy.Read.Unreadable, MobileClipboardPolicy.read(false, true, false))
        assertEquals(MobileClipboardPolicy.Read.Unreadable, MobileClipboardPolicy.read(false, false, true))
        assertEquals(MobileClipboardPolicy.Read.Empty, MobileClipboardPolicy.read(false, false, false))
    }

    @Test
    fun userCopiesArePreservedAndOnlyOurClipIsClearedOrRestored() {
        assertEquals(MobileClipboardPolicy.Restore.Leave, MobileClipboardPolicy.restore(false, MobileClipboardPolicy.Now.Other))
        assertEquals(MobileClipboardPolicy.Restore.Leave, MobileClipboardPolicy.restore(false, MobileClipboardPolicy.Now.Unreadable))
        assertEquals(MobileClipboardPolicy.Restore.PutBack, MobileClipboardPolicy.restore(false, MobileClipboardPolicy.Now.Ours))
        assertEquals(MobileClipboardPolicy.Restore.Clear, MobileClipboardPolicy.restore(true, MobileClipboardPolicy.Now.Ours))
        assertEquals(MobileClipboardPolicy.Restore.PutBack, MobileClipboardPolicy.restore(false, MobileClipboardPolicy.Now.Empty))
    }
}
