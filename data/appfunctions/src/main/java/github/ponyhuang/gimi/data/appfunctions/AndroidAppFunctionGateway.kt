package github.ponyhuang.gimi.data.appfunctions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.appfunctions.metadata.AppFunctionUnitTypeMetadata
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionParameter
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionValueType
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

/**
 * AndroidX AppFunctions 消费端网关。
 *
 * 只把可以完整转换成 JSON 工具声明的函数放入可执行目录；Parcelable、字节及
 * 组合类型保留为设置页不可用项，避免模型产生平台无法安全还原的参数。
 */
@Singleton
internal class AndroidAppFunctionGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppFunctionGateway {
    private val manager = runCatching { AppFunctionManager.getInstance(context) }.getOrNull()
    private val metadataByKey = ConcurrentHashMap<AppFunctionKey, AppFunctionMetadata>()

    override val support: AppFunctionsSupport = when {
        manager == null -> AppFunctionsSupport.UNSUPPORTED_DEVICE
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.EXECUTE_APP_FUNCTIONS,
        ) != PackageManager.PERMISSION_GRANTED -> AppFunctionsSupport.MISSING_SYSTEM_PERMISSION
        else -> AppFunctionsSupport.AVAILABLE
    }

    override fun observeFunctions(): Flow<List<AppFunctionDescriptor>> {
        val activeManager = checkNotNull(manager) { "AppFunctions is unavailable on this device" }
        check(support == AppFunctionsSupport.AVAILABLE) {
            "EXECUTE_APP_FUNCTIONS permission is not granted"
        }
        return activeManager.observeAppFunctions(AppFunctionSearchSpec())
            .map { packages ->
                val descriptors = buildList {
                    packages
                        .filterNot { it.packageName == context.packageName }
                        .forEach { packageMetadata ->
                            val appInfo = runCatching {
                                context.packageManager.getApplicationInfo(packageMetadata.packageName, 0)
                            }.getOrNull()
                            val appLabel = appInfo
                                ?.let(context.packageManager::getApplicationLabel)
                                ?.toString()
                                ?: packageMetadata.packageName
                            val appDescription = packageMetadata
                                .resolveAppFunctionAppMetadata(context)
                                ?.displayDescription
                            packageMetadata.appFunctions.forEach { metadata ->
                                val descriptor = metadata.toDescriptor(
                                    appLabel = appLabel,
                                    appDescription = appDescription,
                                )
                                metadataByKey[descriptor.key] = metadata
                                add(descriptor)
                            }
                        }
                }.sortedWith(
                    compareBy<AppFunctionDescriptor> { it.appLabel.lowercase() }
                        .thenBy { it.key.functionId },
                )
                metadataByKey.keys.retainAll(descriptors.mapTo(hashSetOf()) { it.key })
                descriptors
            }
            .onCompletion { metadataByKey.clear() }
    }

    override suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult {
        val activeManager = manager
            ?: return AppFunctionExecutionResult.Failure("AppFunctions is unavailable.")
        val metadata = metadataByKey[key]
            ?: return AppFunctionExecutionResult.Failure("AppFunction metadata is stale.")
        return try {
            val parameters = metadata.buildParameters(arguments)
            when (
                val response = activeManager.executeAppFunction(
                    ExecuteAppFunctionRequest(
                        targetPackageName = key.packageName,
                        functionIdentifier = key.functionId,
                        functionParameters = parameters,
                    ),
                )
            ) {
                is ExecuteAppFunctionResponse.Success -> AppFunctionExecutionResult.Success(
                    response.returnValue.readValue(
                        ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE,
                        metadata.response.valueType,
                        metadata.components,
                    ),
                )
                is ExecuteAppFunctionResponse.Error -> AppFunctionExecutionResult.Failure(
                    "AppFunction execution failed.",
                )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            AppFunctionExecutionResult.Failure("AppFunction execution failed.")
        }
    }

    private fun AppFunctionMetadata.toDescriptor(
        appLabel: String,
        appDescription: String?,
    ): AppFunctionDescriptor {
        val convertedParameters = parameters.map { parameter ->
            parameter.dataType.toDomainType(components)?.let { type ->
                AppFunctionParameter(
                    name = parameter.name,
                    description = parameter.description,
                    required = parameter.isRequired,
                    type = type,
                )
            }
        }
        val responseSupported = response.valueType is AppFunctionUnitTypeMetadata ||
            response.valueType.toDomainType(components) != null
        val supported = convertedParameters.all { it != null } && responseSupported
        return AppFunctionDescriptor(
            key = AppFunctionKey(packageName, id),
            appLabel = appLabel,
            appDescription = appDescription,
            description = description.ifBlank { id },
            parameters = convertedParameters.filterNotNull(),
            providerEnabled = isEnabled,
            supported = supported,
            unsupportedReason = if (supported) null else {
                "Contains a parameter or return type that cannot be represented safely."
            },
        )
    }
}

