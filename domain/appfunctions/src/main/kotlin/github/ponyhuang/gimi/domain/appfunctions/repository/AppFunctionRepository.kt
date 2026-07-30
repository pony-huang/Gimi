package github.ponyhuang.gimi.domain.appfunctions.repository

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import kotlinx.coroutines.flow.StateFlow

/**
 * AppFunctions 尝鲜功能的目录、授权与执行边界。
 */
interface AppFunctionRepository {
    val state: StateFlow<AppFunctionCatalogState>
    val revision: StateFlow<Long>

    suspend fun setFeatureEnabled(enabled: Boolean): Boolean

    suspend fun setAppEnabled(packageName: String, enabled: Boolean)

    suspend fun setFunctionEnabled(key: AppFunctionKey, enabled: Boolean)

    suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult
}
