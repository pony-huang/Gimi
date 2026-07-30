package github.ponyhuang.gimi.feature.appfunctions

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport

/** AppFunctions 设置页的函数状态筛选。 */
enum class AppFunctionStatusFilter {
    ALL,
    ENABLED,
    DISABLED,
    UNAVAILABLE,
}

/**
 * 设置页中的单个提供应用摘要。
 *
 * @property packageName 应用包名。
 * @property label 应用显示名。
 * @property description 应用能力说明。
 * @property enabledCount 已明确启用且当前可加载的函数数。
 * @property totalCount 当前可启用函数数。
 * @property unsupportedCount 不受首版 JSON 适配器支持的函数数。
 * @property allEnabled 是否已批量启用全部当前可用函数。
 */
data class AppFunctionAppItem(
    val packageName: String,
    val label: String,
    val description: String?,
    val enabledCount: Int,
    val totalCount: Int,
    val unsupportedCount: Int,
    val allEnabled: Boolean,
)

/**
 * AppFunctions 设置界面状态。
 *
 * @property catalog 当前发现目录及授权选择。
 * @property isMutationBlocked Agent 执行期间是否禁止修改工具声明。
 * @property query 函数搜索词。
 * @property filter 函数状态筛选。
 */
data class AppFunctionsUiState(
    val catalog: AppFunctionCatalogState = AppFunctionCatalogState(
        support = AppFunctionsSupport.UNSUPPORTED_DEVICE,
    ),
    val isMutationBlocked: Boolean = false,
    val query: String = "",
    val filter: AppFunctionStatusFilter = AppFunctionStatusFilter.ALL,
)

/** AppFunctions 设置页用户意图。 */
sealed interface AppFunctionsAction {
    /** 手动启动或关闭整个尝鲜功能。 */
    data class SetFeatureEnabled(val enabled: Boolean) : AppFunctionsAction
    /** 批量更新一个提供应用及其当前可用函数。 */
    data class SetAppEnabled(val packageName: String, val enabled: Boolean) : AppFunctionsAction
    /** 更新单个函数的显式选择。 */
    data class SetFunctionEnabled(val key: AppFunctionKey, val enabled: Boolean) : AppFunctionsAction
    /** 更新详情页搜索词。 */
    data class SetQuery(val value: String) : AppFunctionsAction
    /** 更新详情页状态筛选。 */
    data class SetFilter(val value: AppFunctionStatusFilter) : AppFunctionsAction
}

/** AppFunctions 设置页一次性提示。 */
sealed interface AppFunctionsEffect {
    /** Agent 正在运行，授权变更已被拒绝。 */
    data object AgentBusy : AppFunctionsEffect
    /** 系统能力或授权不允许启动功能。 */
    data object FeatureUnavailable : AppFunctionsEffect
}

internal fun AppFunctionCatalogState.toAppItems(): List<AppFunctionAppItem> =
    functions.groupBy { function -> function.key.packageName }
        .map { (packageName, functions) ->
            val loadable = functions.filter { function ->
                function.supported && function.providerEnabled
            }
            val enabled = loadable.count { function ->
                selection.isEnabled(function.key)
            }
            AppFunctionAppItem(
                packageName = packageName,
                label = functions.first().appLabel,
                description = functions.first().appDescription,
                enabledCount = enabled,
                totalCount = loadable.size,
                unsupportedCount = functions.count { function -> !function.supported },
                allEnabled = packageName in selection.enabledPackageNames &&
                    loadable.isNotEmpty() &&
                    enabled == loadable.size,
            )
        }
        .sortedBy { app -> app.label.lowercase() }