private fun AppFunctionDataTypeMetadata.toDomainType(
    components: AppFunctionComponentsMetadata,
    visitedReferences: Set<String> = emptySet(),
): AppFunctionValueType? = when (this) {
    is AppFunctionStringTypeMetadata -> AppFunctionValueType.StringType(
        nullable = isNullable,
        enumValues = enumValues.orEmpty().toList(),
    )
    is AppFunctionIntTypeMetadata -> AppFunctionValueType.IntegerType(
        nullable = isNullable,
        enumValues = enumValues.orEmpty().toList(),
    )
    is AppFunctionLongTypeMetadata -> AppFunctionValueType.IntegerType(nullable = isNullable)
    is AppFunctionFloatTypeMetadata,
    is AppFunctionDoubleTypeMetadata,
    -> AppFunctionValueType.NumberType(nullable = isNullable)
    is AppFunctionBooleanTypeMetadata -> AppFunctionValueType.BooleanType(nullable = isNullable)
    is AppFunctionArrayTypeMetadata -> itemType
        .toDomainType(components, visitedReferences)
        ?.takeUnless { item -> item is AppFunctionValueType.ArrayType }
        ?.let { AppFunctionValueType.ArrayType(it, isNullable) }
    is AppFunctionObjectTypeMetadata -> AppFunctionValueType.ObjectType(
        properties = properties.mapValues { (_, type) ->
            type.toDomainType(components, visitedReferences) ?: return null
        },
        required = required,
        nullable = isNullable,
    )
    is AppFunctionReferenceTypeMetadata -> {
        if (!visitedReferences.addable(referenceDataType)) return null
        components.dataTypes[referenceDataType]
            ?.toDomainType(components, visitedReferences + referenceDataType)
            ?.withNullability(isNullable)
    }
    else -> null
}

private fun Set<String>.addable(value: String): Boolean = value !in this

private fun AppFunctionValueType.withNullability(nullable: Boolean): AppFunctionValueType =
    when (this) {
        is AppFunctionValueType.StringType -> copy(nullable = nullable)
        is AppFunctionValueType.IntegerType -> copy(nullable = nullable)
        is AppFunctionValueType.NumberType -> copy(nullable = nullable)
        is AppFunctionValueType.BooleanType -> copy(nullable = nullable)
        is AppFunctionValueType.ArrayType -> copy(nullable = nullable)
        is AppFunctionValueType.ObjectType -> copy(nullable = nullable)
    }

private fun AppFunctionMetadata.buildParameters(arguments: Map<String, Any>): AppFunctionData {
    val builder = AppFunctionData.Builder(parameters, components)
    parameters.forEach { parameter ->
        val value = arguments[parameter.name] ?: return@forEach
        builder.setValue(parameter.name, parameter.dataType, value, components)
    }
    return builder.build()
}

private fun AppFunctionData.Builder.setValue(
    name: String,
    type: AppFunctionDataTypeMetadata,
    value: Any,
    components: AppFunctionComponentsMetadata,
) {
    when (type) {
        is AppFunctionStringTypeMetadata -> setString(name, value.toString())
        is AppFunctionIntTypeMetadata -> setInt(name, (value as Number).toInt())
        is AppFunctionLongTypeMetadata -> setLong(name, (value as Number).toLong())
        is AppFunctionFloatTypeMetadata -> setFloat(name, (value as Number).toFloat())
        is AppFunctionDoubleTypeMetadata -> setDouble(name, (value as Number).toDouble())
        is AppFunctionBooleanTypeMetadata -> setBoolean(name, value as Boolean)
        is AppFunctionArrayTypeMetadata -> setArray(name, type.itemType, value as List<*>, components)
        is AppFunctionObjectTypeMetadata -> setAppFunctionData(
            name,
            type.buildObject(value as Map<*, *>, components),
        )
        is AppFunctionReferenceTypeMetadata -> {
            val referencedType = components.dataTypes[type.referenceDataType]
                ?: error("Unsupported reference ${type.referenceDataType}")
            setValue(name, referencedType, value, components)
        }
        else -> error("Unsupported AppFunction parameter type")
    }
}

