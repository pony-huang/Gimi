package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局唯一的官方工具请求期组装器 — 所有厂商的官方工具都由它在
 * [OfficialToolset.processLlmRequest] 中按当前请求的模型运行信息现场解析。
 *
 * 每次解析的流程(见 [resolveTools]):
 *
 * 1. [OfficialToolRegistry.specsFor] 按服务 + 协议 + 模型家族门控出适用声明;
 * 2. Tool access ON_DEMAND 模式下跳过 [OfficialToolSpec.searchCandidate] 声明,
 *    它们改由 tool_search 检索候选源按需暴露;
 * 3. 会话级开关([ConversationToolConfiguration],厂商唯一 toolId)决定工具是否注入;
 * 4. 函数级勾选过滤展开为普通声明的工具(GLM/Kimi);厂商原生工具单函数,天然通过。
 *
 * 厂商差异全部收敛在注册表声明里,新增厂商不需要新的 Toolset 类。
 */
class DefaultOfficialToolset @Inject constructor(
    private val registry: OfficialToolRegistry,
) : OfficialToolset {

    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        val onDemand = selection?.toolAccessMode == ToolAccessMode.ON_DEMAND
        return registry.specsFor(
            serviceId = config.serviceId,
            protocol = config.baseType,
            modelId = config.modelId,
        )
            .filter { spec -> !onDemand || !spec.searchCandidate }
            .flatMap { spec -> resolveSpec(spec, config, selection) }
    }

    /**
     * 解析单个官方工具声明在当前请求下的工具实例。
     *
     * 请求期组装与 tool_search 检索候选源([OfficialToolCandidateSource])共用,
     * 保证两条路径的会话开关与函数勾选语义一致。
     */
    internal suspend fun resolveSpec(
        spec: OfficialToolSpec,
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (!selection.isOfficialToolEnabled(spec.toolId)) return emptyList()
        val tools = registry.createTools(spec, config)
        // 函数级勾选只作用于本地执行的工具(工具名即函数 ID);厂商声明/Gemini 原生
        // 工具单工具单函数,工具名是厂商 wire 字面量,工具级开关已足够。
        if (spec.binding !is OfficialToolBinding.LocalFunctions) return tools
        val enabledFunctionIds = selection
            ?.enabledOfficialFunctionIds(spec.toolId)
            ?.takeIf { it.isNotEmpty() && ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in it }
        return if (enabledFunctionIds == null) {
            tools
        } else {
            tools.filter { tool -> tool.name in enabledFunctionIds }
        }
    }
}
