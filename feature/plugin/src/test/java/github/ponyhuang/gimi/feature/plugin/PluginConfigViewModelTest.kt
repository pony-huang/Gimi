package github.ponyhuang.gimi.feature.plugin

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallbackRequest
import github.ponyhuang.gimi.domain.plugin.model.PluginActionDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPrefersStoredValueOverDefault() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                        secret = true,
                        defaultValue = "dflt",
                    ),
                ),
            ),
            stored = mapOf("token" to "stored"),
        )
        val viewModel = PluginConfigViewModel(repository)

        viewModel.load("zhihu")

        val field = viewModel.state.value.fields.single()
        assertEquals("token", field.key)
        assertEquals("stored", field.value)
        assertTrue(field.secret)
    }

    @Test
    fun loadReloadsFromRepositoryOnRepeatedOpenOfSamePlugin() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                    ),
                ),
            ),
            stored = mapOf("token" to "first"),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("v2ex")
        assertEquals("first", viewModel.state.value.fields.single().value)

        // 模拟返回列表后重进同一插件：ViewModel 被跨页复用，必须重读 store，
        // 不能命中幂等缓存显示旧值（否则看起来像配置被清空）。
        repository.stored = mapOf("token" to "second")
        viewModel.load("v2ex")

        assertEquals("second", viewModel.state.value.fields.single().value)
    }

    @Test
    fun loadFallsBackToDefaultWhenNoStoredValue() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                        defaultValue = "dflt",
                    ),
                ),
            ),
            stored = emptyMap(),
        )
        val viewModel = PluginConfigViewModel(repository)

        viewModel.load("zhihu")

        assertEquals("dflt", viewModel.state.value.fields.single().value)
    }

    @Test
    fun savePersistsEditedValues() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                    ),
                ),
            ),
            stored = emptyMap(),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("zhihu")

        viewModel.onAction(PluginConfigAction.SetValue("token", "abc"))
        viewModel.onAction(PluginConfigAction.Save)

        assertEquals("zhihu" to mapOf("token" to "abc"), repository.updatedConfig)
    }

    @Test
    fun saveShowsConfirmationToast() = runTest {
        val viewModel = PluginConfigViewModel(
            FakePluginRepository(
                descriptor = PluginConfigDescriptor(
                    fields = listOf(
                        PluginConfigFieldDescriptor(
                            key = "token",
                            label = "API Token",
                            kind = PluginConfigFieldDescriptor.Kind.TEXT,
                        ),
                    ),
                ),
            ),
        )
        viewModel.load("zhihu")

        viewModel.effects.test {
            viewModel.onAction(PluginConfigAction.Save)

            assertEquals(
                PluginConfigEffect.ShowToast(messageRes = R.string.plugin_config_saved),
                awaitItem(),
            )
        }
    }

    @Test
    fun loadExposesConfigActions() = runTest {
        val viewModel = PluginConfigViewModel(
            FakePluginRepository(
                descriptor = PluginConfigDescriptor(
                    actions = listOf(PluginActionDescriptor(id = "login", label = "Authorize")),
                ),
            ),
        )

        viewModel.load("spotify")

        assertEquals(listOf("login"), viewModel.state.value.actions.map { it.id })
        assertEquals("Authorize", viewModel.state.value.actions.single().label)
    }

    @Test
    fun runActionDelegatesAndEmitsResultToast() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                actions = listOf(PluginActionDescriptor(id = "login", label = "Authorize")),
            ),
            runExecution = PluginActionExecution.Completed(
                PluginActionOutcome(message = "Authorized", success = true),
            ),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("spotify")

        viewModel.effects.test {
            viewModel.onAction(PluginConfigAction.RunAction("login"))

            assertEquals(PluginConfigEffect.ShowToast(message = "Authorized"), awaitItem())
        }
        assertEquals(listOf("spotify" to "login"), repository.runActionCalls)
        // 执行结束后 running 复位。
        assertEquals(false, viewModel.state.value.actions.single().running)
    }

    @Test
    fun runActionAwaitingCallbackShowsCallbackPage() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                actions = listOf(PluginActionDescriptor(id = "login", label = "Authorize")),
            ),
            runExecution = PluginActionExecution.AwaitingCallback(
                PluginActionCallbackRequest(
                    handlerId = "web",
                    parameters = mapOf(
                        "authorize_url" to "https://accounts.spotify.com/authorize?x",
                        "redirect_base" to "http://127.0.0.1:8888/callback",
                        "desktop_mode" to "true",
                    ),
                ),
            ),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("spotify")

        viewModel.onAction(PluginConfigAction.RunAction("login"))
        advanceUntilIdle()

        assertEquals("login", viewModel.state.value.callback?.actionId)
        assertEquals("web", viewModel.state.value.callback?.handlerId)
        assertEquals(
            "https://accounts.spotify.com/authorize?x",
            viewModel.state.value.callback?.parameters?.get("authorize_url"),
        )
        assertEquals(listOf("spotify" to "login"), repository.runActionCalls)
    }

    @Test
    fun actionCallbackDelegatesGenericValuesAndEmitsResultToast() = runTest {
        val values = mapOf("device_code" to "abc", "account_id" to "123")
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                actions = listOf(PluginActionDescriptor(id = "login", label = "Authorize")),
            ),
            callbackOutcome = PluginActionOutcome(message = "Authorization succeeded", success = true),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("spotify")

        viewModel.effects.test {
            viewModel.onAction(PluginConfigAction.ReceiveActionCallback("login", values))

            assertEquals(
                PluginConfigEffect.ShowToast(message = "Authorization succeeded"),
                awaitItem(),
            )
        }
        assertEquals(
            listOf(Triple("spotify", "login", PluginActionCallback(values))),
            repository.actionCallbackCalls,
        )
        assertEquals(null, viewModel.state.value.callback)
    }

    private class FakePluginRepository(
        private val descriptor: PluginConfigDescriptor = PluginConfigDescriptor(),
        var stored: Map<String, String> = emptyMap(),
        private val runExecution: PluginActionExecution? = null,
        private val callbackOutcome: PluginActionOutcome? = null,
    ) : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        var updatedConfig: Pair<String, Map<String, String>>? = null
        val runActionCalls = mutableListOf<Pair<String, String>>()
        val actionCallbackCalls = mutableListOf<Triple<String, String, PluginActionCallback>>()

        override fun setEnabled(pluginId: String, enabled: Boolean) = Unit
        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = descriptor
        override fun configValues(pluginId: String): Map<String, String> = stored
        override fun updateConfig(pluginId: String, values: Map<String, String>) {
            updatedConfig = pluginId to values
        }

        override suspend fun runAction(pluginId: String, actionId: String): PluginActionExecution? {
            runActionCalls += pluginId to actionId
            return runExecution
        }

        override suspend fun refresh(): List<String> = emptyList()

        override suspend fun onActionCallback(
            pluginId: String,
            actionId: String,
            callback: PluginActionCallback,
        ): PluginActionOutcome? {
            actionCallbackCalls += Triple(pluginId, actionId, callback)
            return callbackOutcome
        }
    }
}
