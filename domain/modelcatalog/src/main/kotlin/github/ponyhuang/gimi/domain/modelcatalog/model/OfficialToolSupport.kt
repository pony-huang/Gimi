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
            serviceId = "deepseek",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "DeepSeek web search",
            staticFunctionIds = listOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH),
            canBeUsedAcrossModels = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.DEEPSEEK_WEB_FETCH,
            serviceId = "deepseek",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "DeepSeek web fetch",
            staticFunctionIds = listOf(OfficialToolIds.DEEPSEEK_WEB_FETCH),
            canBeUsedAcrossModels = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.ANTHROPIC_WEB_SEARCH,
            serviceId = "anthropic",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "Anthropic web search",
            staticFunctionIds = listOf(OfficialToolIds.ANTHROPIC_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderDeclaration(OfficialToolIds.PROVIDER_WEB_SEARCH),
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.MINIMAX_WEB_SEARCH,
            serviceId = "minimax",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "MiniMax web search",
            staticFunctionIds = listOf(OfficialToolIds.MINIMAX_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderDeclaration(OfficialToolIds.PROVIDER_WEB_SEARCH),
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.MINIMAX_IMAGE_GENERATION,
            serviceId = "minimax",
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
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Search",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_WEB_SEARCH),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GEMINI_URL_CONTEXT,
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "URL context",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_URL_CONTEXT),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GEMINI_GOOGLE_MAPS,
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Maps",
            staticFunctionIds = listOf(OfficialToolIds.GEMINI_GOOGLE_MAPS),
            binding = OfficialToolBinding.ProviderNative,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.GLM_WEB_SEARCH,
            serviceId = "glm",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            modelFamilies = setOf("glm"),
            displayName = "GLM web tools",
            staticFunctionIds = listOf(OfficialToolIds.GLM_WEB_SEARCH_FUNCTION, OfficialToolIds.GLM_WEB_READER_FUNCTION),
            binding = OfficialToolBinding.LocalFunctions,
        ),
        OfficialToolSpec(
            toolId = OfficialToolIds.KIMI_WEB_TOOLS,
            serviceId = "kimi",
            protocols = setOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
            displayName = "Kimi web tools",
            staticFunctionIds = listOf(OfficialToolIds.KIMI_SEARCH, OfficialToolIds.KIMI_SEARCH_PRO, OfficialToolIds.KIMI_FETCH),
            canBeUsedAcrossModels = true,
            searchCandidate = true,
            binding = OfficialToolBinding.LocalFunctions,
        ),
    )

    /** 按来源服务、请求协议与模型家族筛选声明，不检查服务开关。 */
    fun specsFor(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec> = all.filter { spec ->
        spec.serviceId == serviceId &&
                protocol in spec.protocols &&
                spec.modelBelongsToFamily(modelId)
    }

    /** 当前模型的原生工具与其他已启用来源的独立 API 工具。 */
    fun availableSpecsFor(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
        sourceServices: List<LLMModelSetting>,
    ): List<OfficialToolSpec> {
        val services = sourceServices.associateBy(LLMModelSetting::id)
        val active = specsFor(serviceId, protocol, modelId)
            .filter { spec -> (services[spec.serviceId]?.canProvideOfficialTools() == true) }
        val crossModel = all.filter { spec ->
            spec.serviceId != serviceId &&
                spec.binding is OfficialToolBinding.LocalFunctions &&
                spec.canBeUsedAcrossModels &&
                protocolFor(spec.serviceId, services)?.let(spec.protocols::contains) == true &&
                (services[spec.serviceId]?.canProvideOfficialTools() == true)
        }
        return (active + crossModel).distinctBy(OfficialToolSpec::toolId)
    }

    /** 服务/协议支持的目录 ID，不收窄模型家族。 */
    fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String> = all
        .filter { spec -> spec.serviceId == serviceId && protocol in spec.protocols }
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

    private fun protocolFor(
        serviceId: String,
        services: Map<String, LLMModelSetting>,
    ): ApiProtocol? = services[serviceId]?.apiProtocol

}

/** 官方工具来源必须同时满足服务启用、官方工具启用和凭据非空。 */
fun LLMModelSetting.canProvideOfficialTools(): Boolean =
    isEnabled && isOfficialToolsEnabled && apiKey.isNotBlank()
