package github.ponyhuang.gimi.feature.environment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.environment.*
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 一行变量草稿；id 稳定，名称和值只在显式保存时提交，expanded 只控制编辑区展示。 */
data class EnvironmentVariableDraft(val id: Long, val name: String = "", val value: String = "", val expanded: Boolean = false)

/** 保存变量失败的类别；决定保存栏的针对性提示文案。 */
enum class EnvironmentSaveError {
    /** 草稿中存在空名、重复名或名称含不支持的字符。 */
    Validation,
    /** 运行组件连接不可用或需要更新，保存请求未能到达组件。 */
    Connection,
}

/** 页面唯一状态，包含本地环境、云端配置、变量草稿与操作反馈。 */
data class EnvironmentUiState(
    val local: EnvironmentStatus = EnvironmentStatus(),
    val e2bEnabled: Boolean = false,
    val e2bConfigured: Boolean = false,
    val variables: List<EnvironmentVariableDraft> = emptyList(),
    val busy: Boolean = false,
    val error: Boolean = false,
    val saved: Boolean = false,
    /** 变量草稿是否存在未保存的修改；保存成功或重新加载后清除。 */
    val variablesDirty: Boolean = false,
    /** 最近一次保存失败的类别；由下一次编辑或成功保存清除，反馈集中在保存栏展示。 */
    val saveError: EnvironmentSaveError? = null,
    /** 变量草稿是否已从运行组件加载过；重入变量页时以此防止空列表触发重载回滚未保存的删除。 */
    val variablesLoaded: Boolean = false,
    val confirmClose: Boolean = false,
    val confirmUpdate: Boolean = false,
    val statusLoaded: Boolean = false,
)

/** 配置保存与环境安装的状态归属；凭据不进入日志或 Agent 元数据。 */
@HiltViewModel
class EnvironmentViewModel @Inject constructor(
    private val local: EnvironmentRepository,
    private val sandbox: SandboxSettingsRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(EnvironmentUiState())
    val uiState = mutable.asStateFlow()
    private val packages = Channel<String>(Channel.BUFFERED)
    val packageInstalls = packages.receiveAsFlow()
    private var nextId = 0L
    private val prepareRuntimeUpdate = PrepareRuntimeUpdateUseCase(local)
    init {
        viewModelScope.launch { sandbox.configuration.collect { config -> mutable.update { it.copy(e2bEnabled = config.enabled, e2bConfigured = config.isReady) } } }
        viewModelScope.launch {
            while (isActive) {
                try {
                    val status = local.status()
                    acceptStatus(status)
                }
                catch (error: IOException) { mutable.update { it.copy(error = true) } }
                catch (error: SecurityException) { mutable.update { it.copy(error = true) } }
                delay(1500)
            }
        }
    }
    private fun acceptStatus(status: EnvironmentStatus) {
        mutable.update {
            // 连接失效不能证明安装文件消失，保留最后一次确认的安装状态。
            val displayed = if (status.available && !status.connectionAvailable) status.copy(
                installed = it.local.installed, enabled = it.local.enabled,
            ) else status
            it.copy(local = displayed, statusLoaded = true,
                error = it.error && it.local.runtimeVersion == status.runtimeVersion && it.local.connectionAvailable)
        }
    }
    fun refreshStatus() = operation { acceptStatus(local.status()) }
    private fun operation(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = false, saved = false) }
        viewModelScope.launch {
            try { action() }
            catch (error: IOException) { mutable.update { it.copy(error = true) } }
            catch (error: IllegalArgumentException) { mutable.update { it.copy(error = true) } }
            catch (error: IllegalStateException) { mutable.update { it.copy(error = true) } }
            catch (error: SecurityException) { mutable.update { it.copy(error = true) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun setE2b(enabled: Boolean) = operation { sandbox.setEnabled(enabled) }
    fun enableLocal() = operation { local.setEnabled(true) }
    fun requestClose() { mutable.update { it.copy(confirmClose = true) } }
    fun dismissClose() { mutable.update { it.copy(confirmClose = false) } }
    fun closeLocal() = operation { local.setEnabled(false); mutable.update { it.copy(confirmClose = false) } }
    fun install() = operation {
        val status = local.status()
        acceptStatus(status)
        check(status.connectionAvailable) { "Runtime connection unavailable" }
        if (!status.available) packages.send(local.companionApk()) else if (!status.installed) local.install()
    }
    fun requestRuntimeUpdate() = operation {
        val apk = prepareRuntimeUpdate()
        if (apk == null) mutable.update { it.copy(confirmUpdate = true) }
        else packages.send(apk)
    }
    fun dismissRuntimeUpdate() { mutable.update { it.copy(confirmUpdate = false) } }
    fun updateRuntime() = operation {
        val apk = requireNotNull(prepareRuntimeUpdate(stopRunning = true))
        mutable.update { it.copy(confirmUpdate = false, local = local.status()) }
        packages.send(apk)
    }
    fun cancelInstall() = operation { local.setEnabled(false) }
    fun loadVariables() = operation {
        val entries = local.variables().map { (name, value) -> EnvironmentVariableDraft(nextId++, name, value) }
        mutable.update { it.copy(variables = entries, variablesDirty = false, saveError = null, variablesLoaded = true) }
    }
    fun addVariable() { mutable.update { it.copy(variables = it.variables + EnvironmentVariableDraft(nextId++, expanded = true), variablesDirty = true, saved = false, saveError = null) } }
    fun editVariable(id: Long, name: String, value: String) {
        mutable.update { it.copy(variables = it.variables.map { row -> if (row.id == id) row.copy(name = name, value = value) else row }, variablesDirty = true, saved = false, saveError = null) }
    }
    fun removeVariable(id: Long) { mutable.update { it.copy(variables = it.variables.filterNot { row -> row.id == id }, variablesDirty = true, saved = false, saveError = null) } }
    /** 展开或收起指定变量组的编辑区，不修改草稿内容。 */
    fun toggleVariable(id: Long) { mutable.update { it.copy(variables = it.variables.map { row -> if (row.id == id) row.copy(expanded = !row.expanded) else row }) } }
    fun saveVariables() = operation {
        val rows = mutable.value.variables
        // 校验与连接两类失败都翻译成保存栏可见的 saveError；不再落入 operation 的页面底部泛化错误。
        try {
            require(rows.map { it.name }.distinct().size == rows.size)
            require(validEnvironmentVariables(rows.associate { it.name to it.value }))
            local.saveVariables(rows.associate { it.name to it.value })
        }
        catch (error: IllegalArgumentException) { mutable.update { it.copy(saveError = EnvironmentSaveError.Validation) }; return@operation }
        catch (error: IOException) { mutable.update { it.copy(saveError = EnvironmentSaveError.Connection) }; return@operation }
        catch (error: IllegalStateException) { mutable.update { it.copy(saveError = EnvironmentSaveError.Connection) }; return@operation }
        catch (error: SecurityException) { mutable.update { it.copy(saveError = EnvironmentSaveError.Connection) }; return@operation }
        mutable.update { it.copy(saved = true, variablesDirty = false, saveError = null) }
    }
}
