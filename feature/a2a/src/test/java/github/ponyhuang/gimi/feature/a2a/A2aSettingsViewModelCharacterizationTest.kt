package github.ponyhuang.gimi.feature.a2a

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentProbeResult
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentConnectionTester
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** 远程 A2A agent 配置 ViewModel 的对外契约行为（状态投影、保存门控与错误路径）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class A2aSettingsViewModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observedAgentsAndToggleAreExposedThroughUiContract() = runTest {
        val agent = RemoteAgentConfig(id = "agent", name = "weather_agent")
        val repository = repository(listOf(agent))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.agents.isEmpty()) state = awaitItem()
            assertEquals(listOf(agent), state.agents)

            viewModel.onAction(A2aSettingsAction.ToggleAgent(agent, enabled = false))
            advanceUntilIdle()
            verify { repository.save(agent.copy(isEnabled = false)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveTrimsFieldsAndRequestsSuccessToast() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(A2aSettingsAction.LoadEditor(null))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                val draft = requireNotNull(state.editor).copy(
                    name = " Weather Agent ",
                    endpointUrl = " https://example.com/a2a/weather ",
                )
                viewModel.onAction(A2aSettingsAction.EditorChanged(draft))
                viewModel.onAction(A2aSettingsAction.SaveEditor)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(A2aSettingsEffect.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify {
            repository.save(match {
                it.name == "Weather Agent" && it.endpointUrl == "https://example.com/a2a/weather"
            })
        }
    }

    @Test
    fun editorSaveWithUnreachableAgentIsBlockedWithError() = runTest {
        val repository = repository()
        val tester = tester(RemoteAgentProbeResult(reachable = false, errorMessage = "HTTP 404"))
        val viewModel = viewModel(repository, tester)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(A2aSettingsAction.LoadEditor(null))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                val draft = requireNotNull(state.editor).copy(
                    name = "weather_agent",
                    endpointUrl = "https://dead.example.com/a2a/weather",
                )
                viewModel.onAction(A2aSettingsAction.EditorChanged(draft))
                viewModel.onAction(A2aSettingsAction.SaveEditor)
                do {
                    state = awaitItem()
                } while (state.isTestingConnection || state.connectionError == null)

                assertEquals("HTTP 404", state.connectionError)
                verify(exactly = 0) { repository.save(any()) }

                // 修改配置后错误应被清除（旧结论失效）。
                viewModel.onAction(A2aSettingsAction.EditorChanged(draft.copy(name = "weather2")))
                do {
                    state = awaitItem()
                } while (state.connectionError != null)
                assertNull(state.connectionError)
                cancelAndIgnoreRemainingEvents()
            }
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveWithUnexpectedProbeFailureIsBlockedWithKey() = runTest {
        val repository = repository()
        val tester = mockk<RemoteAgentConnectionTester> {
            coEvery { test(any()) } throws IllegalStateException("broken transport")
        }
        val viewModel = viewModel(repository, tester)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(A2aSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                A2aSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "weather_agent",
                        endpointUrl = "https://example.com/a2a/weather",
                    ),
                ),
            )

            viewModel.onAction(A2aSettingsAction.SaveEditor)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.connectionError == null)

            assertEquals("a2a.connection_error", state.connectionError)
            verify(exactly = 0) { repository.save(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveWithPersistenceFailureIsBlockedWithKey() = runTest {
        val repository = repository()
        every { repository.save(any()) } throws IllegalStateException("storage unavailable")
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(A2aSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                A2aSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "weather_agent",
                        endpointUrl = "https://example.com/a2a/weather",
                    ),
                ),
            )

            viewModel.onAction(A2aSettingsAction.SaveEditor)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.connectionError == null)

            assertEquals("a2a.save_error", state.connectionError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteEditorRemovesConfigAndClosesPage() = runTest {
        val agent = RemoteAgentConfig(id = "agent", name = "weather_agent")
        val repository = repository(listOf(agent))
        every { repository.agent("agent") } returns agent
        val viewModel = viewModel(repository)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(A2aSettingsAction.LoadEditor("agent"))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                viewModel.onAction(A2aSettingsAction.DeleteEditor)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(A2aSettingsEffect.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify { repository.delete("agent") }
    }

    @Test
    fun loadEditorInitializesCardFromSavedConfig() = runTest {
        val card = RemoteAgentCardSnapshot(name = "remote-agent", version = "1.0.0")
        val agent = RemoteAgentConfig(id = "agent", name = "weather_agent", cardSnapshot = card)
        val repository = repository(listOf(agent))
        every { repository.agent("agent") } returns agent
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(A2aSettingsAction.LoadEditor("agent"))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            assertEquals(card, state.editorCard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConnectionSuccessRefreshesEditorCard() = runTest {
        val card = RemoteAgentCardSnapshot(name = "remote-agent", version = "2.0.0")
        val tester = tester(RemoteAgentProbeResult(reachable = true, card = card))
        val viewModel = viewModel(repository(), tester)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(A2aSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                A2aSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "weather_agent",
                        endpointUrl = "https://example.com/a2a/weather",
                    ),
                ),
            )
            viewModel.onAction(A2aSettingsAction.TestConnection)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.editorCard == null)
            assertEquals(card, state.editorCard)
            assertNull(state.connectionError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConnectionFailureShowsErrorWithoutCard() = runTest {
        val tester = tester(RemoteAgentProbeResult(reachable = false, errorMessage = "HTTP 503"))
        val viewModel = viewModel(repository(), tester)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(A2aSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                A2aSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "weather_agent",
                        endpointUrl = "https://dead.example.com/a2a/weather",
                    ),
                ),
            )
            viewModel.onAction(A2aSettingsAction.TestConnection)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.connectionError == null)
            assertEquals("HTTP 503", state.connectionError)
            assertNull(state.editorCard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSavePersistsFreshCardSnapshot() = runTest {
        val card = RemoteAgentCardSnapshot(name = "remote-agent", version = "3.0.0")
        val tester = tester(RemoteAgentProbeResult(reachable = true, card = card))
        val repository = repository()
        val viewModel = viewModel(repository, tester)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(A2aSettingsAction.LoadEditor(null))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                viewModel.onAction(
                    A2aSettingsAction.EditorChanged(
                        requireNotNull(state.editor).copy(
                            name = "weather_agent",
                            endpointUrl = "https://example.com/a2a/weather",
                        ),
                    ),
                )
                viewModel.onAction(A2aSettingsAction.SaveEditor)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(A2aSettingsEffect.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify {
            repository.save(match { it.cardSnapshot == card })
        }
    }

    private fun viewModel(
        repository: RemoteAgentRepository,
        tester: RemoteAgentConnectionTester = tester(RemoteAgentProbeResult(reachable = true)),
    ) = A2aSettingsViewModel(
        repository = repository,
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
        testConnection = tester,
    )

    private fun tester(result: RemoteAgentProbeResult): RemoteAgentConnectionTester = mockk {
        coEvery { test(any()) } returns result
    }

    private fun repository(agents: List<RemoteAgentConfig> = emptyList()): RemoteAgentRepository =
        mockk(relaxed = true) {
            every { observeAgents() } returns MutableStateFlow(agents)
        }
}
