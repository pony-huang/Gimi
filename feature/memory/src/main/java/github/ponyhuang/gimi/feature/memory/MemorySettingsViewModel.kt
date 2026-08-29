package github.ponyhuang.gimi.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MemorySettingsViewModel @Inject constructor(
    private val repository: MemorySettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(repository.configuration.value.toUiState())
    val uiState = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<MemorySettingsEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.configuration.collectLatest { configuration ->
                mutableUiState.update { state ->
                    state.copy(
                        memoryEnabled = configuration.memoryEnabled,
                        mem0Enabled = configuration.mem0Enabled,
                        token = configuration.apiKey,
                        hasStoredToken = configuration.apiKey.isNotBlank(),
                        saving = false,
                    )
                }
            }
        }
    }

    fun onAction(action: MemorySettingsAction) {
        when (action) {
            is MemorySettingsAction.SetMemoryEnabled -> {
                mutableUiState.update {
                    it.copy(memoryEnabled = action.enabled, tokenError = false)
                }
                persist()
            }
            is MemorySettingsAction.SetMem0Enabled -> {
                val state = mutableUiState.value
                if (action.enabled && !state.hasStoredToken && state.token.isBlank()) {
                    mutableUiState.update { it.copy(mem0Enabled = false, tokenError = true) }
                    return
                }
                mutableUiState.update {
                    it.copy(mem0Enabled = action.enabled, tokenError = false)
                }
                persist()
            }
            is MemorySettingsAction.SetToken -> mutableUiState.update {
                it.copy(token = action.token, tokenError = false)
            }
            MemorySettingsAction.Save -> persist()
        }
    }

    /** 校验并保存当前设置；总开关与 Mem0 开关共用同一持久化路径。 */
    private fun persist() {
        val state = mutableUiState.value
        if (state.mem0Enabled && !state.hasStoredToken && state.token.isBlank()) {
            mutableUiState.update { it.copy(tokenError = true) }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true) }
            try {
                repository.save(
                    memoryEnabled = state.memoryEnabled,
                    mem0Enabled = state.mem0Enabled,
                    apiKey = state.token.trim().takeIf(String::isNotEmpty),
                )
                val saved = repository.configuration.value
                mutableUiState.update {
                    it.copy(
                        token = saved.apiKey,
                        tokenError = false,
                        saving = false,
                    )
                }
                mutableEffects.emit(MemorySettingsEffect.Saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val configuration = repository.configuration.value
                mutableUiState.update {
                    it.copy(
                        memoryEnabled = configuration.memoryEnabled,
                        mem0Enabled = configuration.mem0Enabled,
                        saving = false,
                    )
                }
                mutableEffects.emit(MemorySettingsEffect.SaveFailed)
            }
        }
    }
}

private fun github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration.toUiState() =
    MemorySettingsUiState(
        memoryEnabled = memoryEnabled,
        mem0Enabled = mem0Enabled,
        token = apiKey,
        hasStoredToken = apiKey.isNotBlank(),
    )
