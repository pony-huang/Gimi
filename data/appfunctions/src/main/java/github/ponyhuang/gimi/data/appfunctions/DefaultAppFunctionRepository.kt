package github.ponyhuang.gimi.data.appfunctions

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

internal interface AppFunctionGateway {
    val support: AppFunctionsSupport

    fun observeFunctions(): Flow<List<AppFunctionDescriptor>>

    suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult
}

internal interface AppFunctionSelectionStore {
    val selection: StateFlow<AppFunctionSelection>

    fun update(selection: AppFunctionSelection)
}

/**
 * 将平台发现结果与用户显式授权合并为 Agent 可消费目录。
 *
 * 总开关关闭时使用空流，因此不会创建 AppSearch 观察，也不会缓存外部函数。
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppFunctionRepository internal constructor(
    private val gateway: AppFunctionGateway,
    private val selectionStore: AppFunctionSelectionStore,
    scope: CoroutineScope,
) : AppFunctionRepository {

    @Inject
    internal constructor(
        gateway: AndroidAppFunctionGateway,
        selectionStore: SharedPreferencesAppFunctionSelectionStore,
    ) : this(
        gateway = gateway,
        selectionStore = selectionStore,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val discovery: StateFlow<DiscoveryState> = selectionStore.selection
        .map { selection -> selection.featureEnabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (!enabled || gateway.support != AppFunctionsSupport.AVAILABLE) {
                flowOf(DiscoveryState())
            } else {
                gateway.observeFunctions()
                    .map { functions -> DiscoveryState(functions = functions) }
                    .onStart { emit(DiscoveryState(isDiscovering = true)) }
                    .catch { error ->
                        emit(
                            DiscoveryState(
                                errorMessage = "AppFunctions discovery failed.",
                            ),
                        )
                    }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DiscoveryState(),
        )

    override val state: StateFlow<AppFunctionCatalogState> = combine(
        selectionStore.selection,
        discovery,
    ) { selection, discoveryState ->
        AppFunctionCatalogState(
            support = gateway.support,
            selection = selection,
            functions = discoveryState.functions,
            isDiscovering = discoveryState.isDiscovering,
            errorMessage = discoveryState.errorMessage,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppFunctionCatalogState(
            support = gateway.support,
            selection = selectionStore.selection.value,
        ),
    )

    override val revision: StateFlow<Long> = state
        .map { catalog ->
            listOf(
                catalog.support,
                catalog.selection,
                catalog.functions,
            )
        }
        .distinctUntilChanged()
        .scan(0L) { revision, _ -> revision + 1L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    override suspend fun setFeatureEnabled(enabled: Boolean): Boolean {
        if (enabled && gateway.support != AppFunctionsSupport.AVAILABLE) return false
        selectionStore.update(selectionStore.selection.value.copy(featureEnabled = enabled))
        return true
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        val available = state.value.functions
            .filter { function -> function.supported && function.providerEnabled }
            .mapTo(linkedSetOf(), AppFunctionDescriptor::key)
        selectionStore.update(
            selectionStore.selection.value.setAppEnabled(
                packageName = packageName,
                availableFunctions = available,
                enabled = enabled,
            ),
        )
    }

    override suspend fun setFunctionEnabled(key: AppFunctionKey, enabled: Boolean) {
        if (enabled && state.value.functions.none { function ->
                function.key == key && function.supported && function.providerEnabled
            }
        ) {
            return
        }
        val current = selectionStore.selection.value
        val updated = if (enabled) {
            current.enabledFunctionKeys + key
        } else {
            current.enabledFunctionKeys - key
        }
        selectionStore.update(current.copy(enabledFunctionKeys = updated))
    }

    override suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult {
        val active = state.value.enabledFunctions.any { it.key == key }
        if (!active) {
            return AppFunctionExecutionResult.Failure(
                "AppFunction is disabled or no longer available.",
            )
        }
        return gateway.execute(key, arguments)
    }

    /**
     * 单次发现快照。
     *
     * @property functions 当前目录。
     * @property isDiscovering 是否等待首次结果。
     * @property errorMessage 最近失败提示。
     */
    private data class DiscoveryState(
        val functions: List<AppFunctionDescriptor> = emptyList(),
        val isDiscovering: Boolean = false,
        val errorMessage: String? = null,
    )
}
