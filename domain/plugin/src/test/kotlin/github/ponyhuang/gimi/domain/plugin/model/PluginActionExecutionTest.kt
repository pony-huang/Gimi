package github.ponyhuang.gimi.domain.plugin.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginActionExecutionTest {

    @Test
    fun awaitingCallbackCarriesRequestWithoutAssumingCallbackPayload() {
        val execution = PluginActionExecution.AwaitingCallback(
            PluginActionCallbackRequest(
                handlerId = "custom-interaction",
                parameters = mapOf("entry" to "https://example.com/login"),
            ),
        )

        assertEquals("custom-interaction", execution.request.handlerId)
        assertEquals("https://example.com/login", execution.request.parameters["entry"])
    }

    @Test
    fun callbackCarriesPluginDefinedValues() {
        val callback = PluginActionCallback(mapOf("device_code" to "abc"))

        assertEquals("abc", callback.values["device_code"])
    }
}
