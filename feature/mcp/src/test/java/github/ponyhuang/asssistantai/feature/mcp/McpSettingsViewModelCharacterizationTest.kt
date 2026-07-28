package github.ponyhuang.asssistantai.feature.mcp

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.FakeAgentRuntimeGate
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.mcp.model.McpImportResult
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import github.ponyhuang.asssistantai.domain.mcp.usecase.ManageMcpServersUseCase
import github.ponyhuang.asssistantai.domain.mcp.usecase.ObserveMcpServersUseCase
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpSettingsViewModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observedServersAndToggleAreExposedThroughUiContract() = runTest {
        val server = McpServer(id = "server", name = "Server")
        val repository = repository(listOf(server))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.servers.isEmpty()) state = awaitItem()
            assertEquals(listOf(server), state.servers)

            viewModel.onAction(McpSettingsAction.ToggleServer(server, enabled = false))
            advanceUntilIdle()
            verify { repository.save(server.copy(isEnabled = false)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun successfulImportPublishesResultAndRequestsClose() = runTest {
        val repository = repository()
        every { repository.importJson("{}") } returns McpImportResult(imported = 1)
        val viewModel = viewModel(repository)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(McpSettingsAction.ImportJsonChanged("{}"))
                var state = awaitItem()
                while (state.importJson != "{}") state = awaitItem()
                viewModel.onAction(McpSettingsAction.ImportServers)
                do {
                    state = awaitItem()
                } while (state.importResult == null)

                assertEquals("已导入 1 个 MCP 服务", state.importResult)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(McpSettingsEffect.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveTrimsFieldsAndRequestsClose() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(McpSettingsAction.LoadEditor(null))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                val draft = requireNotNull(state.editor).copy(
                    name = " Server ",
                    endpointUrl = " https://example.com/mcp ",
                )
                viewModel.onAction(McpSettingsAction.EditorChanged(draft))
                viewModel.onAction(McpSettingsAction.SaveEditor)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(McpSettingsEffect.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify { repository.save(match { it.name == "Server" && it.endpointUrl == "https://example.com/mcp" }) }
    }

    private fun viewModel(repository: McpRepository) = McpSettingsViewModel(
        observeServers = ObserveMcpServersUseCase(repository),
        manageServers = ManageMcpServersUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
    )

    private fun repository(servers: List<McpServer> = emptyList()): McpRepository =
        mockk(relaxed = true) {
            every { observeServers() } returns MutableStateFlow(servers)
        }
}
