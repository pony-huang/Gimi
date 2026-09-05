package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmReaderTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebSearchTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebToolApi
import github.ponyhuang.gimi.data.agent.tools.official.kimi.KimiFormulaCache
import github.ponyhuang.gimi.data.agent.tools.official.kimi.KimiFormulaTool
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 官方工具声明 — 描述一个厂商内置工具的目录身份、适用范围与构造方式。
 *
 * @property toolId 目录级工具 ID,厂商唯一,用于 UI 渲染与
 * [github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration]
 * 持久化;不是发给厂商 API 的声明名。
 * @property serviceId 提供该工具的模型服务 ID。
 * @property protocols 该服务在哪些 API 协议下支持此工具。
 * @property modelFamilies 可选的模型 ID 家族收窄(按请求模型 ID 前缀匹配);
 * 空集合表示该服务下所有模型都可用,只按服务 + 协议门控。
 * @property displayName Agent 侧来源展示名(如 tool_search 检索候选来源)。
 * @property staticFunctionIds 静态函数 ID 列表;空列表表示函数列表需要通过
 * [dynamicFunctions] 动态获取(如 Kimi formulas)。
 * @property searchCandidate 是否在 Tool access ON_DEMAND 模式下转为 tool_search 检索候选源。
 * @property binding 工具构造方式。
 * @property dynamicFunctions 动态函数列表获取(按当前服务凭据);仅当
 * [staticFunctionIds] 为空时生效,与厂商本地执行绑定配套使用。
 */
data class OfficialToolSpec(
    val toolId: String,
    val serviceId: String,
    val protocols: Set<ApiProtocol>,
    val modelFamilies: Set<String> = emptySet(),
    val displayName: String,
    val staticFunctionIds: List<String> = emptyList(),
    val searchCandidate: Boolean = false,
    val binding: OfficialToolBinding,
    val dynamicFunctions: (suspend (serviceId: String, apiKey: String) -> List<OfficialToolFunction>)? = null,
)

/** 官方工具的三种接入方式 — 厂商差异收敛为"怎么构造工具"。 */
sealed interface OfficialToolBinding {

    /**
     * 厂商协议保留声明:本地只下发 [wireName] 字面量声明,由厂商远端执行,
     * 协议模型适配层(Openai/Claude)负责转换 wire 格式。
     */
    data class ProviderDeclaration(
        val wireName: String,
    ) : OfficialToolBinding

    /** ADK 自带的厂商原生工具(如 Gemini 的 googleSearch),声明与执行均由 ADK 透传。 */
    data class AdkNative(
        val create: () -> BaseTool,
    ) : OfficialToolBinding

    /** 本地执行的函数工具(GLM/Kimi):按当前请求凭据构造真正可执行的工具实例。 */
    data class LocalFunctions(
        val create: suspend (config: ModelRuntimeMetadata, apiKey: String) -> List<BaseTool>,
    ) : OfficialToolBinding
}

/**
 * 官方工具运行时门控与构造入口 — "哪个服务/协议/模型家族支持哪个官方工具"
 * 在整个 App 内只在本类与 [all] 中维护。
 *
 * 消费方只有两类:请求期组装的 [DefaultOfficialToolset] 与目录查询的
 * [DefaultOfficialToolFunctionCatalog],两者共享同一份声明,保证 UI 勾选与
 * 实际注入永远不会各说各话。
 */
