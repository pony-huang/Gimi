package github.ponyhuang.gimi.feature.mcp

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.usecase.FetchMcpServerCapabilitiesUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.ManageMcpServersUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.ObserveMcpServersUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.TestMcpConnectionUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val importResult = McpImportResult(created = 1)
        every { repository.importJson("{}") } returns importResult
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

                assertEquals(importResult, state.importResult)
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

    @Test
    fun editorSaveWithUnreachableServerIsBlockedWithError() = runTest {
        val repository = repository()
        val tester = tester(McpProbeResult(reachable = false, errorMessage = "无法连接到服务器"))
        val viewModel = viewModel(repository, tester)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(McpSettingsAction.LoadEditor(null))
                var state = awaitItem()
                while (state.editor == null) state = awaitItem()
                val draft = requireNotNull(state.editor).copy(
                    name = "Server",
                    endpointUrl = "https://dead.example.com/mcp",
                )
                viewModel.onAction(McpSettingsAction.EditorChanged(draft))
                viewModel.onAction(McpSettingsAction.SaveEditor)
                do {
                    state = awaitItem()
                } while (state.isTestingConnection || state.connectionError == null)

                assertEquals("无法连接到服务器", state.connectionError)
                verify(exactly = 0) { repository.save(any()) }

                // 修改配置后错误应被清除（旧结论失效）。
                viewModel.onAction(McpSettingsAction.EditorChanged(draft.copy(name = "Server2")))
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
    fun unexpectedImportFailureIsShownWithoutClosing() = runTest {
        val repository = repository()
        every { repository.importJson(any()) } throws IllegalStateException("storage unavailable")
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

                assertEquals("MCP 配置导入失败，请重试", state.importResult.error)
                cancelAndIgnoreRemainingEvents()
            }
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveWithUnexpectedProbeFailureIsBlockedWithError() = runTest {
        val repository = repository()
        val tester = mockk<McpConnectionTester> {
            coEvery { test(any()) } throws IllegalStateException("broken transport")
        }
        val viewModel = viewModel(repository, tester)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(McpSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                McpSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "Server",
                        endpointUrl = "https://example.com/mcp",
                    ),
                ),
            )

            viewModel.onAction(McpSettingsAction.SaveEditor)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.connectionError == null)

            assertEquals("无法连接到服务器，请检查配置", state.connectionError)
            verify(exactly = 0) { repository.save(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editorSaveWithPersistenceFailureIsBlockedWithError() = runTest {
        val repository = repository()
        every { repository.save(any()) } throws IllegalStateException("storage unavailable")
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(McpSettingsAction.LoadEditor(null))
            var state = awaitItem()
            while (state.editor == null) state = awaitItem()
            viewModel.onAction(
                McpSettingsAction.EditorChanged(
                    requireNotNull(state.editor).copy(
                        name = "Server",
                        endpointUrl = "https://example.com/mcp",
                    ),
                ),
            )

            viewModel.onAction(McpSettingsAction.SaveEditor)
            do {
                state = awaitItem()
            } while (state.isTestingConnection || state.connectionError == null)

            assertEquals("MCP 配置保存失败，请重试", state.connectionError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun expandingServerCardFetchesCapabilitiesOnceAndCachesThem() = runTest {
        val server = McpServer(id = "server", name = "Server")
        val repository = repository(listOf(server))
        every { repository.server("server") } returns server
        val probeResult = McpProbeResult(
            reachable = true,
            tools = listOf(McpToolSummary(name = "search", description = "Search things")),
        )
        val tester = tester(probeResult)
        val viewModel = viewModel(repository, tester)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.servers.isEmpty()) state = awaitItem()

            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.capabilities["server"] !is ServerCapabilityState.Loaded)

            assertEquals("server", state.expandedServerId)
            val loaded = state.capabilities["server"] as ServerCapabilityState.Loaded
            assertEquals(listOf(McpToolSummary(name = "search", description = "Search things")), loaded.result.tools)

            // 折叠再展开走缓存，不重复探测。
            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.expandedServerId != null)
            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.expandedServerId != "server")
            advanceUntilIdle()
            coVerify(exactly = 1) { tester.test(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun expandingServerCardWithUnexpectedProbeFailureShowsError() = runTest {
        val server = McpServer(id = "server", name = "Server")
        val repository = repository(listOf(server))
        every { repository.server("server") } returns server
        val tester = mockk<McpConnectionTester> {
            coEvery { test(any()) } throws IllegalStateException("broken transport")
        }
        val viewModel = viewModel(repository, tester)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.servers.isEmpty()) state = awaitItem()

            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.capabilities["server"] !is ServerCapabilityState.Failed)

            assertEquals(
                "无法连接到服务器，请检查配置",
                (state.capabilities["server"] as ServerCapabilityState.Failed).message,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun capabilityCacheIsInvalidatedWhenServerConfigChanges() = runTest {
        val server = McpServer(id = "server", name = "Server")
        val serversFlow = MutableStateFlow(listOf(server))
        val repository = repository()
        every { repository.observeServers() } returns serversFlow
        every { repository.server("server") } answers { serversFlow.value.first() }
        val tester = tester(McpProbeResult(reachable = true))
        val viewModel = viewModel(repository, tester)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.servers.isEmpty()) state = awaitItem()

            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.capabilities["server"] !is ServerCapabilityState.Loaded)

            // 配置变更（新实例）后重新展开应再次探测。
            serversFlow.value = listOf(server.copy(endpointUrl = "https://new.example.com/mcp"))
            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.expandedServerId != null)
            viewModel.onAction(McpSettingsAction.ServerCardClicked("server"))
            do {
                state = awaitItem()
            } while (state.capabilities["server"] !is ServerCapabilityState.Loaded)
            advanceUntilIdle()
            coVerify(exactly = 2) { tester.test(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(
        repository: McpRepository,
        tester: McpConnectionTester = tester(McpProbeResult(reachable = true)),
    ) = McpSettingsViewModel(
        observeServers = ObserveMcpServersUseCase(repository),
        manageServers = ManageMcpServersUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
        testConnection = TestMcpConnectionUseCase(tester),
        fetchCapabilities = FetchMcpServerCapabilitiesUseCase(tester, repository),
    )

    private fun tester(result: McpProbeResult): McpConnectionTester = mockk {
        coEvery { test(any()) } returns result
    }

    private fun repository(servers: List<McpServer> = emptyList()): McpRepository =
        mockk(relaxed = true) {
            every { observeServers() } returns MutableStateFlow(servers)
        }
}
