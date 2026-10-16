package github.ponyhuang.gimi.feature.mobileuse

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileUseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val enabled = MutableStateFlow(true)
    private var availability = MobileUseAvailability.PERMISSION_REQUIRED
    private var textInputAvailable = false
    private var saveGate: CompletableDeferred<Unit>? = null
    private val repository = mockk<MobileUseRepository>(relaxed = true) {
        every { enabled } returns this@MobileUseViewModelTest.enabled
        every { availability() } answers { this@MobileUseViewModelTest.availability }
        every { textInputAvailable() } answers { this@MobileUseViewModelTest.textInputAvailable }
        coEvery { setEnabled(any()) } coAnswers {
            this@MobileUseViewModelTest.saveGate?.await()
            this@MobileUseViewModelTest.enabled.value = firstArg()
        }
    }

    @Test
    fun refreshReadsSwitchAuthorizationAndTextInputStatus() = runTest {
        val viewModel = MobileUseViewModel(repository)
        assertEquals(
            MobileUseUiState(MobileUseAvailability.PERMISSION_REQUIRED, textInputAvailable = false, enabled = true),
            viewModel.uiState.value,
        )
        availability = MobileUseAvailability.READY
        textInputAvailable = true
        viewModel.refresh()
        assertEquals(
            MobileUseUiState(MobileUseAvailability.READY, textInputAvailable = true, enabled = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun switchingOnThePagePersistsChoiceAndUnfoldsStatus() = runTest {
        enabled.value = false
        availability = MobileUseAvailability.DISABLED
        val viewModel = MobileUseViewModel(repository)
        assertFalse(viewModel.uiState.value.enabled)

        availability = MobileUseAvailability.PERMISSION_REQUIRED
        viewModel.setEnabled(true)
        runCurrent()

        coVerify(exactly = 1) { repository.setEnabled(true) }
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(MobileUseAvailability.PERMISSION_REQUIRED, viewModel.uiState.value.availability)
        assertFalse(viewModel.uiState.value.updating)
    }

    @Test
    fun repeatedSwitchChangesWaitForTheCurrentSave() = runTest {
        val save = CompletableDeferred<Unit>()
        saveGate = save
        val viewModel = MobileUseViewModel(repository)

        viewModel.setEnabled(false)
        runCurrent()
        assertTrue(viewModel.uiState.value.updating)

        viewModel.setEnabled(true)
        runCurrent()
        coVerify(exactly = 0) { repository.setEnabled(true) }

        save.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.updating)
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun permissionRequestIsLimitedToAuthorizationNeededState() = runTest {
        val viewModel = MobileUseViewModel(repository)
        viewModel.requestPermission()
        verify(exactly = 1) { repository.requestPermission(any()) }
        availability = MobileUseAvailability.PERMISSION_DENIED
        viewModel.refresh()
        viewModel.requestPermission()
        verify(exactly = 1) { repository.requestPermission(any()) }
    }

    @Test
    fun disablingHidesAuthorizationAndTextInputState() = runTest {
        availability = MobileUseAvailability.DISABLED
        textInputAvailable = true
        enabled.value = false
        val viewModel = MobileUseViewModel(repository)
        runCurrent()

        assertEquals(
            MobileUseUiState(MobileUseAvailability.DISABLED, textInputAvailable = false, enabled = false),
            viewModel.uiState.value,
        )
        viewModel.requestPermission()
        verify(exactly = 0) { repository.requestPermission(any()) }
    }
}
