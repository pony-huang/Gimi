package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileAvailability
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChoice
import github.ponyhuang.gimi.domain.conversation.cloud.LoadCloudFileChoicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 暂选、连接变化和迟到结果的业务边界，不运行 Compose UI。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatCloudFilesCoordinatorTest {
    private val load = mockk<LoadCloudFileChoicesUseCase>()
    private var context: CloudFilePickerContext? = CloudFilePickerContext("session", ModelSelection("deepseek", "group", "model"),
        LLMModelSetting("deepseek", "DeepSeek", true, apiKey = "fake", apiBaseUrl = "https://example.test",
            apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList(), isFileUploadEnabled = true))
    private var state = CloudFilePickerState()

    @Test fun searchKeepsSelectionsAndBackDiscardsAllPendingChanges() = runTest {
        coEvery { load(any(), any(), any()) } returns listOf(choice("one"), choice("two"))
        val coordinator = ChatCloudFilesCoordinator(this, load, { context }, { state }, { state = it })
        coordinator.onAction(CloudFilePickerAction.Open)
        advanceUntilIdle()
        coordinator.onAction(CloudFilePickerAction.Toggle("one"))
        coordinator.onAction(CloudFilePickerAction.QueryChanged("two"))
        assertEquals(listOf("two"), state.visibleChoices.map { it.file.id })
        assertEquals(setOf("one"), state.selectedIds)
        assertEquals("one", state.selectedDrafts.single().cloudReference!!.fileId)
        coordinator.onAction(CloudFilePickerAction.Close)
        assertEquals(CloudFilePickerState(), state)
    }

    @Test fun unknownUnavailableAndExcessFilesCannotBeSelected() = runTest {
        coEvery { load(any(), any(), any()) } returns (1..4).map { choice(it.toString()) } +
            choice("blocked").copy(availability = CloudFileAvailability.ReferenceUnverified)
        val coordinator = ChatCloudFilesCoordinator(this, load, { context }, { state }, { state = it })
        coordinator.onAction(CloudFilePickerAction.Open)
        advanceUntilIdle()
        listOf("unknown", "blocked", "1", "2", "3", "4").forEach { coordinator.onAction(CloudFilePickerAction.Toggle(it)) }
        assertEquals(setOf("1", "2", "3"), state.selectedIds)
        coordinator.onAction(CloudFilePickerAction.Toggle("2"))
        assertEquals(setOf("1", "3"), state.selectedIds)
    }

    @Test fun credentialChangeClosesAndClearsTheOldConnectionImmediately() = runTest {
        coEvery { load(any(), any(), any()) } returns listOf(choice("one"))
        val coordinator = ChatCloudFilesCoordinator(this, load, { context }, { state }, { state = it })
        coordinator.onAction(CloudFilePickerAction.Open)
        advanceUntilIdle()
        coordinator.onAction(CloudFilePickerAction.Toggle("one"))
        context = context!!.copy(service = context!!.service.copy(apiKey = "rotated"))
        coordinator.contextChanged()
        assertFalse(state.isOpen)
        assertTrue(state.choices.isEmpty())
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test fun closedThenReopenedSameConnectionRejectsLateResultsFromTheOldLoad() = runTest {
        val old = CompletableDeferred<List<CloudFileChoice>>()
        var calls = 0
        coEvery { load(any(), any(), any()) } coAnswers {
            if (calls++ == 0) withContext(NonCancellable) { old.await() } else listOf(choice("new"))
        }
        val coordinator = ChatCloudFilesCoordinator(this, load, { context }, { state }, { state = it })
        coordinator.onAction(CloudFilePickerAction.Open)
        runCurrent()
        coordinator.onAction(CloudFilePickerAction.Close)
        coordinator.onAction(CloudFilePickerAction.Open)
        runCurrent()
        old.complete(listOf(choice("old")))
        advanceUntilIdle()
        assertEquals(listOf("new"), state.choices.map { it.file.id })
    }

    private fun choice(id: String) = CloudFileChoice(CloudFile(id, "scope", "$id.pdf", "application/pdf", 10,
        "hash", 1, Long.MAX_VALUE, null, null), CloudFileAvailability.Ready)
}
