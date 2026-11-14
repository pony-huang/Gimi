package github.ponyhuang.gimi.domain.modelcatalog.model

import javax.inject.Inject

/** 官方工具唯一的厂商支持矩阵与可用性规则；设置目录与 Agent 共用。 */
class OfficialToolSupport @Inject constructor() {
    /**
     * 全部官方工具声明，顺序决定目录展示及请求期组装顺序。
     *
     * OpenAI 与 MiMo 的联网搜索是厂商原生声明（OpenAI 只有 Responses API、MiMo 也只有
     * Chat Completions 的 `tools[].type = "web_search"`），而应用当前的 OpenAI 兼容通道
     * 只能下发 function 工具，无法表达这类声明，因此不做声明；等 OpenAI 兼容通道能下发
     * 厂商 wire 形态时再补回。
     */
    val all: List<OfficialToolSpec> = listOf(
        OfficialToolSpec(
            toolId = OfficialToolIds.DEEPSEEK_WEB_SEARCH,
            templateId = "deepseek",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "DeepSeek web search",
            staticFunctionIds = listOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH),
            canBeUsedAcrossModels = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.DEEPSEEK_WEB_FETCH,
            templateId = "deepseek",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "DeepSeek web fetch",
            staticFunctionIds = listOf(OfficialToolIds.DEEPSEEK_WEB_FETCH),
            canBeUsedAcrossModels = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.ANTHROPIC_WEB_SEARCH,
            templateId = "anthropic",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "Anthropic web search",
            staticFunctionIds = listOf(OfficialToolIds.ANTHROPIC_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderDeclaration(OfficialToolIds.PROVIDER_WEB_SEARCH),
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.MINIMAX_WEB_SEARCH,
            templateId = "minimax",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "MiniMax web search",
            staticFunctionIds = listOf(OfficialToolIds.MINIMAX_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderDeclaration(OfficialToolIds.PROVIDER_WEB_SEARCH),
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.MINIMAX_IMAGE_GENERATION,
            templateId = "minimax",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "MiniMax image generation",
            staticFunctionIds = listOf(
                OfficialToolIds.MINIMAX_TEXT_TO_IMAGE,
                OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE,
            ),
            // 图像生成通过独立 REST API 调用，函数名也为厂商唯一，可安全供其它模型使用。
            canBeUsedAcrossModels = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GEMINI_WEB_SEARCH,
            templateId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Search",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GEMINI_URL_CONTEXT,
            templateId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "URL context",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_URL_CONTEXT),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GEMINI_GOOGLE_MAPS,
            templateId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Maps",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_GOOGLE_MAPS),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GLM_WEB_SEARCH,
            templateId = "glm",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            modelFamilies = setOf("glm"),
            displayName = "GLM web tools",
            staticFunctionIds = listOf(OfficialToolIds.GLM_WEB_SEARCH_FUNCTION, OfficialToolIds.GLM_WEB_READER_FUNCTION),
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.KIMI_WEB_TOOLS,
            templateId = "kimi",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "Kimi web tools",
            staticFunctionIds = listOf(OfficialToolIds.KIMI_SEARCH, OfficialToolIds.KIMI_SEARCH_PRO, OfficialToolIds.KIMI_FETCH),
            canBeUsedAcrossModels = true,
            searchCandidate = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
    )

    /** 按来源模板、请求协议与模型家族筛选声明，不检查实例开关。 */
    fun specsFor(
        templateId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec> = all.filter { spec ->
        spec.templateId == templateId &&
                protocol in spec.protocols &&
                spec.modelBelongsToFamily(modelId)
    }

    /**
     * 当前模型的原生工具与其他已启用实例的独立 API 工具。
     *
     * [sourceInstances] 是当前启用的全部实例：来源匹配按实例的模板身份，
     * 凭据归属保持在各实例内，多实例不会错用密钥。
     */
    fun availableSpecsFor(
        templateId: String,
        protocol: ApiProtocol,
        modelId: String,
        sourceInstances: List<ModelServiceInstance>,
    ): List<OfficialToolSpec> {
        val templateByInstance = sourceInstances.associateBy(ModelServiceInstance::id)
        val active = specsFor(templateId, protocol, modelId)
            .filter { spec -> templateByInstance.values.any { it.canProvideOfficialTools() && it.templateId == spec.templateId } }
        val crossModel = all.filter { spec ->
            spec.templateId != templateId &&
                spec.binding is OfficialToolBinding.LocalFunctions &&
                spec.canBeUsedAcrossModels &&
                templateByInstance.values.any { instance ->
                    instance.templateId == spec.templateId &&
                        instance.canProvideOfficialTools() &&
                        instance.conversationConnection?.let { spec.protocols.contains(it.protocol) } == true
                }
        }
        return (active + crossModel).distinctBy(OfficialToolSpec::toolId)
    }

    /** 模板/协议支持的目录 ID，不收窄模型家族。 */
    fun supportedToolIds(templateId: String, protocol: ApiProtocol): Set<String> = all
        .filter { spec -> spec.templateId == templateId && protocol in spec.protocols }
        .map { it.toolId }
        .toSet()

    private fun OfficialToolSpec.modelBelongsToFamily(modelId: String): Boolean {
        if (modelFamilies.isEmpty()) return true
        val normalized = modelId.substringAfterLast('/').lowercase()
        // 家族名后只接受常见分隔符,避免把 glmatrix 之类无关名称误判为 GLM。
        return modelFamilies.any { family ->
            val f = family.lowercase()
            normalized == f ||
                    normalized.startsWith("$f-") ||
                    normalized.startsWith("${f}_") ||
                    normalized.startsWith("$f.")
        }
    }
}
