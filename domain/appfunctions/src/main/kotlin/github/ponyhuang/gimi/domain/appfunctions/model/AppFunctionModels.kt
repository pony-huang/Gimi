package github.ponyhuang.gimi.domain.appfunctions.model

/**
 * 外部 AppFunction 的稳定业务标识。
 *
 * @property packageName 提供函数的 Android 包名。
 * @property functionId 提供方声明的函数标识。
 */
data class AppFunctionKey(
    val packageName: String,
    val functionId: String,
) {
    val encoded: String = "$packageName|$functionId"

    companion object {
        fun decode(encoded: String): AppFunctionKey? {
            val separator = encoded.indexOf('|')
            if (separator <= 0 || separator == encoded.lastIndex) return null
            return AppFunctionKey(
                packageName = encoded.substring(0, separator),
                functionId = encoded.substring(separator + 1),
            )
        }
    }
}

/**
 * AppFunctions 尝鲜功能及函数选择的持久化快照。
 *
 * 关闭总开关只影响当前可用性，不清除用户此前选择，便于再次启动时恢复。
 *
 * @property featureEnabled 用户是否手动启动尝鲜功能。
 * @property enabledPackageNames 用户明确允许向 Agent 加载函数的应用包名。
 * @property enabledFunctionKeys 用户明确允许加载到 Agent 的函数集合。
 */
data class AppFunctionSelection(
    val featureEnabled: Boolean = false,
    val enabledPackageNames: Set<String> = emptySet(),
    val enabledFunctionKeys: Set<AppFunctionKey> = emptySet(),
) {
    fun isEnabled(key: AppFunctionKey): Boolean =
        featureEnabled &&
            key.packageName in enabledPackageNames &&
            key in enabledFunctionKeys

    fun setAppEnabled(
        packageName: String,
        availableFunctions: Set<AppFunctionKey>,
        enabled: Boolean,
    ): AppFunctionSelection {
        val appFunctions = availableFunctions.filterTo(linkedSetOf()) {
            it.packageName == packageName
        }
        val updated = if (enabled) {
            enabledFunctionKeys + appFunctions
        } else {
            enabledFunctionKeys - appFunctions
        }
        val updatedPackages = if (enabled) {
            enabledPackageNames + packageName
        } else {
            enabledPackageNames - packageName
        }
        return copy(
            enabledPackageNames = updatedPackages,
            enabledFunctionKeys = updated,
        )
    }
}

/**
 * Agent 可表达的 AppFunction 参数类型。
 */
sealed interface AppFunctionValueType {
    val nullable: Boolean

    /**
     * 字符串参数。
     *
     * @property nullable 是否允许空值。
     * @property enumValues 可选枚举值；空集合表示不限制。
     */
    data class StringType(
        override val nullable: Boolean,
        val enumValues: List<String> = emptyList(),
    ) : AppFunctionValueType

    /**
     * 整数参数。
     *
     * @property nullable 是否允许空值。
     * @property enumValues 可选枚举值；空集合表示不限制。
     */
    data class IntegerType(
        override val nullable: Boolean,
        val enumValues: List<Int> = emptyList(),
    ) : AppFunctionValueType

    /** 浮点参数。 */
    data class NumberType(
        override val nullable: Boolean,
    ) : AppFunctionValueType

    /** 布尔参数。 */
    data class BooleanType(
        override val nullable: Boolean,
    ) : AppFunctionValueType

    /**
     * 数组参数。
     *
     * @property itemType 数组元素类型。
     * @property nullable 是否允许空值。
     */
    data class ArrayType(
        val itemType: AppFunctionValueType,
        override val nullable: Boolean,
    ) : AppFunctionValueType

    /**
     * 对象参数。
     *
     * @property properties 对象属性。
     * @property required 必填属性名。
     * @property nullable 是否允许空值。
     */
    data class ObjectType(
        val properties: Map<String, AppFunctionValueType>,
        val required: List<String>,
        override val nullable: Boolean,
    ) : AppFunctionValueType
}

/**
 * 单个 AppFunction 参数描述。
 *
 * @property name 参数名。
 * @property description 面向 Agent 的用途说明。
 * @property required 是否必填。
 * @property type 平台无关参数类型。
 */
data class AppFunctionParameter(
    val name: String,
    val description: String,
    val required: Boolean,
    val type: AppFunctionValueType,
)

/**
 * 外部 AppFunction 的平台无关目录项。
 *
 * @property key 稳定函数标识。
 * @property appLabel 提供应用的用户可见名称。
 * @property appDescription 提供应用的能力说明。
 * @property description 函数用途说明。
 * @property parameters Agent 调用参数。
 * @property providerEnabled 提供方系统状态是否允许执行。
 * @property supported 当前适配器是否可安全转换其参数和返回值。
 * @property unsupportedReason 不支持时面向设置页的简短原因。
 */
data class AppFunctionDescriptor(
    val key: AppFunctionKey,
    val appLabel: String,
    val appDescription: String?,
    val description: String,
    val parameters: List<AppFunctionParameter>,
    val providerEnabled: Boolean,
    val supported: Boolean,
    val unsupportedReason: String? = null,
)

/** 当前设备对 AppFunctions 消费端能力的支持状态。 */
enum class AppFunctionsSupport {
    AVAILABLE,
    UNSUPPORTED_DEVICE,
    MISSING_SYSTEM_PERMISSION,
}

/**
 * AppFunctions 目录的可观察状态。
 *
 * @property support 系统能力状态。
 * @property selection 用户总开关及函数选择。
 * @property functions 当前发现的外部函数。
 * @property isDiscovering 是否正在等待首次发现结果。
 * @property errorMessage 最近一次发现失败的安全提示。
 */
data class AppFunctionCatalogState(
    val support: AppFunctionsSupport,
    val selection: AppFunctionSelection = AppFunctionSelection(),
    val functions: List<AppFunctionDescriptor> = emptyList(),
    val isDiscovering: Boolean = false,
    val errorMessage: String? = null,
) {
    val enabledFunctions: List<AppFunctionDescriptor>
        get() = functions.filter { function ->
            function.supported &&
                function.providerEnabled &&
                selection.isEnabled(function.key)
        }
}

/** AppFunction 执行结果。 */
sealed interface AppFunctionExecutionResult {
    /** 执行成功并返回 JSON 兼容值。 */
    data class Success(val value: Any?) : AppFunctionExecutionResult

    /** 执行失败并返回已清理的错误信息。 */
    data class Failure(val message: String) : AppFunctionExecutionResult
}
