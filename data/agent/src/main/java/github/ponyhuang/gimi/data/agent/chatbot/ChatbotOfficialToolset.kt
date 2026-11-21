package github.ponyhuang.gimi.data.agent.chatbot

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.CHATBOT_WEB_SEARCH_TOOL_IDS
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chatbot 形态的官方工具集：只放行当前模型厂商自有的联网检索工具。
 *
 * 委托 [DefaultOfficialToolset] 按目录解析（含实例启用与协议门控），随后按当前模型的
 * 模板身份收口：跨模型独立 API 工具与其它厂商的检索工具一律不进入 Chatbot；没有检索工具
 * 声明的厂商解析为空集，即无工具的普通 agent。工具的会话级开关仍由请求期的
 * [ConversationToolConfiguration] 过滤，用户勾选变化不需要重建 Agent。
 */
@Singleton
class ChatbotOfficialToolset @Inject constructor(
    private val delegate: DefaultOfficialToolset,
    private val catalog: OfficialToolFunctionCatalog,
) : OfficialToolset {

    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        val allowed = allowedToolNames(config.templateId.orEmpty())
        if (allowed.isEmpty()) return emptyList()
        return delegate.resolveTools(config, selection).filter { it.name in allowed }
    }

    /**
     * 当前模板在 Chatbot 形态可声明的工具名：厂商远端执行声明用本地声明名
     * （协议适配层据此翻译成厂商 wire 形态），本地执行工具用函数 ID；目录条目缺失时
     * 按目录 ID 兜底（目录 ID 即单函数工具的声明名）。
     */
    private fun allowedToolNames(templateId: String): Set<String> =
        CHATBOT_WEB_SEARCH_TOOL_IDS[templateId].orEmpty().flatMap { toolId ->
            val spec = catalog.all.firstOrNull { it.toolId == toolId }
            when (val binding = spec?.binding) {
                is OfficialToolBinding.ProviderDeclaration -> listOf(binding.declarationName)
                null -> listOf(toolId)
                else -> spec.staticFunctionIds.ifEmpty { listOf(toolId) }
            }
        }.toSet()
}
