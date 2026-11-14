package github.ponyhuang.gimi.domain.environment

import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RuntimeUpdateTest {
    private val bundled = RuntimeVersion(66, "new", 1)
    private fun status(code: Long, api: Int = 1) = EnvironmentStatus(available = true,
        runtimeVersion = RuntimeVersion(code, "old", api), bundledRuntimeVersion = bundled)

    @Test fun compatibleOlderVersionOffersOptionalUpdate() {
        assertTrue(status(65).updateAvailable)
        assertFalse(status(65).updateRequired)
        assertTrue(status(65).runtimeUsable)
    }
    @Test fun legacyInterfaceRequiresUpdateEvenAtSameVersion() {
        assertTrue(status(66, 0).updateAvailable)
        assertTrue(status(66, 0).updateRequired)
        assertFalse(status(66, 0).runtimeUsable)
    }
    @Test fun disconnectedComponentIsUnusableWithoutLosingVersionInformation() {
        val state = status(65).copy(connectionAvailable = false)
        assertFalse(state.runtimeUsable)
        assertTrue(state.available)
        assertTrue(state.updateAvailable)
    }
    @Test fun currentAndNewerVersionsDoNotOfferDowngrade() {
        assertFalse(status(66).updateAvailable)
        assertFalse(status(67).updateAvailable)
        assertFalse(status(67, 2).updateAvailable)
        assertTrue(status(67, 2).updateRequired)
        assertFalse(EnvironmentStatus().updateAvailable)
    }
    @Test fun preparationStopsRuntimeBeforeProvidingApk() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(65).copy(enabled = true)
        coEvery { repository.setEnabled(false) } returns Unit
        coEvery { repository.companionApk() } returns "runtime.apk"
        assertEquals("runtime.apk", PrepareRuntimeUpdateUseCase(repository)(stopRunning = true))
        coVerifyOrder { repository.status(); repository.setEnabled(false); repository.companionApk() }
    }
    @Test fun installationIsStoppedBeforeUpdate() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(65).copy(installing = true)
        coEvery { repository.setEnabled(false) } returns Unit
        coEvery { repository.companionApk() } returns "runtime.apk"
        PrepareRuntimeUpdateUseCase(repository)(stopRunning = true)
        coVerify { repository.setEnabled(false) }
    }
    @Test fun stopFailurePreventsInstallerPreparation() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(65).copy(enabled = true)
        coEvery { repository.setEnabled(false) } throws IOException("stop failed")
        try { PrepareRuntimeUpdateUseCase(repository)(stopRunning = true); fail("Expected failure") } catch (_: IOException) { }
        coVerify(exactly = 0) { repository.companionApk() }
    }
    @Test fun idleRuntimeNeedsNoStop() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(65)
        coEvery { repository.companionApk() } returns "runtime.apk"
        PrepareRuntimeUpdateUseCase(repository)(stopRunning = true)
        coVerify(exactly = 0) { repository.setEnabled(any()) }
    }
    @Test fun changedVersionIsRecheckedBeforeInstall() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(67)
        try { PrepareRuntimeUpdateUseCase(repository)(stopRunning = true); fail("Expected failure") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { repository.companionApk() }
    }
    @Test fun activeRuntimeWithoutConsentRequestsConfirmation() = runTest {
        val repository = mockk<EnvironmentRepository>()
        coEvery { repository.status() } returns status(65).copy(enabled = true)
        assertNull(PrepareRuntimeUpdateUseCase(repository)())
        coVerify(exactly = 0) { repository.setEnabled(any()) }
        coVerify(exactly = 0) { repository.companionApk() }
    }
}
