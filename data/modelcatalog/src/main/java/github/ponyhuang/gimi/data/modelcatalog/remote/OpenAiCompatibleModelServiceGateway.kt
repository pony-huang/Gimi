package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OpenAiCompatibleModelServiceGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelServiceRemoteGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validateConnection(connection: ResolvedServiceConnection): ServiceConnectionResult =
        withContext(ioDispatcher) {
            executeWithCatalogFallback(connection).use { response ->
                when (response.code) {
                    200 -> ServiceConnectionResult.Succeeded
                    401, 403 -> ServiceConnectionResult.AuthFailed
                    else -> ServiceConnectionResult.NetworkFailed
                }
            }
        }

    override suspend fun fetchModels(connection: ResolvedServiceConnection): List<Model> =
        withContext(ioDispatcher) {
            // 只有对话连接提供模型目录接口；语音连接的清单由内置目录或手动添加给出。
            require(connection.capability == ServiceCapability.Conversation) { "非对话连接不提供模型目录接口" }
            executeWithCatalogFallback(connection).use { response ->
                check(response.isSuccessful) { "Model list request failed with HTTP ${response.code}" }
                json.decodeFromString<OpenAiModelsResponse>(response.body.string()).data
                    .filter { it.id.isNotBlank() }.distinctBy { it.id }.map { Model(it.id, it.id) }
            }
        }

    /** 目录探测按候选顺序回退，仅 404（地址不存在）触发下一候选；401/403 说明端点存在，不回退。 */
    private suspend fun executeWithCatalogFallback(connection: ResolvedServiceConnection): Response {
        var last: Response? = null
        for (request in catalogRequests(connection)) {
            last?.close()
            val response = execute(request)
            if (response.code != 404) return response
            last = response
        }
        return last ?: error("目录探测候选为空")
    }

    private fun catalogRequests(connection: ResolvedServiceConnection): List<Request> {
        val base = connection.endpoint.trim().trimEnd('/')
        val requests = buildList {
            if (connection.protocol == ApiProtocol.Anthropic) {
                add(anthropicCatalog(connection, if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"))
                // 厂商 Anthropic 兼容入口（DeepSeek/Kimi/MiMo 等）常不带目录接口；
                // 剥掉 /anthropic 后缀回落 OpenAI 根地址，聊天仍走原始入口。
                val openAiBase = base.removeSuffix("/anthropic").trimEnd('/')
                if (openAiBase != base) {
                    add(bearerCatalog(connection, "$openAiBase/v1/models"))
                    add(bearerCatalog(connection, "$openAiBase/models"))
                }
            } else {
                // Gemini 当前仅通过兼容端点提供目录检测，聊天仍使用原生实现。
                add(bearerCatalog(connection, "$base/models"))
            }
        }
        return requests
    }

    private fun anthropicCatalog(connection: ResolvedServiceConnection, url: String): Request =
        Request.Builder().get().url(url).header("Accept", "application/json")
            .header("x-api-key", connection.apiKey.trim()).header("anthropic-version", "2023-06-01")
            .build()

    private fun bearerCatalog(connection: ResolvedServiceConnection, url: String): Request =
        Request.Builder().get().url(url).header("Accept", "application/json")
            .header("Authorization", "Bearer ${connection.apiKey.trim()}").build()

    /** 编辑连接或退出时取消底层请求；取消发生在回调交付之后时也关闭响应体。 */
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, unconsumed, _ -> unconsumed.close() }
            }
        })
    }
}

/** OpenAI 兼容和 Anthropic 模型列表共有的 data 数组。 */
@Serializable
private data class OpenAiModelsResponse(val data: List<OpenAiModelEntry> = emptyList())

/** 远端模型的精确请求 ID；其余元数据由本地目录补充。 */
@Serializable
private data class OpenAiModelEntry(val id: String = "")
