@file:OptIn(com.google.adk.kt.annotations.ExperimentalAppFunctionsFeature::class)

package github.ponyhuang.gimi.data.agent.contribution

import android.content.Context
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.*
import com.google.adk.kt.tools.appfunctions.AppFunctionsToolset
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.data.agent.*
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolsetCandidateSource
import github.ponyhuang.gimi.domain.appfunction.AppFunctionRepository
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** 创建官方 SDK 工具集的边界，测试可替换而不访问真实设备。 */
class AppFunctionToolsetFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(packageName: String): Toolset = AppFunctionsToolset(
        context,
        filteredPackageNames = setOf(packageName),
        // 工具会加跨应用命名空间，不注入引用 SDK 原始工具名的说明，避免误导模型。
        injectAppMetadata = false,
    )
}

/** 每应用隔离的工具贡献；常驻、按需检索与能力目录使用同一开关和执行保护。 */
@Singleton
class AppFunctionToolContribution @Inject constructor(
    private val repository: AppFunctionRepository,
    private val factory: AppFunctionToolsetFactory,
) : AgentContribution {
    override val id: String = "appfunction"
    override fun revision(): Any = repository.revision.value

    private fun toolset(packageName: String): Toolset = GuardedAppFunctionToolset(
        packageName, factory.create(packageName), repository,
    )

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
            repository.enabledPackages.value.sorted().map(::toolset)
        } else emptyList()

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        if (spec.toolAccessMode == ToolAccessMode.ON_DEMAND) {
            repository.enabledPackages.value.sorted().map { packageName ->
                ToolsetCandidateSource("appfunction:$packageName", packageName, toolset(packageName))
            }
        } else emptyList()

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        repository.enabledPackages.value.sorted().map { packageName ->
            val source = toolset(packageName)
            try {
                AgentToolCatalogEntry("appfunction:$packageName", source.getTools(null))
            } finally {
                source.close()
            }
        }
}

/** SDK 只枚举声明；失败局限于本应用，关闭后旧请求中的工具也不能执行。 */
internal class GuardedAppFunctionToolset(
    private val packageName: String,
    private val delegate: Toolset,
    private val repository: AppFunctionRepository,
) : Toolset {
    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        if (packageName !in repository.enabledPackages.value) return emptyList()
        val tools = cancellationAwareRunCatching { delegate.getTools(readonlyContext) }.getOrDefault(emptyList())
        // 平台查询可能耗时，查询期间关闭应用时也不继续发布其声明。
        if (packageName !in repository.enabledPackages.value) return emptyList()
        return tools.map { GuardedAppFunctionTool(packageName, it, repository) }
    }

    override fun close() = delegate.close()
}

/** 命名空间保护与执行时开关检查；参数和结果转换仍由官方 SDK 完成。 */
internal class GuardedAppFunctionTool(
    private val packageName: String,
    private val delegate: BaseTool,
    private val repository: AppFunctionRepository,
) : BaseTool(
    name = appFunctionToolName(packageName, delegate.name),
    description = "[$packageName] ${delegate.description}",
    isLongRunning = delegate.isLongRunning,
    customMetadata = delegate.customMetadata,
) {
    override fun declaration() = delegate.declaration()?.copy(name = name, description = description)

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
        if (packageName !in repository.enabledPackages.value) {
            mapOf("error" to "AppFunction access for this app has been disabled in Gimi settings")
        } else delegate.run(context, args)

    override fun close() = delegate.close()
}

/** 限制为模型接受的名称长度，哈希保留包名及长方法名的区别，避免截断引起冲突。 */
internal fun appFunctionToolName(packageName: String, name: String): String {
    fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it) }
    val readable = name.map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_') it else '_' }
        .joinToString("").take(32)
    return "af_${digest(packageName)}_${readable}_${digest(name)}"
}
