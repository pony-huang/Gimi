package github.ponyhuang.gimi.pluginapi

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPluginTest {

    @Test
    fun actionCallbackContractBumpsPluginApiVersion() {
        assertEquals(3, PluginApi.VERSION)
    }

    @Test
    fun callbackRequestCarriesOpaqueHandlerParameters() {
        val request = PluginActionCallbackRequest(
            handlerId = "custom-interaction",
            parameters = mapOf(
                "entry" to "https://example.com/login",
                "completion" to "document.querySelector('.account') !== null",
            ),
        )

        assertEquals("custom-interaction", request.handlerId)
        assertEquals("https://example.com/login", request.parameters["entry"])
    }

    @Test
    fun actionCallbackCarriesPluginDefinedValues() {
        val callback = PluginActionCallback(
            values = mapOf(
                "authorization_code" to "abc",
                "account_id" to "123",
            ),
        )

        assertEquals("abc", callback.values["authorization_code"])
        assertEquals("123", callback.values["account_id"])
    }

    private fun plugin(): AgentPlugin = object : AgentPlugin {
        override val pluginId: String = "test"
        override val version: Int = 1
        override val config: PluginConfig = PluginConfig()
    }

    @Test
    fun apiVersionDefaultsToPluginApiVersion() {
        assertEquals(PluginApi.VERSION, plugin().apiVersion)
    }

    @Test
    fun adkNameDefaultsToPluginId() {
        assertEquals("test", plugin().name)
    }

    @Test
    fun declaredToolCountDefaultsToDirectTools() {
        assertEquals(0, plugin().toolCount)
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

    @Test
    fun runConfigActionDefaultsToUnsupported() = runTest {
        val execution = plugin().runConfigAction("whatever")
        val result = (execution as PluginConfigActionExecution.Completed).result

        assertFalse(result.success)
        assertTrue(result.message.contains("whatever"))
    }
}
