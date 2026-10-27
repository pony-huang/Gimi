package github.ponyhuang.gimi.data.agent.tools.official.kimi

import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Kimi 独立网页 API；固定使用官方端点，不复用聊天代理或 Anthropic 基础地址。 */
internal class KimiWebToolApi(
    private val apiKey: String,
    httpClient: OkHttpClient,
) {
    // 付费接口由 Agent 决定重试，避免 OkHttp 隐式重发 HTTP 408 等请求。
    private val httpClient = httpClient.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        // 服务端最多等待 60 秒，客户端需留出传输余量。
        .readTimeout(70, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun execute(functionId: String, payload: JsonObject): Map<String, Any?> {
        val operation = when (functionId) {
            OfficialToolIds.KIMI_SEARCH -> "search"
            OfficialToolIds.KIMI_SEARCH_PRO -> "search_pro"
            OfficialToolIds.KIMI_FETCH -> "fetch"
            else -> error("Unknown Kimi web function")
        }
        val root = post(operation, payload)
        if (operation == "fetch") {
            check(listOf("url", "title", "markdown").all { (root[it] as? JsonPrimitive)?.isString == true }) {
                "Invalid URL, title or markdown from Kimi fetch"
            }
        } else {
            val results = root["search_results"] as? JsonArray ?: error("Missing search_results from Kimi search")
            check(results.all { it is JsonObject }) { "Invalid search_results from Kimi search" }
        }
        return root.mapValues { (_, value) -> value.toNativeValue() }
    }

    private suspend fun post(operation: String, payload: JsonObject): JsonObject {
        val request = Request.Builder()
            .url("https://api.moonshot.cn/v1/tools/$operation")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = httpClient.newCall(request)
            // 读取响应正文前保持 continuation 挂起，取消会同时终止连接和正文读取。
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code} from Kimi $operation")
                            it.body.string()
                        }
                        continuation.resume(text)
                    } catch (failure: IOException) {
                        continuation.resumeWithException(failure)
                    }
                }
            })
        }
        return Json.parseToJsonElement(body) as? JsonObject ?: error("Invalid response from Kimi $operation")
    }

    /** 保留官方结果中的正文、评分、可空站点信息及后续新增字段，转换为 ADK 可序列化的值。 */
    private fun JsonElement.toNativeValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> mapValues { (_, value) -> value.toNativeValue() }
        is JsonArray -> map { it.toNativeValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            else -> doubleOrNull ?: error("Invalid primitive from Kimi search")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
