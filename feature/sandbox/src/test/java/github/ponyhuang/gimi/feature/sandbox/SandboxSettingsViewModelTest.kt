package github.ponyhuang.gimi.feature.sandbox

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.sandbox.*
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
    private class Templates(private val load: (suspend (String, String) -> List<SandboxTemplate>)? = null) : SandboxTemplateRepository {
        val calls = mutableListOf<Pair<String, String>>()
        var result = listOf(SandboxTemplate("cli-id", listOf("project/cli"), "ready"))
        var failure: SandboxTemplateFailure? = null
        override suspend fun listTemplates(apiUrl: String, apiKey: String): List<SandboxTemplate> {
            calls += apiUrl to apiKey
            failure?.let { throw SandboxTemplateException(it) }
            return load?.invoke(apiUrl, apiKey) ?: result
        }
    }

    @Test fun queriesDraftCredentialsOnceAndSelectionWaitsForSave() = runTest {
        val settings = Settings(); val templates = Templates()
        val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiUrl(" http://localhost:3000/Control "))
        viewModel.onAction(SandboxSettingsAction.SetApiKey(" draft-key "))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(listOf("http://localhost:3000/Control" to "draft-key"), templates.calls)
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
        viewModel.onAction(SandboxSettingsAction.SetApiUrl("e2b.app"))
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
        viewModel.onAction(SandboxSettingsAction.SetApiUrl("https://api.e2b.pro"))
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
    @Test fun savesAndRestoresHttpEndpointsWithoutChangingPathCase() = runTest {
        val settings = Settings(); val templates = Templates()
        val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiUrl(" http://localhost:3000/Control "))
        viewModel.onAction(SandboxSettingsAction.SetSandboxUrl(" http://localhost:3002/Proxy "))
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        val saved = settings.configuration.value.connection
        assertEquals("http://localhost:3000/Control", saved.apiUrl)
        assertEquals("http://localhost:3002/Proxy", saved.sandboxUrl)
        val restored = SandboxSettingsViewModel(settings, templates).uiState.value
        assertEquals(saved.apiUrl, restored.apiUrl)
        assertEquals(saved.sandboxUrl, restored.sandboxUrl)
        assertFalse(viewModel.uiState.value.dirty)
    }
    @Test fun invalidProxyBlocksSaveButDoesNotBlockTemplateQuery() = runTest {
        val settings = Settings(); val templates = Templates()
        val viewModel = SandboxSettingsViewModel(settings, templates)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.SetSandboxUrl("localhost:3002"))
        viewModel.onAction(SandboxSettingsAction.LoadTemplates); runCurrent()
        assertEquals(SandboxTemplateLoadState.LOADED, viewModel.uiState.value.templateLoadState)
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(SandboxSettingField.SANDBOX_URL in viewModel.uiState.value.errors)
        assertEquals(0, settings.saves)
        viewModel.onAction(SandboxSettingsAction.SetSandboxUrl(""))
        assertFalse(SandboxSettingField.SANDBOX_URL in viewModel.uiState.value.errors)
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals(1, settings.saves)
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
    @Test fun invalidDomainKeyAndTimeoutAreNotSaved() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiUrl("e2b.app"))
        viewModel.onAction(SandboxSettingsAction.SetIdleTimeout("0"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.errors.containsAll(setOf(SandboxSettingField.API_URL, SandboxSettingField.API_KEY, SandboxSettingField.IDLE_TIMEOUT)))
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
    @Test fun existingVariablesStartCollapsedAndNewVariablesExpandIndependently() = runTest {
        val repository = Settings()
        repository.configuration.value = SandboxConfiguration(true, SandboxConnection(apiKey = "key", envs = mapOf("LANG" to "en_US")))
        val viewModel = SandboxSettingsViewModel(repository, Templates()); runCurrent()
        val savedId = viewModel.uiState.value.environment.single().id
        assertFalse(viewModel.uiState.value.environment.single().expanded)
        viewModel.onAction(SandboxSettingsAction.AddEnvironment)
        val addedId = viewModel.uiState.value.environment.last().id
        assertTrue(viewModel.uiState.value.environment.last().expanded)
        viewModel.onAction(SandboxSettingsAction.ToggleEnvironment(savedId))
        assertTrue(viewModel.uiState.value.environment.all { it.expanded })
        viewModel.onAction(SandboxSettingsAction.ToggleEnvironment(addedId))
        assertTrue(viewModel.uiState.value.environment.first().expanded)
        assertFalse(viewModel.uiState.value.environment.last().expanded)
        assertEquals(0, repository.saves)
    }
    @Test fun invalidCollapsedVariableExpandsWhenSaving() = runTest {
        val repository = Settings()
        val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.AddEnvironment)
        val id = viewModel.uiState.value.environment.single().id
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(id, "INVALID=NAME"))
        viewModel.onAction(SandboxSettingsAction.ToggleEnvironment(id))
        assertFalse(viewModel.uiState.value.environment.single().expanded)
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertTrue(viewModel.uiState.value.environment.single().nameError)
        assertTrue(viewModel.uiState.value.environment.single().expanded)
        assertEquals(0, repository.saves)
    }
    @Test fun savingPreservesVariableIdentityAndExpansion() = runTest {
        val repository = Settings()
        val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.AddEnvironment)
        val id = viewModel.uiState.value.environment.single().id
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentName(id, " LANG "))
        viewModel.onAction(SandboxSettingsAction.SetEnvironmentValue(id, "en_US"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals(id, viewModel.uiState.value.environment.single().id)
        assertEquals("LANG", viewModel.uiState.value.environment.single().name)
        assertTrue(viewModel.uiState.value.environment.single().expanded)
        assertFalse(viewModel.uiState.value.dirty)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onAction(SandboxSettingsAction.ToggleEnvironment(id)); runCurrent()
        assertFalse(viewModel.uiState.value.dirty)
        assertFalse(viewModel.uiState.value.environment.single().expanded)
    }
    @Test fun keyVisibilityDoesNotPersistConnection() = runTest {
        val repository = Settings(); val viewModel = SandboxSettingsViewModel(repository, Templates())
        viewModel.onAction(SandboxSettingsAction.ToggleKeyVisibility); runCurrent()
        assertTrue(viewModel.uiState.value.keyVisible)
        assertEquals(0, repository.saves)
        assertEquals(0L, repository.revision.value)
    }
    @Test fun configurationCanExpandWhileDisabledWithoutPersistingSettings() = runTest {
        val repository = Settings()
        val viewModel = SandboxSettingsViewModel(repository, Templates()); runCurrent()
        viewModel.onAction(SandboxSettingsAction.ToggleConfiguration)
        assertTrue(viewModel.uiState.value.configurationExpanded)
        assertFalse(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.dirty)
        assertEquals(0L, repository.revision.value)
        viewModel.onAction(SandboxSettingsAction.ToggleConfiguration)
        assertFalse(viewModel.uiState.value.configurationExpanded)
        assertEquals(0, repository.saves)
    }

    @Test fun enablingAndDisablingPreservesIndependentConfigurationExpansion() = runTest {
        val repository = Settings()
        val viewModel = SandboxSettingsViewModel(repository, Templates()); runCurrent()
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        assertTrue(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.configurationExpanded)
        viewModel.onAction(SandboxSettingsAction.ToggleConfiguration)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(false)); runCurrent()
        assertFalse(viewModel.uiState.value.enabled)
        assertTrue(viewModel.uiState.value.configurationExpanded)
        viewModel.onAction(SandboxSettingsAction.SetEnabled(true)); runCurrent()
        assertTrue(viewModel.uiState.value.configurationExpanded)
        viewModel.onAction(SandboxSettingsAction.SetApiKey("key"))
        viewModel.onAction(SandboxSettingsAction.Save); runCurrent()
        assertEquals(1, repository.saves)
        assertTrue(viewModel.uiState.value.configurationExpanded)
    }

}
