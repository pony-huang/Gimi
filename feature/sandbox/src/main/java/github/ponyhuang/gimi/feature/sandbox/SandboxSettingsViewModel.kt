package github.ponyhuang.gimi.feature.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.sandbox.isSandboxEnvironmentName
import github.ponyhuang.gimi.domain.sandbox.validateSandboxConnection
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 保存草稿与即时开关分开，观察仓库更新时不能覆盖用户尚未保存的字段。 */
@HiltViewModel
class SandboxSettingsViewModel @Inject constructor(private val repository: SandboxSettingsRepository) : ViewModel() {
    private var nextEnvironmentId = 0L
    private val mutableState = MutableStateFlow(fromConfiguration(repository.configuration.value))
    val uiState = mutableState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<SandboxSettingsEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.configuration.collect { configuration ->
                mutableState.update { state ->
                    if (state.dirty) state.copy(enabled = configuration.enabled, configured = configuration.isConfigured)
                    else fromConfiguration(configuration).copy(advancedExpanded = state.advancedExpanded, keyVisible = state.keyVisible, busy = state.busy)
                }
            }
        }
    }

    fun onAction(action: SandboxSettingsAction) {
        if (uiState.value.busy) return
        when (action) {
            is SandboxSettingsAction.SetEnabled -> persist { repository.setEnabled(action.enabled) }
            is SandboxSettingsAction.SetDomain -> edit(SandboxSettingField.DOMAIN) { it.copy(domain = action.value) }
            is SandboxSettingsAction.SetApiKey -> edit(SandboxSettingField.API_KEY) { it.copy(apiKey = action.value) }
            is SandboxSettingsAction.SetTemplate -> edit(SandboxSettingField.TEMPLATE) { it.copy(template = action.value) }
            is SandboxSettingsAction.SetIdleTimeout -> edit(SandboxSettingField.IDLE_TIMEOUT) { it.copy(idleTimeoutSeconds = action.value) }
            is SandboxSettingsAction.SetEnvironmentName -> edit(SandboxSettingField.ENVIRONMENT) { state ->
                state.copy(environment = state.environment.map { if (it.id == action.id) it.copy(name = action.value, nameError = false) else it })
            }
            is SandboxSettingsAction.SetEnvironmentValue -> edit(SandboxSettingField.ENVIRONMENT) { state ->
                state.copy(environment = state.environment.map { if (it.id == action.id) it.copy(value = action.value) else it })
            }
            is SandboxSettingsAction.RemoveEnvironment -> edit(SandboxSettingField.ENVIRONMENT) { state -> state.copy(environment = state.environment.filterNot { it.id == action.id }) }
            SandboxSettingsAction.AddEnvironment -> edit(SandboxSettingField.ENVIRONMENT) { state -> state.copy(environment = state.environment + SandboxEnvironmentDraft(nextEnvironmentId++)) }
            SandboxSettingsAction.ToggleAdvanced -> mutableState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            SandboxSettingsAction.ToggleKeyVisibility -> mutableState.update { it.copy(keyVisible = !it.keyVisible) }
            SandboxSettingsAction.Save -> save()
        }
    }

    private fun edit(field: SandboxSettingField, transform: (SandboxSettingsUiState) -> SandboxSettingsUiState) {
        mutableState.update { transform(it).copy(dirty = true, errors = it.errors - field) }
    }

    private fun save() {
        val state = uiState.value
        if (!state.enabled) return
        val duplicates = state.environment.groupingBy { it.name.trim() }.eachCount().filterValues { it > 1 }.keys
        val rows = state.environment.map { it.copy(nameError = !isSandboxEnvironmentName(it.name.trim()) || it.name.trim() in duplicates) }
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
                    mutableState.update { state -> fromConfiguration(repository.configuration.value)
                        .copy(advancedExpanded = state.advancedExpanded, keyVisible = state.keyVisible) }
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

    private fun fromConfiguration(configuration: SandboxConfiguration): SandboxSettingsUiState {
        val connection = configuration.connection
        return SandboxSettingsUiState(enabled = configuration.enabled, configured = configuration.isConfigured,
            domain = connection.domain, apiKey = connection.apiKey, template = connection.template,
            idleTimeoutSeconds = connection.idleTimeoutSeconds.toString(),
            environment = connection.envs.map { (name, value) -> SandboxEnvironmentDraft(nextEnvironmentId++, name, value) })
    }
}
