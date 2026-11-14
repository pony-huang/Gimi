package github.ponyhuang.gimi.data.agent.tools.official

import github.ponyhuang.gimi.data.modelcatalog.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** 官方工具测试共用夹具:注册表依赖与 canned HTTP client。 */

fun testHttpClient(): OkHttpClient = OkHttpClient()

/** 无任何已启用服务的配置源;官方工具因此拿不到凭据。 */
fun emptyServices(): AgentModelConfigurationSource = mockk {
    every { currentInstances() } returns emptyList()
}

/**
 * 构造一个厂商服务实例:实例 ID 与模板 ID 同为 [templateId],仅含一条
 * OpenAI 兼容对话连接,凭据走实例共享密钥 —— 与旧 LLMModelSetting 夹具语义等价。
 */
fun officialToolsInstance(
    templateId: String,
    apiKey: String,
    isOfficialToolsEnabled: Boolean = true,
    protocol: ApiProtocol = ApiProtocol.OpenaiChatcompletions,
    endpoint: String = "https://example.com",
): ModelServiceInstance = ModelServiceInstance(
    id = templateId,
    name = templateId,
    templateId = templateId,
    isEnabled = true,
    isOfficialToolsEnabled = isOfficialToolsEnabled,
    sharedApiKey = apiKey,
    connections = listOf(
        ServiceConnection(
            id = "conversation",
            capability = ServiceCapability.Conversation,
            protocol = protocol,
            endpoint = endpoint,
        ),
    ),
)

fun servicesWith(
    serviceId: String,
    apiKey: String = "key",
): AgentModelConfigurationSource {
    val instance = officialToolsInstance(serviceId, apiKey)
    return mockk {
        every { currentInstances() } returns listOf(instance)
    }
}

/** Short-circuits every request with a canned response. */
fun cannedClient(
    code: Int,
    body: String,
    onRequest: (Request) -> Unit = {},
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        onRequest(chain.request())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("canned")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
    .build()

/** 固定目录供 UI 查询和 Agent 工厂使用。 */
fun testCatalog(
    modelServices: AgentModelConfigurationSource = emptyServices(),
) = DefaultOfficialToolFunctionCatalog(OfficialToolSupport(), modelServices)
