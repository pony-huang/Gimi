package dev.e2b.sdk

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.Closeable
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

internal object Json {
    val gson = Gson()
    fun encode(value: Any): String = gson.toJson(value)
    fun parse(value: String): JsonElement = try {
        JsonParser.parseString(value)
    } catch (_: JsonParseException) {
        throw ProtocolException("Invalid JSON response")
    }
    fun obj(value: JsonElement): JsonObject = if (value.isJsonObject) value.asJsonObject
        else throw ProtocolException("Expected a JSON object")
}

internal fun JsonObject.text(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
internal fun JsonObject.requiredText(name: String): String = text(name)?.takeIf { it.isNotBlank() }
    ?: throw ProtocolException("Missing response field: $name")
internal fun JsonObject.number(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0
internal fun JsonObject.strings(name: String): Map<String, String> = getAsJsonObject(name)?.entrySet()
    ?.associate { it.key to it.value.asString } ?: emptyMap()

internal fun failure(status: Int?, code: String? = null, retryAfter: String? = null): E2BException = when {
    status == 404 || code == "not_found" -> NotFoundException(status)
    status == 401 || status == 403 || code == "unauthenticated" || code == "permission_denied" -> AuthenticationException(status)
    status == 429 || code == "resource_exhausted" -> RateLimitException(status, retryAfter)
    else -> E2BException("E2B request failed${status?.let { ": HTTP $it" } ?: ""}${code?.let { ": $it" } ?: ""}", status, code, retryAfter)
}

internal class HttpTransport(private val config: E2BConfig) : Closeable {
    // 创建/执行操作不能被自动重试；跨域重定向也不能携带 API 密钥。
    private val client = OkHttpClient.Builder()
        .connectTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    @Volatile private var closed = false

    fun url(base: String, vararg segments: String): HttpUrl = base.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
    }.build()

    fun jsonBody(value: Any): RequestBody = Json.encode(value).toRequestBody("application/json".toMediaType())

    fun api(method: String, url: HttpUrl, value: Any? = null): Response = execute(
        Request.Builder().url(url).header("X-API-Key", config.apiKey)
            .method(method, value?.let(::jsonBody)).build(), config.requestTimeoutSeconds,
    )

    fun envdRequest(base: String, segments: List<String>, sandboxId: String, token: String?, user: String?, port: Int = 49983): Request.Builder {
        return Request.Builder().url(url(base, *segments.toTypedArray()))
            .header("E2b-Sandbox-Id", sandboxId)
            .header("E2b-Sandbox-Port", port.toString())
            .apply {
                token?.let { header("X-Access-Token", it) }
                user?.let { header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$it:".toByteArray(Charsets.UTF_8))) }
            }
    }

    fun execute(request: Request, timeoutSeconds: Int): Response = network { call(request, timeoutSeconds).execute() }

    fun call(request: Request, timeoutSeconds: Int, readTimeoutSeconds: Int = if (timeoutSeconds == 0) 0 else config.requestTimeoutSeconds): Call {
        check(!closed) { "E2BClient is closed" }
        val configured = client.newBuilder()
            .readTimeout(readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        return configured.newCall(request.newBuilder().header("User-Agent", "e2b-kotlin/0.1.0").build())
    }

    fun check(response: Response) {
        if (response.isSuccessful) return
        val body = network { response.peekBody(64 * 1024).string() }
        val code = try {
            Json.obj(Json.parse(body)).text("code")
        } catch (_: RuntimeException) { null }
        throw failure(response.code, code, response.header("Retry-After"))
    }

    fun readJson(response: Response): JsonElement {
        check(response)
        return Json.parse(network { response.body?.string() } ?: throw ProtocolException("Missing response body"))
    }

    fun cancelRequests() = client.dispatcher.cancelAll()

    override fun close() {
        closed = true
        cancelRequests()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

internal class Envd(
    private val http: HttpTransport,
    private val config: E2BConfig,
    val sandboxId: String,
    private val domain: String,
    private val token: String?,
    version: String,
) {
    private val base = config.sandboxUrl ?: if (domain in setOf("e2b.app", "e2b.dev", "e2b.pro", "e2b-staging.dev")) {
        "https://sandbox.$domain"
    } else "https://49983-$sandboxId.$domain"
    private val legacyUser = version.substringBefore('-').split('.').let {
        val parts = it.map { part -> part.toIntOrNull() ?: throw ProtocolException("Invalid envd version") }
        parts.getOrElse(0) { 0 } == 0 && parts.getOrElse(1) { 0 } < 4
    }

    private fun user(value: String?) = value ?: if (legacyUser) "user" else null

    fun request(segments: List<String>, username: String? = null): Request.Builder =
        http.envdRequest(base, segments, sandboxId, token, user(username))

    fun executeCode(code: String, options: CodeOptions): Response {
        val interpreterBase = config.codeInterpreterUrl ?: if (domain in setOf("e2b.app", "e2b.dev", "e2b.pro", "e2b-staging.dev")) {
            "https://sandbox.$domain"
        } else "https://49999-$sandboxId.$domain"
        val request = http.envdRequest(interpreterBase, listOf("execute"), sandboxId, token, null, 49999)
            .post(http.jsonBody(mapOf("code" to code, "language" to options.language, "env_vars" to options.envs.toMap())))
            .build()
        val response = network { http.call(request, options.timeoutSeconds, options.timeoutSeconds).execute() }
        try {
            http.check(response)
            return response
        } catch (error: Throwable) {
            response.close()
            throw error
        }
    }

    fun unary(service: String, method: String, body: Any, username: String? = null): JsonObject {
        val request = request(listOf(service, method), username)
            .header("Connect-Protocol-Version", "1").post(http.jsonBody(body)).build()
        return http.execute(request, config.requestTimeoutSeconds).use { Json.obj(http.readJson(it)) }
    }

    fun stream(method: String, body: Any, options: CommandOptions): ConnectStream {
        val payload = Json.encode(body).toByteArray(Charsets.UTF_8)
        // Connect 流不是换行 JSON：每个消息有标志位和四字节大端长度。
        val framed = ByteArray(payload.size + 5)
        java.nio.ByteBuffer.wrap(framed).put(0.toByte()).putInt(payload.size).put(payload)
        val request = request(listOf("process.Process", method), options.user)
            .header("Connect-Protocol-Version", "1")
            .header("Keepalive-Ping-Interval", "50")
            .header("Connect-Accept-Encoding", "identity")
            .apply {
                if (options.timeoutSeconds > 0) header("Connect-Timeout-Ms", (options.timeoutSeconds.toLong() * 1000).toString())
            }
            .post(framed.toRequestBody("application/connect+json".toMediaType())).build()
        val call = http.call(request, options.timeoutSeconds)
        val response = network { call.execute() }
        try {
            http.check(response)
            if (response.header("Content-Type")?.substringBefore(';')?.trim() != "application/connect+json") {
                throw ProtocolException("Expected a Connect JSON stream")
            }
            return ConnectStream(call, response)
        } catch (error: Throwable) {
            response.close()
            throw error
        }
    }

    fun readFile(path: String, username: String?, maxBytes: Int? = null): ByteArray {
        val builder = http.envdRequest(base, listOf("files"), sandboxId, token, null)
        val url = builder.build().url.newBuilder().addQueryParameter("path", path).apply {
            user(username)?.let { addQueryParameter("username", it) }
        }.build()
        return http.execute(builder.url(url).get().build(), config.requestTimeoutSeconds).use {
            http.check(it)
            val body = it.body ?: throw ProtocolException("Missing file body")
            if (maxBytes == null) network { body.bytes() } else {
                if (body.contentLength() > maxBytes) throw FileSizeLimitException()
                val output = okio.Buffer()
                val source = body.source()
                network {
                    while (true) {
                        val read = source.read(output, minOf(8192L, maxBytes.toLong() + 1 - output.size))
                        if (read == -1L) break
                        if (output.size > maxBytes) throw FileSizeLimitException()
                    }
                    output.readByteArray()
                }
            }
        }
    }

    fun writeFile(path: String, bytes: ByteArray, username: String?): JsonElement {
        val builder = http.envdRequest(base, listOf("files"), sandboxId, token, null)
        val url = builder.build().url.newBuilder().addQueryParameter("path", path).apply {
            user(username)?.let { addQueryParameter("username", it) }
        }.build()
        // Multipart 在所有受支持的 envd 版本可用；文件内容不走 JSON/base64。
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", path, bytes.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.execute(builder.url(url).post(body).build(), config.requestTimeoutSeconds).use { http.readJson(it) }
    }
}

internal class ConnectStream(private val call: Call, private val response: Response) : Closeable {
    private val source: BufferedSource = response.body?.source() ?: throw ProtocolException("Missing stream body")
    private var finished = false

    fun next(): JsonObject? = network { readNext() }

    private fun readNext(): JsonObject? {
        if (finished) return null
        if (!source.request(5)) throw ProtocolException("Truncated Connect stream header")
        val flags = source.readByte().toInt() and 255
        val length = source.readInt()
        if (length < 0 || length > 16 * 1024 * 1024) throw ProtocolException("Connect frame exceeds 16 MiB")
        if (!source.request(length.toLong())) throw ProtocolException("Truncated Connect stream payload")
        if (flags != 0 && flags != 2) throw ProtocolException("Unsupported Connect frame flags")
        val message = Json.obj(Json.parse(source.readUtf8(length.toLong())))
        if (flags == 2) {
            finished = true
            message.getAsJsonObject("error")?.let { throw failure(null, it.text("code")) }
            return null
        }
        return message
    }

    override fun close() {
        call.cancel()
        response.close()
    }
}

internal inline fun <T> network(block: () -> T): T = try { block() } catch (error: IOException) { throw TransportException(error) }
