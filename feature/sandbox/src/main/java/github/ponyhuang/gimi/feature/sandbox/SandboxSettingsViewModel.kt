package github.ponyhuang.gimi.feature.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateException
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateFailure
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateRepository
import github.ponyhuang.gimi.domain.sandbox.isSandboxEnvironmentName
import github.ponyhuang.gimi.domain.sandbox.validateSandboxConnection
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 保存草稿与即时开关分开，观察仓库更新时不能覆盖用户尚未保存的字段。 */
@HiltViewModel
class SandboxSettingsViewModel @Inject constructor(
    private val repository: SandboxSettingsRepository,
    private val templates: SandboxTemplateRepository,
) : ViewModel() {
    private var nextEnvironmentId = 0L
    private var templateQuery: Job? = null
    private var templateGeneration = 0L
    private val mutableState = MutableStateFlow(fromConfiguration(repository.configuration.value))
    val uiState = mutableState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<SandboxSettingsEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.configuration.collect { configuration ->
                val previous = uiState.value
                if (!previous.dirty && !sameCredentials(configuration.connection, previous)) invalidateTemplates()
                mutableState.update { state ->
                    if (state.dirty) state.copy(enabled = configuration.enabled, configured = configuration.isConfigured)
                    else restore(configuration, state)
                }
            }
        }
    }

    fun onAction(action: SandboxSettingsAction) {
        if (uiState.value.busy) return
        when (action) {
            is SandboxSettingsAction.SetEnabled -> persist { repository.setEnabled(action.enabled) }
            is SandboxSettingsAction.SetDomain -> {
                if (action.value != uiState.value.domain) invalidateTemplates()
                edit(SandboxSettingField.DOMAIN) { it.copy(domain = action.value) }
            }
            is SandboxSettingsAction.SetApiKey -> {
                if (action.value != uiState.value.apiKey) invalidateTemplates()
                edit(SandboxSettingField.API_KEY) { it.copy(apiKey = action.value) }
            }
            is SandboxSettingsAction.SetTemplate -> edit(SandboxSettingField.TEMPLATE) { it.copy(template = action.value) }
            SandboxSettingsAction.LoadTemplates -> loadTemplates(refresh = false)
            SandboxSettingsAction.RefreshTemplates -> loadTemplates(refresh = true)
            is SandboxSettingsAction.SetIdleTimeout -> edit(SandboxSettingField.IDLE_TIMEOUT) { it.copy(idleTimeoutSeconds = action.value) }
            is SandboxSettingsAction.SetEnvironmentName -> edit(SandboxSettingField.ENVIRONMENT) { state ->
                state.copy(environment = state.environment.map { if (it.id == action.id) it.copy(name = action.value, nameError = false) else it })
            }
            is SandboxSettingsAction.SetEnvironmentValue -> edit(SandboxSettingField.ENVIRONMENT) { state ->
                state.copy(environment = state.environment.map { if (it.id == action.id) it.copy(value = action.value) else it })
            }
            is SandboxSettingsAction.RemoveEnvironment -> edit(SandboxSettingField.ENVIRONMENT) { state -> state.copy(environment = state.environment.filterNot { it.id == action.id }) }
            is SandboxSettingsAction.ToggleEnvironment -> mutableState.update { state ->
                state.copy(environment = state.environment.map { if (it.id == action.id) it.copy(expanded = !it.expanded) else it })
            }
            SandboxSettingsAction.AddEnvironment -> edit(SandboxSettingField.ENVIRONMENT) { state ->
                state.copy(advancedExpanded = true, environment = state.environment + SandboxEnvironmentDraft(nextEnvironmentId++, expanded = true))
            }
            SandboxSettingsAction.ToggleAdvanced -> mutableState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            SandboxSettingsAction.ToggleKeyVisibility -> mutableState.update { it.copy(keyVisible = !it.keyVisible) }
            SandboxSettingsAction.Save -> save()
        }
    }

    private fun edit(field: SandboxSettingField, transform: (SandboxSettingsUiState) -> SandboxSettingsUiState) {
        mutableState.update { transform(it).copy(dirty = true, errors = it.errors - field) }
    }

    private fun invalidateTemplates() {
        templateGeneration++
        templateQuery?.cancel()
        templateQuery = null
        mutableState.update { it.copy(templates = emptyList(), templateLoadState = SandboxTemplateLoadState.IDLE) }
    }

    private fun loadTemplates(refresh: Boolean) {
        val state = uiState.value
        if (state.templateLoadState == SandboxTemplateLoadState.LOADING) return
        if (!refresh && state.templateLoadState != SandboxTemplateLoadState.IDLE) return
        val domain = state.domain.trim().lowercase()
        val apiKey = state.apiKey.trim()
        val credentialErrors = validateSandboxConnection(SandboxConnection(domain = domain, apiKey = apiKey))
            .intersect(setOf(SandboxSettingField.DOMAIN, SandboxSettingField.API_KEY))
        if (credentialErrors.isNotEmpty()) {
            mutableState.update { it.copy(templateLoadState = SandboxTemplateLoadState.CREDENTIALS_REQUIRED, errors = it.errors + credentialErrors) }
            return
        }
        val generation = ++templateGeneration
        mutableState.update { it.copy(templateLoadState = SandboxTemplateLoadState.LOADING) }
        templateQuery = viewModelScope.launch {
            try {
                val result = templates.listTemplates(domain, apiKey)
                // generation 防止不及时响应取消的远端请求覆盖新凭据或刷新后的列表。
                if (generation == templateGeneration) mutableState.update { it.copy(templates = result, templateLoadState = SandboxTemplateLoadState.LOADED) }
            } catch (error: SandboxTemplateException) {
                if (generation == templateGeneration) mutableState.update { it.copy(templateLoadState = when (error.failure) {
                    SandboxTemplateFailure.AUTHENTICATION -> SandboxTemplateLoadState.AUTHENTICATION
                    SandboxTemplateFailure.RATE_LIMIT -> SandboxTemplateLoadState.RATE_LIMIT
                    SandboxTemplateFailure.NETWORK -> SandboxTemplateLoadState.NETWORK
                    SandboxTemplateFailure.PROTOCOL -> SandboxTemplateLoadState.PROTOCOL
                    SandboxTemplateFailure.UNAVAILABLE -> SandboxTemplateLoadState.UNAVAILABLE
                }) }
            }
        }
    }

    private fun sameCredentials(connection: SandboxConnection, state: SandboxSettingsUiState): Boolean =
        connection.domain == state.domain.trim().lowercase() && connection.apiKey == state.apiKey.trim()

    private fun restore(configuration: SandboxConfiguration, state: SandboxSettingsUiState): SandboxSettingsUiState =
        fromConfiguration(configuration, state).copy(
            advancedExpanded = state.advancedExpanded, keyVisible = state.keyVisible, busy = state.busy,
            templates = if (sameCredentials(configuration.connection, state)) state.templates else emptyList(),
            templateLoadState = if (sameCredentials(configuration.connection, state)) state.templateLoadState else SandboxTemplateLoadState.IDLE,
        )

    private fun save() {
        val state = uiState.value
        if (!state.enabled) return
        val duplicates = state.environment.groupingBy { it.name.trim() }.eachCount().filterValues { it > 1 }.keys
        val rows = state.environment.map { row ->
            val invalid = !isSandboxEnvironmentName(row.name.trim()) || row.name.trim() in duplicates
            row.copy(nameError = invalid, expanded = row.expanded || invalid)
        }
        val connection = SandboxConnection(state.domain.trim().lowercase(), state.apiKey.trim(), state.template.trim(), state.idleTimeoutSeconds.toIntOrNull() ?: 0,
            rows.associate { it.name.trim() to it.value })
        val errors = validateSandboxConnection(connection) + if (rows.any { it.nameError }) setOf(SandboxSettingField.ENVIRONMENT) else emptySet()
        mutableState.update { it.copy(errors = errors, environment = rows, advancedExpanded = it.advancedExpanded || errors.any { field -> field in setOf(SandboxSettingField.TEMPLATE, SandboxSettingField.IDLE_TIMEOUT, SandboxSettingField.ENVIRONMENT) }) }
        if (errors.isNotEmpty()) return
        persist(connectionSaved = true) { repository.saveConnection(connection) }
    }

    private fun persist(connectionSaved: Boolean = false, operation: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                operation()
                if (connectionSaved) {
                    mutableState.update { state -> restore(repository.configuration.value, state) }
                    mutableEffects.emit(SandboxSettingsEffect.SAVED)
                }
            } catch (_: IOException) {
                mutableEffects.emit(SandboxSettingsEffect.SAVE_FAILED)
            } catch (_: GeneralSecurityException) {
                mutableEffects.emit(SandboxSettingsEffect.SAVE_FAILED)
            } finally {
                val saved = repository.configuration.value
                mutableState.update { it.copy(enabled = saved.enabled, configured = saved.isConfigured, busy = false) }
            }
        }
    }

    private fun fromConfiguration(configuration: SandboxConfiguration, previous: SandboxSettingsUiState? = null): SandboxSettingsUiState {
        val connection = configuration.connection
        // 保存时名称会 trim；使用归一化名称保留当前行 ID 和展开态，避免重建编辑组。
        val previousRows = previous?.environment.orEmpty().associateBy { it.name.trim() }
        return SandboxSettingsUiState(enabled = configuration.enabled, configured = configuration.isConfigured,
            domain = connection.domain, apiKey = connection.apiKey, template = connection.template,
            idleTimeoutSeconds = connection.idleTimeoutSeconds.toString(),
            environment = connection.envs.map { (name, value) ->
                previousRows[name]?.copy(name = name, value = value, nameError = false)
                    ?: SandboxEnvironmentDraft(nextEnvironmentId++, name, value)
            })
    }
}
