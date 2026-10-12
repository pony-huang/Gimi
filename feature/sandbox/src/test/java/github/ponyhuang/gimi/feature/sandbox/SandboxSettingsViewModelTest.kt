package github.ponyhuang.gimi.feature.sandbox

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.sandbox.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxSettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private class Settings : SandboxSettingsRepository {
        override val configuration = MutableStateFlow(SandboxConfiguration())
        override val revision = MutableStateFlow(0L)
        var saves = 0; var fail = false
        override suspend fun setEnabled(enabled: Boolean) {
            if (fail) throw IOException("test storage failure")
            configuration.value = configuration.value.copy(enabled = enabled); revision.value++
        }
        override suspend fun saveConnection(connection: SandboxConnection) {
            if (fail) throw IOException("test storage failure")
            saves++; configuration.value = configuration.value.copy(connection = connection); revision.value++
        }
    }
    @Test fun toggleIsImmediateAndConnectionEditsWaitForSave() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        assertTrue(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.configured)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key")); runCurrent()
        assertEquals("", repository.configuration.value.connection.apiKey)
        assertEquals("key", viewModel.uiState.value.apiKey)
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.configured)
        assertEquals("key", repository.configuration.value.connection.apiKey)
    }
    @Test fun disablingPreservesUnsavedDraftAndSavedCredentials() = runTest {
        val repository = Settings()
        repository.configuration.value = SandboxConfiguration(true, SandboxConnection(apiKey = "saved"))
        val viewModel = SandboxSettingsViewModel(repository); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("draft"))
        viewModel.onAction(SandboxSettingsAction.SetEnabled(false)); runCurrent()
        assertEquals("draft", viewModel.uiState.value.apiKey)
        assertEquals("saved", repository.configuration.value.connection.apiKey)
        assertFalse(viewModel.uiState.value.enabled)
    }
    @Test fun invalidDomainKeyAndTimeoutAreNotSavedAndAdvancedErrorsExpand() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetDomain("https://e2b.app"))
        viewModel.onAction(SandboxSettingsAction.SetIdleTimeout("0"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.errors.containsAll(setOf(SandboxSettingField.DOMAIN, SandboxSettingField.API_KEY, SandboxSettingField.IDLE_TIMEOUT)))
        assertTrue(viewModel.uiState.value.advancedExpanded)
        assertEquals(0, repository.saves)
    }
    @Test fun rejectsDuplicateAndBlankEnvironmentNamesBeforeMapping() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        repeat(2) { viewModel.onAction(SandboxSettingsAction.AddEnvironment) }
        val rows = viewModel.uiState.value.environment
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(rows[0].id, "TOKEN"))
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(rows[1].id, " TOKEN "))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.environment.all { it.nameError })
        assertEquals(0, repository.saves)
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(rows[1].id, ""))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.environment[1].nameError)
    }
    @Test fun emptyEnvironmentValueIsSavedAndRowCanBeRemoved() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.AddEnvironment)
        val id = viewModel.uiState.value.environment.single().id
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(id, "EMPTY"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals(mapOf("EMPTY" to ""), repository.configuration.value.connection.envs)
        viewModel.onAction(SandboxSettingsAction.RemoveEnvironment(viewModel.uiState.value.environment.single().id))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(repository.configuration.value.connection.envs.isEmpty())
    }
    @Test fun failedSaveKeepsDraftAndActualConfiguration() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("draft"))
        repository.fail = true
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.dirty)
        assertEquals("draft", viewModel.uiState.value.apiKey)
        assertEquals("", repository.configuration.value.connection.apiKey)
    }
    @Test fun keyVisibilityAndAdvancedDisplayDoNotPersistConnection() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository)
        viewModel.onAction(SandboxSettingsAction.ToggleAdvanced)
        viewModel.onAction(SandboxSettingsAction.ToggleKeyVisibility); runCurrent()
        assertTrue(viewModel.uiState.value.advancedExpanded)
        assertTrue(viewModel.uiState.value.keyVisible)
        assertEquals(0, repository.saves)
        assertEquals(0L, repository.revision.value)
    }
}
