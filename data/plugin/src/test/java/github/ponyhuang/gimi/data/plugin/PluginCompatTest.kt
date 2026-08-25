package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.pluginapi.PluginApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCompatTest {

    @Test
    fun matchingApiVersionIsCompatible() {
        assertTrue(PluginCompat.isCompatible(PluginApi.VERSION))
    }

    @Test
    fun mismatchedApiVersionIsRejected() {
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION + 1))
    }
}
