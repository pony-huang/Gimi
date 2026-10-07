package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.LOCAL_GEMMA_SERVICE_ID
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatLocalModelCoordinatorTest {
    @get:Rule val main = MainDispatcherRule()
    private fun selection(id: String) = ModelSelection(LOCAL_GEMMA_SERVICE_ID, "gemma4", id)

    @Test fun selectingLocalModelBlocksUntilMatchingEngineIsReady() = runTest {
        val runtime = FakeRuntime()
        val state = MutableStateFlow(ChatUiState(currentModelSelection = selection("a")))
        ChatLocalModelCoordinator(state, backgroundScope, runtime)
        runCurrent()
        assertEquals(listOf("a"), runtime.prepared)
        assertTrue(state.value.isLocalModelInputBlocked)
        runtime.finish.complete(Unit)
        runCurrent()
        assertFalse(state.value.isLocalModelInputBlocked)
    }

    @Test fun staleReadyCannotUnlockNewSelectionAndRemoteSwitchUnloads() = runTest {
        val runtime = FakeRuntime()
        val state = MutableStateFlow(ChatUiState(currentModelSelection = selection("a")))
        ChatLocalModelCoordinator(state, backgroundScope, runtime)
        runCurrent()
        state.update { it.copy(currentModelSelection = selection("b")) }
        runCurrent()
        runtime.state.value = LocalModelLoadState("a", LocalModelLoadPhase.Ready)
        runCurrent()
        assertTrue(state.value.isLocalModelInputBlocked)
        runtime.finish.complete(Unit)
        runCurrent()
        assertFalse(state.value.isLocalModelInputBlocked)
        state.update { it.copy(currentModelSelection = ModelSelection("remote", "group", "model")) }
        runCurrent()
        assertFalse(state.value.isLocalModelInputBlocked)
        assertEquals(1, runtime.unloaded)
    }

    @Test fun failureKeepsInputBlockedAndRetryLoadsAgain() = runTest {
        val runtime = FakeRuntime().apply { fail = true; finish.complete(Unit) }
        val state = MutableStateFlow(ChatUiState(currentModelSelection = selection("a")))
        val coordinator = ChatLocalModelCoordinator(state, backgroundScope, runtime)
        runCurrent()
        assertTrue(state.value.isLocalModelInputBlocked)
        assertEquals(LocalModelLoadPhase.Failed, state.value.localModelLoadState.phase)
        runtime.fail = false
        coordinator.retry()
        runCurrent()
        assertEquals(listOf("a", "a"), runtime.prepared)
        assertFalse(state.value.isLocalModelInputBlocked)
        coordinator.close()
        assertTrue(runtime.released)
    }

    @Test fun unloadedEngineIsPreparedAgainWithoutDiscardingSelection() = runTest {
        val runtime = FakeRuntime().apply { finish.complete(Unit) }
        val state = MutableStateFlow(ChatUiState(currentModelSelection = selection("a")))
        ChatLocalModelCoordinator(state, backgroundScope, runtime)
        runCurrent()
        runtime.state.value = LocalModelLoadState()
        runCurrent()
        assertEquals(listOf("a", "a"), runtime.prepared)
        assertFalse(state.value.isLocalModelInputBlocked)
    }

    private class FakeRuntime : LocalModelRuntime {
        override val state = MutableStateFlow(LocalModelLoadState())
        val finish = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        var unloaded = 0
        var released = false
        var fail = false
        override suspend fun prepare(modelId: String) {
            prepared += modelId
            state.value = LocalModelLoadState(modelId, LocalModelLoadPhase.Loading)
            finish.await()
            state.value = LocalModelLoadState(modelId, if (fail) LocalModelLoadPhase.Failed else LocalModelLoadPhase.Ready)
        }
        override suspend fun unload() { unloaded++; state.value = LocalModelLoadState() }
        override suspend fun remove(modelId: String) = Unit
        override fun release() { released = true }
    }
}
