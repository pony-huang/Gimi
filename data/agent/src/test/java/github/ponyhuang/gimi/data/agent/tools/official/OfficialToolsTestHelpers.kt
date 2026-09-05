package github.ponyhuang.gimi.data.agent.tools.official

import github.ponyhuang.gimi.data.agent.tools.official.kimi.KimiFormulaCache
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
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

fun testKimiFormulaCache(httpClient: OkHttpClient = testHttpClient()): KimiFormulaCache =
    KimiFormulaCache(httpClient)

/** 无任何已启用服务的配置源;官方工具因此拿不到凭据。 */
fun emptyServices(): AgentModelConfigurationSource = mockk {
    every { currentServices() } returns emptyList()
}

fun servicesWith(
    serviceId: String,
    apiKey: String = "key",
): AgentModelConfigurationSource {
    val service = mockk<LLMModelSetting> {
        every { id } returns serviceId
        every { isEnabled } returns true
        every { apiKey } returns apiKey
    }
    return mockk {
        every { currentServices() } returns listOf(service)
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
