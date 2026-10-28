package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.pluginapi.PluginApi
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginCompatTest {

    @Test
    fun changedAdkVersionIsRejectedEvenWithMatchingApi() {
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION, "0.8.0"))
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION, "99.0.0"))
    }

    @Test
    fun missingCompatibilityMetadataIsRejected() {
        assertFalse(PluginCompat.isCompatible(null, null))
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION, null))
        assertFalse(PluginCompat.isCompatible(null, PluginApi.ADK_VERSION))
    }

    @Test
    fun mismatchedApiIsRejectedEvenWithMatchingAdk() {
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION - 1, PluginApi.ADK_VERSION))
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION + 1, PluginApi.ADK_VERSION))
    }

    @Test
    fun mismatchedApiVersionIsRejected() {
        assertFalse(PluginCompat.isCompatible(PluginApi.VERSION + 1))
    }

}
