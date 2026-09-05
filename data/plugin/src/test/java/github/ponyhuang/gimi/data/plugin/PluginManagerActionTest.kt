package github.ponyhuang.gimi.data.plugin

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginActionCallbackRequest
import github.ponyhuang.gimi.pluginapi.PluginActionResult
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginManagerActionTest {

    @Test(expected = CancellationException::class)
    fun runActionPropagatesCancellation() = runTest {
        val plugin = object : AgentPlugin {
            override val pluginId: String = "test"
            override val version: Int = 1
            override val config: PluginConfig = PluginConfig()

            override suspend fun runConfigAction(actionId: String): PluginConfigActionExecution {
                throw CancellationException("cancelled")
            }
        }

        manager(plugin).runAction("test", "login")
    }

    @Test(expected = CancellationException::class)
    fun actionCallbackPropagatesCancellation() = runTest {
        val plugin = fakePlugin(
            onCallback = { throw CancellationException("cancelled") },
        )

        manager(plugin).onActionCallback(
            pluginId = "test",
            actionId = "login",
            callback = PluginActionCallback(),
        )
    }

    @Test
    fun runActionMapsAwaitingCallbackRequest() = runTest {
        val plugin = fakePlugin(
            execution = PluginConfigActionExecution.AwaitingCallback(
                PluginActionCallbackRequest(
                    handlerId = "custom-interaction",
                    parameters = mapOf("entry" to "https://example.com/login"),
                ),
            ),
        )

        val execution = manager(plugin).runAction("test", "login") as PluginActionExecution.AwaitingCallback

        assertEquals("custom-interaction", execution.request.handlerId)
        assertEquals("https://example.com/login", execution.request.parameters["entry"])
    }

    @Test
    fun actionCallbackPassesPluginDefinedValues() = runTest {
        var received: github.ponyhuang.gimi.pluginapi.PluginActionCallback? = null
        val plugin = fakePlugin(
            onCallback = { callback ->
                received = callback
                PluginActionResult("Done")
            },
        )

        val outcome = manager(plugin).onActionCallback(
            pluginId = "test",
            actionId = "login",
            callback = PluginActionCallback(mapOf("device_code" to "abc")),
        )

        assertEquals("abc", received?.values?.get("device_code"))
        assertEquals("Done", outcome?.message)
    }

    private fun manager(plugin: AgentPlugin): PluginManager {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        every { preferences.getStringSet(any(), any()) } returns emptySet()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns preferences
        val loader = object : PluginLoader {
            override fun load(): List<LoadedPlugin> = listOf(LoadedPlugin("test.package", plugin))
            override fun refresh(): List<LoadedPlugin> = emptyList()
        }
        return PluginManager(loader, context, mockk(relaxed = true))
    }

    private fun fakePlugin(
        execution: PluginConfigActionExecution = PluginConfigActionExecution.Completed(
            PluginActionResult("Done"),
        ),
        onCallback: suspend (github.ponyhuang.gimi.pluginapi.PluginActionCallback) -> PluginActionResult = {
            PluginActionResult("Done")
        },
    ): AgentPlugin = object : AgentPlugin {
        override val pluginId: String = "test"
        override val version: Int = 1
        override val config: PluginConfig = PluginConfig()

        override suspend fun runConfigAction(actionId: String): PluginConfigActionExecution = execution

        override suspend fun onConfigActionCallback(
            actionId: String,
            callback: github.ponyhuang.gimi.pluginapi.PluginActionCallback,
        ): PluginActionResult = onCallback(callback)
    }
}
