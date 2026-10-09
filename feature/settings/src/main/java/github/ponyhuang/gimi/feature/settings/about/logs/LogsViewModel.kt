package github.ponyhuang.gimi.feature.settings.about.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.logging.LogRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 拥有读取、重试、文档选择和导出状态；取消关闭后的读取，避免旧结果覆盖新弹窗。 */
@HiltViewModel
class LogsViewModel @Inject constructor(private val repository: LogRepository) : ViewModel() {
    private val state = MutableStateFlow(LogsUiState())
    val uiState = state.asStateFlow()
    private val effectChannel = Channel<LogsEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var readJob: Job? = null
    private var exportJob: Job? = null

    fun open() {
        if (state.value.exporting) return
        readJob?.cancel()
        state.value = LogsUiState(visible = true, loading = true)
        readJob = viewModelScope.launch {
            try {
                val snapshot = repository.read()
                state.update { it.copy(loading = false, content = snapshot.content, truncated = snapshot.truncated) }
            } catch (_: IOException) {
                state.update { it.copy(loading = false, failed = true) }
            } catch (_: SecurityException) {
                state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun close() {
        readJob?.cancel()
        state.update { it.copy(visible = false, loading = false) }
    }

    fun requestExport() {
        val current = state.value
        if (!current.visible || current.loading || current.failed || current.exporting || current.content.isEmpty()) return
        state.update { it.copy(exporting = true) }
        viewModelScope.launch { effectChannel.send(LogsEffect.ChooseDestination) }
    }

    fun destinationUnavailable() {
        state.update { it.copy(exporting = false) }
        viewModelScope.launch { effectChannel.send(LogsEffect.ExportFailed) }
    }

    fun destinationSelected(destination: String?) {
        if (!state.value.exporting || exportJob?.isActive == true) return
        if (destination == null) {
            state.update { it.copy(exporting = false) }
            return
        }
        exportJob = viewModelScope.launch {
            val effect = try {
                repository.export(destination)
                LogsEffect.ExportSucceeded
            } catch (_: IOException) {
                LogsEffect.ExportFailed
            } catch (_: SecurityException) {
                LogsEffect.ExportFailed
            }
            state.update { it.copy(exporting = false) }
            effectChannel.send(effect)
        }
    }
}
