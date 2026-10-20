package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginSubscriptionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addClickedShowsDialog() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSubscriptionAction.AddClicked)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.isAddDialogVisible)
        assertEquals("", harness.viewModel.state.value.draftUrl)
    }

    @Test
    fun confirmAddAppendsUrlAndClosesDialog() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSubscriptionAction.AddClicked)
        harness.viewModel.onAction(
            PluginSubscriptionAction.DraftChanged("https://example.com/team.json"),
        )
        harness.viewModel.onAction(PluginSubscriptionAction.ConfirmAdd)
        advanceUntilIdle()

        assertEquals(
            listOf(harness.store.defaultUrl, "https://example.com/team.json"),
            harness.store.urls.value,
        )
        assertFalse(harness.viewModel.state.value.isAddDialogVisible)
    }

    @Test
    fun confirmAddRejectsInvalidUrl() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSubscriptionAction.AddClicked)
        harness.viewModel.onAction(PluginSubscriptionAction.DraftChanged("http://insecure.example"))
        harness.viewModel.onAction(PluginSubscriptionAction.ConfirmAdd)
        advanceUntilIdle()

        assertEquals(PluginSubscriptionError.INVALID_URL, harness.viewModel.state.value.error)
        assertTrue(harness.viewModel.state.value.isAddDialogVisible)
        assertEquals(listOf(harness.store.defaultUrl), harness.store.urls.value)
    }

    @Test
    fun confirmAddRejectsDuplicateUrl() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSubscriptionAction.AddClicked)
        harness.viewModel.onAction(
            PluginSubscriptionAction.DraftChanged(harness.store.defaultUrl),
        )
        harness.viewModel.onAction(PluginSubscriptionAction.ConfirmAdd)
        advanceUntilIdle()

        assertEquals(PluginSubscriptionError.DUPLICATE, harness.viewModel.state.value.error)
        assertEquals(listOf(harness.store.defaultUrl), harness.store.urls.value)
    }

    @Test
    fun removeDeletesOneSource() = runTest {
        val harness = harness(
            initialUrls = listOf(
                "https://example.com/a.json",
                "https://example.com/b.json",
            ),
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(
            PluginSubscriptionAction.Remove("https://example.com/a.json"),
        )
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/b.json"), harness.store.urls.value)
    }

    @Test
    fun resetRestoresOnlyDefaultSource() = runTest {
        val harness = harness(
            initialUrls = listOf(
                "https://example.com/a.json",
                "https://example.com/b.json",
            ),
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSubscriptionAction.Reset)
        advanceUntilIdle()

        assertEquals(listOf(harness.store.defaultUrl), harness.store.urls.value)
    }

    private class Harness(
        val viewModel: PluginSubscriptionViewModel,
        val store: FakeSubscriptionStore,
    )

    private fun TestScope.collectUiState(viewModel: PluginSubscriptionViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { }
        }
    }

    private fun harness(
        initialUrls: List<String> = listOf(DEFAULT_URL),
    ): Harness {
        val store = FakeSubscriptionStore(initialUrls)
        return Harness(PluginSubscriptionViewModel(store), store)
    }

    private class FakeSubscriptionStore(initialUrls: List<String>) : PluginSubscriptionStore {
        override val defaultUrl: String = DEFAULT_URL
        private val mutableUrls = MutableStateFlow(initialUrls)
        override val urls: StateFlow<List<String>> = mutableUrls

        override fun add(url: String) {
            if (url !in mutableUrls.value) mutableUrls.value = mutableUrls.value + url
        }

        override fun remove(url: String) {
            mutableUrls.value = mutableUrls.value - url
        }

        override fun reset() {
            mutableUrls.value = listOf(DEFAULT_URL)
        }
    }

    private companion object {
        const val DEFAULT_URL = "https://example.com/official.json"
    }
}
