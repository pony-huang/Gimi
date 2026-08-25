package github.ponyhuang.gimi.pluginapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPluginTest {

    private fun plugin(): AgentPlugin = object : AgentPlugin {
        override val pluginId: String = "test"
        override val version: Int = 1
        override val name: String = "test_plugin"
        override val config: PluginConfig = PluginConfig()
    }

    @Test
    fun apiVersionDefaultsToPluginApiVersion() {
        assertEquals(PluginApi.VERSION, plugin().apiVersion)
    }

    @Test
    fun configDescriptorPreservesFieldMetadata() {
        val config = PluginConfig(
            fields = listOf(
                PluginConfigField.Text(key = "token", label = "Token", secret = true),
                PluginConfigField.Toggle(key = "on", label = "On", defaultValue = true),
                PluginConfigField.Select(key = "env", label = "Env", options = listOf("a", "b")),
            ),
        )

        assertEquals(3, config.fields.size)
        val token = config.fields[0] as PluginConfigField.Text
        assertTrue(token.secret)
        assertEquals("token", token.key)
    }
}