private fun AppFunctionObjectTypeMetadata.buildObject(
    values: Map<*, *>,
    components: AppFunctionComponentsMetadata,
): AppFunctionData {
    val builder = AppFunctionData.Builder(this, components)
    properties.forEach { (name, type) ->
        values[name]?.let { builder.setValue(name, type, it, components) }
    }
    return builder.build()
}

private fun AppFunctionData.Builder.setArray(
    name: String,
    itemType: AppFunctionDataTypeMetadata,
    values: List<*>,
    components: AppFunctionComponentsMetadata,
) {
    when (itemType) {
        is AppFunctionStringTypeMetadata -> setStringList(name, values.map(Any?::toString))
        is AppFunctionIntTypeMetadata -> setIntArray(name, values.map { (it as Number).toInt() }.toIntArray())
        is AppFunctionLongTypeMetadata -> setLongArray(name, values.map { (it as Number).toLong() }.toLongArray())
        is AppFunctionFloatTypeMetadata -> setFloatArray(name, values.map { (it as Number).toFloat() }.toFloatArray())
        is AppFunctionDoubleTypeMetadata ->
            setDoubleArray(name, values.map { (it as Number).toDouble() }.toDoubleArray())
        is AppFunctionBooleanTypeMetadata -> setBooleanArray(name, values.map { it as Boolean }.toBooleanArray())
        is AppFunctionObjectTypeMetadata -> setAppFunctionDataList(
            name,
            values.map { itemType.buildObject(it as Map<*, *>, components) },
        )
        is AppFunctionReferenceTypeMetadata -> {
            val referencedType = components.dataTypes[itemType.referenceDataType]
                ?: error("Unsupported reference ${itemType.referenceDataType}")
            setArray(name, referencedType, values, components)
        }
        else -> error("Unsupported AppFunction array item type")
    }
}

private fun AppFunctionData.readValue(
    name: String,
    type: AppFunctionDataTypeMetadata,
    components: AppFunctionComponentsMetadata,
): Any? = when (type) {
    is AppFunctionUnitTypeMetadata -> emptyMap<String, Any>()
    is AppFunctionStringTypeMetadata -> getStringOrNull(name)
    is AppFunctionIntTypeMetadata -> getIntOrNull(name)
    is AppFunctionLongTypeMetadata -> getLongOrNull(name)
    is AppFunctionFloatTypeMetadata -> getFloatOrNull(name)
    is AppFunctionDoubleTypeMetadata -> getDoubleOrNull(name)
    is AppFunctionBooleanTypeMetadata -> getBooleanOrNull(name)
    is AppFunctionArrayTypeMetadata -> readArray(name, type.itemType, components)
    is AppFunctionObjectTypeMetadata -> getAppFunctionData(name)?.readObject(type, components)
    is AppFunctionReferenceTypeMetadata -> components.dataTypes[type.referenceDataType]
        ?.let { referenced -> readValue(name, referenced, components) }
    else -> null
}

private fun AppFunctionData.readArray(
    name: String,
    itemType: AppFunctionDataTypeMetadata,
    components: AppFunctionComponentsMetadata,
): Any? = when (itemType) {
        is AppFunctionStringTypeMetadata -> getStringList(name)
        is AppFunctionIntTypeMetadata -> getIntArray(name)?.toList()
        is AppFunctionLongTypeMetadata -> getLongArray(name)?.toList()
        is AppFunctionFloatTypeMetadata -> getFloatArray(name)?.toList()
        is AppFunctionDoubleTypeMetadata -> getDoubleArray(name)?.toList()
        is AppFunctionBooleanTypeMetadata -> getBooleanArray(name)?.toList()
        is AppFunctionObjectTypeMetadata -> getAppFunctionDataList(name)?.map {
            it.readObject(itemType, components)
        }
        is AppFunctionReferenceTypeMetadata -> components.dataTypes[itemType.referenceDataType]
            ?.let { referenced -> readArray(name, referenced, components) }
        else -> null
    }

private fun AppFunctionData.readObject(
    type: AppFunctionObjectTypeMetadata,
    components: AppFunctionComponentsMetadata,
): Map<String, Any?> = type.properties.mapValues { (name, propertyType) ->
    readValue(name, propertyType, components)
}
