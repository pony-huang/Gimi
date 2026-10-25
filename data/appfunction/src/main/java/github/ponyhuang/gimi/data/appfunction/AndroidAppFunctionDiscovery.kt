package github.ponyhuang.gimi.data.appfunction

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.metadata.*
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appfunction.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 通过平台索引发现可见应用；包信息或某个应用的说明读取失败不影响其余应用。 */
class AndroidAppFunctionDiscovery @Inject constructor(@ApplicationContext private val context: Context) : AppFunctionDiscovery {
    @SuppressLint("MissingPermission") // 查询前显式校验跨应用执行权限。
    override suspend fun discover(): AppFunctionCatalog = withContext(Dispatchers.IO) {
        val manager = AppFunctionManager.getInstance(context)
            ?: return@withContext AppFunctionCatalog(AppFunctionAvailability.UNSUPPORTED)
        if (context.checkSelfPermission("android.permission.EXECUTE_APP_FUNCTIONS") != PackageManager.PERMISSION_GRANTED) {
            return@withContext AppFunctionCatalog(AppFunctionAvailability.PERMISSION_REQUIRED)
        }
        val functions = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = null))
        val states = if (functions.isEmpty()) emptyMap() else manager.getAppFunctionStates(functions.map { it.name })
            .associate { it.functionName to it.isEnabled }
        val apps = functions.groupBy { it.packageName }.mapNotNull { (packageName, metadata) ->
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val appMetadata = try {
                    metadata.first().packageMetadata.resolveAppFunctionAppMetadata(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                AppFunctionApp(
                    packageName = packageName,
                    name = context.packageManager.getApplicationLabel(appInfo).toString(),
                    description = appMetadata?.displayDescription?.ifBlank { appMetadata.description }.orEmpty(),
                    functions = metadata.distinctBy { it.id }.sortedBy { it.id }.map { it.toInfo(states[it.name]) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: PackageManager.NameNotFoundException) {
                null // 查询和读取之间可能卸载了应用，下次刷新重新发现。
            } catch (_: SecurityException) {
                null // 个别应用不可见不应丢弃整个目录。
            } catch (_: Exception) {
                null // 来自应用资源或声明的异常只隔离该应用。
            }
        }
        AppFunctionCatalog(apps = apps)
    }
}

/** 平台声明到领域展示模型的转换，不依赖 ADK 的内部转换器。 */
internal fun AppFunctionMetadata.toInfo(enabled: Boolean?) = AppFunctionInfo(
    id = id,
    description = description,
    parameters = parameters.map {
        AppFunctionParameter(it.name, it.dataType.displayType(), it.isRequired, it.description.ifBlank { it.dataType.description })
    },
    returnType = response.valueType.displayType(),
    returnDescription = response.description,
    isEnabled = enabled,
)

/** 保留声明类型与 nullable 标记，限制数组递归深度避免异常元数据阻塞详情页。 */
internal fun AppFunctionDataTypeMetadata.displayType(depth: Int = 0): String {
    if (depth >= 16) return "…"
    val type = when (this) {
        is AppFunctionStringTypeMetadata -> "string"
        is AppFunctionIntTypeMetadata -> "int"
        is AppFunctionLongTypeMetadata -> "long"
        is AppFunctionFloatTypeMetadata -> "float"
        is AppFunctionDoubleTypeMetadata -> "double"
        is AppFunctionBooleanTypeMetadata -> "boolean"
        is AppFunctionBytesTypeMetadata -> "bytes"
        is AppFunctionUnitTypeMetadata -> "unit"
        is AppFunctionArrayTypeMetadata -> "array<${itemType.displayType(depth + 1)}>"
        is AppFunctionObjectTypeMetadata -> qualifiedName ?: "object"
        is AppFunctionReferenceTypeMetadata -> referenceDataType
        is AppFunctionAllOfTypeMetadata -> qualifiedName ?: "allOf"
        is AppFunctionOneOfTypeMetadata -> qualifiedName
        is AppFunctionParcelableTypeMetadata -> qualifiedName
        else -> "unknown"
    }
    return if (isNullable) "$type?" else type
}
