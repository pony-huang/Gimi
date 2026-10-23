package github.ponyhuang.gimi.data.agent.model.files

import com.openai.core.RequestOptions
import com.openai.core.http.Headers
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** SDK 的公开传输接口适配；持有原始 Call，避免关闭解析流时等待阻塞的 socket 读取。 */
internal class CancellableChatHttpClient : HttpClient {
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).callTimeout(Duration.ofSeconds(120)).build()

    // OkHttp 在响应回调结束后不再把流读取视为运行中的 Call，需跟踪至响应体关闭。
    private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()
    fun cancelRequests() { calls.forEach { it.cancel() } }

    override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
        val call = call(request, requestOptions)
        return try { call.execute().toSdkResponse(call) }
        catch (failure: IOException) { calls.remove(call); throw failure }
    }

    override fun executeAsync(request: HttpRequest, requestOptions: RequestOptions): CompletableFuture<HttpResponse> {
        val call = call(request, requestOptions)
        val result = CompletableFuture<HttpResponse>()
        result.whenComplete { _, _ -> if (result.isCancelled) call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { calls.remove(call); result.completeExceptionally(e) }
            override fun onResponse(call: Call, response: Response) {
                if (!result.complete(response.toSdkResponse(call))) {
                    call.cancel()
                    response.close()
                    calls.remove(call)
                }
            }
        })
        return result
    }

    private fun call(request: HttpRequest, options: RequestOptions): Call {
        val builder = Request.Builder().url(request.url())
        request.headers.names().forEach { name -> request.headers.values(name).forEach { builder.addHeader(name, it) } }
        val sdkBody = request.body
        val body = sdkBody?.let { payload -> object : RequestBody() {
            override fun contentType() = payload.contentType()?.toMediaTypeOrNull()
            override fun contentLength() = payload.contentLength()
            override fun isOneShot() = !payload.repeatable()
            override fun writeTo(sink: BufferedSink) { payload.writeTo(sink.outputStream()) }
        } }
        val client = http.newBuilder().apply {
            options.timeout?.let {
                connectTimeout(it.connect()).readTimeout(it.read()).writeTimeout(it.write()).callTimeout(it.request())
            }
        }.build()
        return client.newCall(builder.method(request.method.name, body).build()).also { calls.add(it) }
    }

    private fun Response.toSdkResponse(call: Call): HttpResponse {
        val response = this
        return object : HttpResponse {
            override fun statusCode() = response.code
            override fun headers(): Headers = Headers.builder().putAll(response.headers.toMultimap()).build()
            override fun body() = response.body.byteStream()
            override fun close() {
                call.cancel()
                try { response.close() } finally { calls.remove(call) }
            }
        }
    }

    override fun close() {
        cancelRequests()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
