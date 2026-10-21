package github.ponyhuang.gimi.domain.appfunction

import kotlinx.coroutines.flow.StateFlow

/** 平台查询结果；权限不足和设备不支持不能被当成空列表。 */
enum class AppFunctionAvailability { AVAILABLE, UNSUPPORTED, PERMISSION_REQUIRED, FAILED }

/** 功能参数声明，包含供详情页展示的类型和必填约束。 */
data class AppFunctionParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String = "",
)

/** 平台声明的单个功能；[isEnabled] 为系统状态，null 表示尚无法读取。 */
data class AppFunctionInfo(
    val id: String,
    val description: String = "",
    val parameters: List<AppFunctionParameter> = emptyList(),
    val returnType: String = "",
    val returnDescription: String = "",
    val isEnabled: Boolean? = null,
)

/** 一个提供 AppFunction 的应用；包名是配置和导航使用的稳定标识。 */
data class AppFunctionApp(
    val packageName: String,
    val name: String,
    val description: String = "",
    val functions: List<AppFunctionInfo> = emptyList(),
)

/** 完整发现快照；非 AVAILABLE 时不将结果解释为没有应用。 */
data class AppFunctionCatalog(
    val availability: AppFunctionAvailability = AppFunctionAvailability.AVAILABLE,
    val apps: List<AppFunctionApp> = emptyList(),
)

/** AppFunction 发现与 Gimi 使用开关，不修改其他应用的系统级功能状态。 */
interface AppFunctionRepository {
    val catalog: StateFlow<AppFunctionCatalog>
    val enabledPackages: StateFlow<Set<String>>
    val revision: StateFlow<Long>
    suspend fun refresh()
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
