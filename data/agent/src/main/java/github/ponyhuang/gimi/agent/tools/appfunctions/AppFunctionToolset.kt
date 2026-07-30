package github.ponyhuang.gimi.agent.tools.appfunctions

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionValueType
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** 将用户明确启用的外部 AppFunctions 适配为 ADK 工具。 */
@Singleton
class AppFunctionToolset @Inject constructor(
    private val repository: AppFunctionRepository,
) : Toolset {
    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val candidates = repository.state.value.enabledFunctions.map { descriptor ->
            AppFunctionToolName.from(descriptor.key) to descriptor
        }
        // 名称碰撞时两个函数都不加载，避免确认后执行到错误的跨应用目标。
        val collisions = candidates.groupingBy(Pair<String, AppFunctionDescriptor>::first)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        return candidates.mapNotNull { (name, descriptor) ->
            descriptor.takeIf { name !in collisions }?.let {
                AppFunctionTool(name, it, repository)
            }
        }
    }
}

/** AppFunction 的确定性 ADK 工具名生成规则。 */
internal object AppFunctionToolName {
    private const val MAX_LENGTH = 64
    private const val PREFIX = "appfn_"
    private const val HASH_LENGTH = 12

    fun from(key: AppFunctionKey): String {
        val readable = "${key.packageName}_${key.functionId}"
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .trim('_')
            .ifBlank { "function" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.encoded.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(HASH_LENGTH)
        val readableLimit = MAX_LENGTH - PREFIX.length - HASH_LENGTH - 1
        return "$PREFIX${readable.take(readableLimit)}_$digest"
    }
}

private class AppFunctionTool(
    name: String,
    private val descriptor: AppFunctionDescriptor,
    private val repository: AppFunctionRepository,
) : FunctionTool(
    name = name,
    description = "${descriptor.appLabel}: ${descriptor.description}",
    requiresConfirmation = true,
) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = descriptor.parameters.associate { parameter ->
                parameter.name to parameter.type.toSchema(parameter.description)
            },
            required = descriptor.parameters
                .filter { parameter -> parameter.required }
                .map { parameter -> parameter.name },
        ),
    )

    override suspend fun execute(
        context: ToolContext,
        args: Map<String, Any>,
    ): Any = when (val result = repository.execute(descriptor.key, args)) {
        is AppFunctionExecutionResult.Success -> mapOf("result" to result.value)
        is AppFunctionExecutionResult.Failure -> mapOf(ERROR_KEY to result.message)
    }
}

private fun AppFunctionValueType.toSchema(description: String? = null): Schema = when (this) {
    is AppFunctionValueType.StringType -> Schema(
        type = Type.STRING,
        description = description,
        enum = enumValues.takeIf(List<String>::isNotEmpty),
    )
    is AppFunctionValueType.IntegerType -> Schema(
        type = Type.INTEGER,
        description = description,
        enum = enumValues.takeIf(List<Int>::isNotEmpty)?.map(Int::toString),
    )
    is AppFunctionValueType.NumberType -> Schema(
        type = Type.NUMBER,
        description = description,
    )
    is AppFunctionValueType.BooleanType -> Schema(
        type = Type.BOOLEAN,
        description = description,
    )
    is AppFunctionValueType.ArrayType -> Schema(
        type = Type.ARRAY,
        description = description,
        items = itemType.toSchema(),
    )
    is AppFunctionValueType.ObjectType -> Schema(
        type = Type.OBJECT,
        description = description,
        properties = properties.mapValues { (_, type) -> type.toSchema() },
        required = required,
    )
}
