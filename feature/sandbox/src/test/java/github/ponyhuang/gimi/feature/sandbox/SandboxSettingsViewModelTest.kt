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
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    private class Templates(private val load: (suspend (String, String) -> List<SandboxTemplate>)? = null) : SandboxTemplateRepository {
        val calls = mutableListOf<Pair<String, String>>()
        var result = listOf(SandboxTemplate("cli-id", listOf("project/cli"), "ready"))
        var failure: SandboxTemplateFailure? = null
        override suspend fun listTemplates(domain: String, apiKey: String): List<SandboxTemplate> {
            calls += domain to apiKey
            failure?.let { throw SandboxTemplateException(it) }
            return load?.invoke(domain, apiKey) ?: result
        }
    }

    @Test fun queriesDraftCredentialsOnceAndSelectionWaitsForSave() = runTest {
        val settings = Settings(); val templates = Templates()
        val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetDomain(" E2B.APP "))
        viewModel.onAction(SandboxSettingsAction.SetApiKey(" draft-key "))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(listOf("e2b.app" to "draft-key"), templates.calls)
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
        assertEquals("code-interpreter-v1", viewModel.uiState.value.template)
        assertEquals(0, settings.saves)
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(1, templates.calls.size)
        viewModel.onAction(SandboxSettingsAction.SetTemplate("cli-id"))
        assertEquals("code-interpreter-v1", settings.configuration.value.connection.template)
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals("cli-id", settings.configuration.value.connection.template)
        assertEquals(templates.result, viewModel.uiState.value.templates)
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
    }
    @Test fun missingOrInvalidCredentialsDoNotStartQuery() = runTest {
        val templates = Templates(); val viewModel = SandboxSettingsViewModel(Settings(), templates)
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.CREDENTIALS_REQUIRED, viewModel.uiState.value.templateLoadState)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.SetDomain("https://e2b.app"))
        viewModel.onAction(SandboxSettingsAction.RefreshTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.CREDENTIALS_REQUIRED, viewModel.uiState.value.templateLoadState)
        assertTrue(templates.calls.isEmpty())
    }
    @Test fun failedQueryAndRefreshPreserveManualTemplateAndAllowEmptyList() = runTest {
        val templates = Templates().apply { failure = SandboxTemplateFailure.AUTHENTICATION }
        val settings = Settings(); val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.SetTemplate("public/template:v2"))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.AUTHENTICATION, viewModel.uiState.value.templateLoadState)
        assertEquals("public/template:v2", viewModel.uiState.value.template)
        templates.failure = null; templates.result = emptyList()
        viewModel.onAction(SandboxSettingsAction.RefreshTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
        assertTrue(viewModel.uiState.value.templates.isEmpty())
        assertEquals(2, templates.calls.size)
        assertEquals(0, settings.saves)
        assertEquals("public/template:v2", viewModel.uiState.value.template)
    }
    @Test fun credentialChangesDiscardLateResponsesAndResetList() = runTest {
        lateinit var old: Continuation<List<SandboxTemplate>>
        val newTemplates = listOf(SandboxTemplate("new-id", emptyList(), "building"))
        val templates = Templates { _, key ->
            if (key == "old-key") suspendCoroutine { old = it } else newTemplates
        }
        val viewModel = SandboxSettingsViewModel(Settings(), templates)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("old-key"))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.LOADING, viewModel.uiState.value.templateLoadState)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("new-key"))
        assertEquals(SandboxTemplateLoadState.IDLE, viewModel.uiState.value.templateLoadState)
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        old.resume(listOf(SandboxTemplate("old-id", emptyList(), "ready"))); runCurrent()
        assertEquals(newTemplates, viewModel.uiState.value.templates)
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
        viewModel.onAction(SandboxSettingsAction.SetDomain("e2b.pro"))
        assertTrue(viewModel.uiState.value.templates.isEmpty())
        assertEquals(SandboxTemplateLoadState.IDLE, viewModel.uiState.value.templateLoadState)
    }
    @Test fun loadingDoesNotBlockSavingOrOverwriteSelection() = runTest {
        lateinit var pending: Continuation<List<SandboxTemplate>>
        val templates = Templates { _, _ -> suspendCoroutine { pending = it } }
        val settings = Settings(); val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetTemplate("manual:v1"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals("manual:v1", settings.configuration.value.connection.template)
        assertEquals(SandboxTemplateLoadState.LOADING, viewModel.uiState.value.templateLoadState)
        pending.resume(emptyList()); runCurrent()
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
        assertEquals("manual:v1", viewModel.uiState.value.template)
    }
    @Test fun toggleIsImmediateAndConnectionEditsWaitForSave() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
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
        val viewModel = SandboxSettingsViewModel(repository, Templates()); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("draft"))
        viewModel.onAction(SandboxSettingsAction.SetEnabled(false)); runCurrent()
        assertEquals("draft", viewModel.uiState.value.apiKey)
        assertEquals("saved", repository.configuration.value.connection.apiKey)
        assertFalse(viewModel.uiState.value.enabled)
    }
    @Test fun invalidDomainKeyAndTimeoutAreNotSavedAndAdvancedErrorsExpand() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetDomain("https://e2b.app"))
        viewModel.onAction(SandboxSettingsAction.SetIdleTimeout("0"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.errors.containsAll(setOf(SandboxSettingField.DOMAIN, SandboxSettingField.API_KEY, SandboxSettingField.IDLE_TIMEOUT)))
        assertTrue(viewModel.uiState.value.advancedExpanded)
        assertEquals(0, repository.saves)
    }
    @Test fun rejectsDuplicateAndBlankEnvironmentNamesBeforeMapping() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
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
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
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
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
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
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.ToggleAdvanced)
        viewModel.onAction(SandboxSettingsAction.ToggleKeyVisibility); runCurrent()
        assertTrue(viewModel.uiState.value.advancedExpanded)
        assertTrue(viewModel.uiState.value.keyVisible)
        assertEquals(0, repository.saves)
        assertEquals(0L, repository.revision.value)
    }
}
