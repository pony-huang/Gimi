package github.ponyhuang.gimi.domain.appupdate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parse accepts v prefix and pre-release suffix`() {
        assertEquals(AppVersion(0, 2, 0, null), AppVersion.parse("v0.2.0"))
        assertEquals(AppVersion(0, 1, 1, "alpha"), AppVersion.parse("0.1.1-alpha"))
    }

    @Test
    fun `parse fills missing minor and patch with zero`() {
        assertEquals(AppVersion(1, 2, 0, null), AppVersion.parse("1.2"))
        assertEquals(AppVersion(1, 0, 0, null), AppVersion.parse("1"))
    }

    @Test
    fun `parse returns null for invalid input`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("abc"))
        assertNull(AppVersion.parse("1.2.x"))
    }

    @Test
    fun `stable release outranks pre-release of same numbers`() {
        assertTrue(AppVersion.parse("0.1.1")!! > AppVersion.parse("0.1.1-alpha")!!)
    }

    @Test
    fun `higher patch outranks pre-release of lower numbers`() {
        assertTrue(AppVersion.parse("v0.2.0")!! > AppVersion.parse("0.1.1-alpha")!!)
    }

    @Test
    fun `pre-release identifiers compare numerically per segment`() {
        assertTrue(AppVersion.parse("0.1.1-alpha.2")!! > AppVersion.parse("0.1.1-alpha.1")!!)
        assertTrue(AppVersion.parse("0.1.1-alpha.10")!! > AppVersion.parse("0.1.1-alpha.2")!!)
        assertTrue(AppVersion.parse("0.1.1-alpha")!! < AppVersion.parse("0.1.1-beta")!!)
    }

    @Test
    fun `equal versions compare equal`() {
        assertEquals(0, AppVersion.parse("0.2.0")!!.compareTo(AppVersion.parse("v0.2.0")!!))
    }
}
