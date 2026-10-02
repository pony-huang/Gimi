package github.ponyhuang.gimi.feature.mobileuse

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileUseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val enabled = MutableStateFlow(true)
    private var availability = MobileUseAvailability.PERMISSION_REQUIRED
    private var textInputAvailable = false
    private val repository = mockk<MobileUseRepository>(relaxed = true) {
        every { enabled } returns this@MobileUseViewModelTest.enabled
        every { availability() } answers { this@MobileUseViewModelTest.availability }
        every { textInputAvailable() } answers { this@MobileUseViewModelTest.textInputAvailable }
    }

    @Test
    fun refreshReadsBothAuthorizationAndTextInputStatus() = runTest {
        val viewModel = MobileUseViewModel(repository)
        assertEquals(MobileUseUiState(MobileUseAvailability.PERMISSION_REQUIRED, false), viewModel.uiState.value)
        availability = MobileUseAvailability.READY
        textInputAvailable = true
        viewModel.refresh()
        assertEquals(MobileUseUiState(MobileUseAvailability.READY, true), viewModel.uiState.value)
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
    fun disablingFromSettingsHidesAuthorizationAndTextInputState() = runTest {
        val viewModel = MobileUseViewModel(repository)
        runCurrent()
        availability = MobileUseAvailability.DISABLED
        enabled.value = false
        runCurrent()
        assertEquals(MobileUseUiState(), viewModel.uiState.value)
        viewModel.requestPermission()
        verify(exactly = 0) { repository.requestPermission(any()) }
    }
}