@Singleton
class OfficialToolRegistry @Inject constructor(
    private val kimiFormulaCache: KimiFormulaCache,
    private val httpClient: OkHttpClient,
    private val modelServices: AgentModelConfigurationSource,
) {

    /** 全部官方工具声明;每行由对应厂商自行维护,toolId 厂商唯一。 */
    val all: List<OfficialToolSpec> = listOf(
        OfficialToolSpec(
            toolId = "openai_web_search",
            serviceId = "openai",
            protocols = setOf(ApiProtocol.Standard),
            displayName = "OpenAI web search",
            staticFunctionIds = listOf("openai_web_search"),
            binding = OfficialToolBinding.ProviderDeclaration(wireName = "web_search"),
        ),
        OfficialToolSpec(
            toolId = "anthropic_web_search",
            serviceId = "anthropic",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "Anthropic web search",
            staticFunctionIds = listOf("anthropic_web_search"),
            binding = OfficialToolBinding.ProviderDeclaration(wireName = "web_search"),
        ),
        OfficialToolSpec(
            toolId = "minimax_web_search",
            serviceId = "minimax",
            protocols = setOf(ApiProtocol.Anthropic),
            displayName = "MiniMax web search",
            staticFunctionIds = listOf("minimax_web_search"),
            binding = OfficialToolBinding.ProviderDeclaration(wireName = "web_search"),
        ),
        OfficialToolSpec(
            toolId = "mimo_web_search",
            serviceId = "mimo",
            protocols = setOf(ApiProtocol.Standard),
            displayName = "MiMo web search",
            staticFunctionIds = listOf("mimo_web_search"),
            binding = OfficialToolBinding.ProviderDeclaration(wireName = "web_search"),
        ),
        OfficialToolSpec(
            toolId = "gemini_web_search",
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Search",
            staticFunctionIds = listOf("gemini_web_search"),
            binding = OfficialToolBinding.AdkNative(create = ::GoogleSearchTool),
        ),
        OfficialToolSpec(
            toolId = "gemini_url_context",
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "URL context",
            staticFunctionIds = listOf("gemini_url_context"),
            binding = OfficialToolBinding.AdkNative(create = ::UrlContextTool),
        ),
        OfficialToolSpec(
            toolId = "gemini_google_maps",
            serviceId = "gemini",
            protocols = setOf(ApiProtocol.Gemini),
            displayName = "Google Maps",
            staticFunctionIds = listOf("gemini_google_maps"),
            binding = OfficialToolBinding.AdkNative(create = ::GoogleMapsTool),
        ),
        OfficialToolSpec(
            toolId = "glm_web_search",
            serviceId = "glm",
            protocols = setOf(ApiProtocol.Standard, ApiProtocol.Anthropic),
            modelFamilies = setOf("glm"),
            displayName = "GLM web tools",
            staticFunctionIds = listOf(GlmWebSearchTool.NAME, GlmReaderTool.NAME),
            binding = OfficialToolBinding.LocalFunctions { config, apiKey ->
                val api = GlmWebToolApi(
                    apiKey = apiKey,
                    baseUrl = config.fullBaseUrl,
                    httpClient = httpClient,
                )
                listOf(
                    GlmWebSearchTool(api),
                    GlmReaderTool(api),
                )
            },
        ),
        OfficialToolSpec(
            toolId = "kimi_formulas",
            serviceId = "kimi",
            protocols = setOf(ApiProtocol.Standard, ApiProtocol.Anthropic),
            modelFamilies = setOf("kimi", "moonshot"),
            displayName = "Kimi formulas",
            staticFunctionIds = emptyList(),
            searchCandidate = true,
            binding = OfficialToolBinding.LocalFunctions { config, apiKey ->
                kimiFormulaCache.fetch(
                    serviceId = config.serviceId,
                    apiKey = apiKey,
                ).map { declaration ->
                    KimiFormulaTool(
                        apiKey = apiKey,
                        declaration = declaration,
                        httpClient = httpClient,
                    )
                }
            },
            dynamicFunctions = { serviceId, apiKey ->
                kimiFormulaCache.fetch(serviceId = serviceId, apiKey = apiKey)
                    .map { declaration ->
                        OfficialToolFunction(
                            id = declaration.name,
                            name = declaration.name,
                            description = declaration.description,
                        )
                    }
            },
        ),
    )

    private val specsById: Map<String, OfficialToolSpec> = all.associateBy { it.toolId }

    /**
     * 按当前请求的运行时信息解析适用的官方工具声明:服务 + 协议必须匹配,
     * 声明了模型家族时还要求请求模型 ID 属于该家族。
     */
    fun specsFor(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec> = all.filter { spec ->
        spec.serviceId == serviceId &&
                protocol in spec.protocols &&
                spec.modelBelongsToFamily(modelId)
    }

    /**
     * 服务 + 协议粒度的工具目录(不含模型家族收窄),与会话工具配置初始化、
     * UI 工具列表的旧语义保持一致:模型家族差异由运行时门控兜底。
     */
    fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String> = all
        .filter { spec -> spec.serviceId == serviceId && protocol in spec.protocols }
        .map { it.toolId }
        .toSet()

    fun specById(toolId: String): OfficialToolSpec? = specsById[toolId]

    /**
     * 当前服务 + 协议下由厂商远端执行的保留声明字面量集合。
     *
     * 协议模型适配层据此把同名声明转换成厂商原生 wire 格式;本地执行的
     * 同名函数(GLM 搜索等)不在集合内,保持普通 function 下发。
     */
    fun providerDeclaredWireNames(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): Set<String> = specsFor(serviceId, protocol, modelId)
        .mapNotNull { spec ->
            (spec.binding as? OfficialToolBinding.ProviderDeclaration)?.wireName
        }
        .toSet()

    /** 构造 [spec] 在当前请求下真正可用的工具实例;凭据缺失时返回空列表。 */
    suspend fun createTools(spec: OfficialToolSpec, config: ModelRuntimeMetadata): List<BaseTool> =
        when (val binding = spec.binding) {
            is OfficialToolBinding.ProviderDeclaration ->
                listOf(OfficialBuiltInTool(declarationName = binding.wireName))

            is OfficialToolBinding.AdkNative -> listOf(binding.create())

            is OfficialToolBinding.LocalFunctions ->
                apiKeyForService(spec.serviceId)
                    ?.takeIf(String::isNotBlank)
                    ?.let { apiKey -> binding.create(config, apiKey) }
                    .orEmpty()
        }

    /** 目录查询:返回 [toolId] 的函数列表;静态工具为固定 ID,动态工具(如 Kimi)现场获取。 */
    suspend fun listFunctions(toolId: String): List<OfficialToolFunction> {
        val spec = specsById[toolId] ?: return emptyList()
        spec.staticFunctionIds.takeIf { it.isNotEmpty() }?.let { ids ->
            return ids.map { functionId ->
                OfficialToolFunction(id = functionId, name = functionId, description = functionId)
            }
        }
        val dynamic = spec.dynamicFunctions ?: return emptyList()
        val apiKey = apiKeyForService(spec.serviceId)?.takeIf(String::isNotBlank) ?: return emptyList()
        return dynamic(spec.serviceId, apiKey)
    }

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

    /** 从安全模型配置源读取服务的凭据;凭据不进入 RunConfig metadata。 */
    private fun apiKeyForService(serviceId: String): String? = modelServices
        .currentServices()
        .firstOrNull { service -> service.id == serviceId && service.isEnabled }
        ?.apiKey
}
