package github.ponyhuang.gimi.data.agent.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** MCP transport 共用的协程消息分发、异常上报与 JSON 映射逻辑。 */
internal abstract class BaseAndroidMcpTransport : Transport {
  protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val inboundHandler = AtomicReference<suspend (JSONRPCMessage) -> Unit>()
  private val errorHandler = AtomicReference<(Throwable) -> Unit>({})
  private val closeHandler = AtomicReference<() -> Unit>({})

  override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
    inboundHandler.set(block)
  }

  override fun onError(block: (Throwable) -> Unit) {
    errorHandler.set(block)
  }

  override fun onClose(block: () -> Unit) {
    closeHandler.set(block)
  }

  protected suspend fun dispatch(message: JSONRPCMessage) {
    val result = (message as? JSONRPCResponse)?.result
    if (result is InitializeResult) {
      onProtocolNegotiated(result.protocolVersion)
    }
    inboundHandler.get()?.invoke(message)
      ?: error("MCP transport has no inbound message handler")
  }

  protected open fun onProtocolNegotiated(version: String) = Unit

  protected fun dispatchAsync(message: JSONRPCMessage) {
    scope.launch {
      try {
        dispatch(message)
      } catch (error: Throwable) {
        reportException(error)
      }
    }
  }

  protected fun reportException(error: Throwable) {
    errorHandler.get().invoke(error)
  }

  protected fun notifyClosed() {
    closeHandler.get().invoke()
  }

  protected fun deserialize(data: String): JSONRPCMessage =
    try {
      McpJson.decodeFromString(data)
    } catch (error: Exception) {
      throw IOException("Failed to deserialize MCP JSON-RPC message", error)
    }

  protected fun serialize(message: JSONRPCMessage): String =
    try {
      McpJson.encodeToString(message)
    } catch (error: Exception) {
      throw IOException("Failed to serialize MCP JSON-RPC message", error)
    }
}

/** Android-compatible stdio transport that avoids the unavailable `Process.onExit()` API. */
internal class AndroidStdioClientTransport(
  private val parameters: StdioServerParameters,
) : BaseAndroidMcpTransport() {
  private val closing = AtomicBoolean(false)
  private val executor: ExecutorService = Executors.newFixedThreadPool(2)
  private val process = AtomicReference<Process>()

  override suspend fun start() =
    withContext(Dispatchers.IO) {
      require(parameters.command.isNotBlank()) { "Stdio server command must not be blank." }
      val child =
        ProcessBuilder(listOf(parameters.command) + parameters.args)
          .apply { environment().putAll(parameters.env) }
          .start()
      process.set(child)
      executor.execute { readMessages(child) }
      executor.execute { drainErrors(child) }
    }

  override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) =
    withContext(Dispatchers.IO) {
      check(!closing.get()) { "MCP stdio transport is closed" }
      val output = process.get()?.outputStream ?: error("MCP stdio process has not started")
      val json = serialize(message).replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
      synchronized(output) {
        output.write(json.toByteArray(StandardCharsets.UTF_8))
        output.write('\n'.code)
        output.flush()
      }
    }

  private fun readMessages(child: Process) {
    try {
      child.inputStream.bufferedReader().useLines { lines ->
        lines.takeWhile { !closing.get() }.forEach { line ->
          if (line.isNotBlank()) dispatchAsync(deserialize(line))
        }
      }
    } catch (error: Exception) {
      if (!closing.get()) reportException(error)
    }
  }

  private fun drainErrors(child: Process) {
    try {
      child.errorStream.bufferedReader().useLines { lines ->
        lines.takeWhile { !closing.get() }.forEach { /* stderr must not enter JSON-RPC stdout. */ }
      }
    } catch (error: Exception) {
      if (!closing.get()) reportException(error)
    }
  }

  override suspend fun close() {
    if (!closing.compareAndSet(false, true)) return
    val child = process.getAndSet(null)
    closeQuietly(child?.outputStream)
    closeQuietly(child?.inputStream)
    closeQuietly(child?.errorStream)
    child?.destroy()
    executor.shutdownNow()
    scope.cancel()
    notifyClosed()
  }

  private fun closeQuietly(stream: AutoCloseable?) {
    try {
      stream?.close()
    } catch (_: Exception) {
      // Best-effort shutdown; destroying the process is the final cleanup boundary.
    }
  }
}

/** Deprecated HTTP+SSE MCP transport implemented with OkHttp for Android. */
internal class OkHttpSseClientTransport(
  baseUrl: String,
  sseEndpoint: String,
  private val headers: Map<String, String>,
  private val client: OkHttpClient,
) : BaseAndroidMcpTransport() {
  private val sseUrl = appendEndpoint(baseUrl.toHttpUrl(), sseEndpoint)
  private val messageEndpoint = AtomicReference<HttpUrl>()
  private val streamCall = AtomicReference<Call>()
  private val closing = AtomicBoolean(false)

  override suspend fun start() {
    val endpointReady = CompletableDeferred<Unit>()
    scope.launch {
      val request =
        requestBuilder(sseUrl)
          .header(HEADER_ACCEPT, "text/event-stream")
          .header(HEADER_CACHE_CONTROL, "no-cache")
          .header(HEADER_PROTOCOL_VERSION, PROTOCOL_2024_11_05)
          .get()
          .build()
      val call = client.newCall(request)
      streamCall.set(call)
      try {
        call.execute().use { response ->
          check(response.isSuccessful) { "SSE connection failed with HTTP ${response.code}" }
          val body = checkNotNull(response.body) { "SSE connection returned an empty body" }
          SseEventParser.parse(body.charStream()).forEach { event ->
            when (event.event) {
              "endpoint" -> {
                val endpoint = resolveUrl(sseUrl, event.data)
                requireSameOrigin(sseUrl, endpoint)
                messageEndpoint.set(endpoint)
                endpointReady.complete(Unit)
              }
              "message" -> if (event.data.isNotBlank()) dispatch(deserialize(event.data))
            }
          }
          if (!endpointReady.isCompleted && !closing.get()) {
            error("SSE stream ended before endpoint discovery")
          }
        }
      } catch (error: Throwable) {
        if (!closing.get()) {
          endpointReady.completeExceptionally(error)
          reportException(error)
        }
      }
    }
    endpointReady.await()
  }

  override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) =
    withContext(Dispatchers.IO) {
      val endpoint = checkNotNull(messageEndpoint.get()) { "SSE message endpoint has not been discovered" }
      val request =
        requestBuilder(endpoint)
          .header(HEADER_CONTENT_TYPE, JSON_MEDIA_TYPE.toString())
          .header(HEADER_PROTOCOL_VERSION, PROTOCOL_2024_11_05)
          .post(serialize(message).toRequestBody(JSON_MEDIA_TYPE))
          .build()
      client.newCall(request).execute().use { response ->
        check(response.code in setOf(200, 201, 202, 206)) {
          "MCP request failed with HTTP ${response.code}"
        }
      }
    }

  override suspend fun close() {
    if (!closing.compareAndSet(false, true)) return
    streamCall.getAndSet(null)?.cancel()
    scope.cancel()
    notifyClosed()
  }

  private fun requestBuilder(url: HttpUrl): Request.Builder =
    Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }
}

/** MCP Streamable HTTP transport implemented with OkHttp and resumable SSE reads. */
internal class OkHttpStreamableHttpTransport(
  url: String,
  private val headers: Map<String, String>,
  private val client: OkHttpClient,
) : BaseAndroidMcpTransport() {
  private val endpoint = url.toHttpUrl()
  private val sessionId = AtomicReference<String>()
  private val lastEventId = AtomicReference<String>()
  private val listenerCall = AtomicReference<Call>()
  private val closing = AtomicBoolean(false)
  private val listenerStarted = AtomicBoolean(false)
  private val activeProtocolVersion = AtomicReference(PROTOCOL_2025_11_25)

  override suspend fun start() = Unit

  override fun onProtocolNegotiated(version: String) {
    activeProtocolVersion.set(version)
  }

  override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) =
    withContext(Dispatchers.IO) {
      check(!closing.get()) { "MCP Streamable HTTP transport is closed" }
      val protocolVersion = activeProtocolVersion.get()
      val request =
        requestBuilder(protocolVersion)
          .header(HEADER_ACCEPT, "application/json, text/event-stream")
          .header(HEADER_CONTENT_TYPE, JSON_MEDIA_TYPE.toString())
          .post(serialize(message).toRequestBody(JSON_MEDIA_TYPE))
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          if (response.code == 400 || response.code == 404) sessionId.set(null)
          error("MCP request failed with HTTP ${response.code}")
        }
        response.header(HEADER_SESSION_ID)?.let { received ->
          if (sessionId.compareAndSet(null, received)) openListener(protocolVersion)
        }
        if (response.code == 202) return@use
        val body = response.body ?: return@use
        when {
          response.header(HEADER_CONTENT_TYPE).orEmpty().contains("text/event-stream", ignoreCase = true) ->
            consumeSse(body.charStream())
          response.header(HEADER_CONTENT_TYPE).orEmpty().contains("application/json", ignoreCase = true) -> {
            val payload = body.string()
            if (payload.isNotBlank()) dispatch(deserialize(payload))
          }
          body.contentLength() != 0L -> error("Unsupported MCP response media type")
        }
      }
    }

  private fun openListener(protocolVersion: String) {
    if (!listenerStarted.compareAndSet(false, true) || closing.get()) return
    scope.launch {
      val request =
        requestBuilder(protocolVersion)
          .apply { lastEventId.get()?.let { header(HEADER_LAST_EVENT_ID, it) } }
          .header(HEADER_ACCEPT, "text/event-stream")
          .get()
          .build()
      val call = client.newCall(request)
      listenerCall.set(call)
      try {
        call.execute().use { response ->
          if (response.code == 405) return@use
          check(response.isSuccessful) { "MCP listener failed with HTTP ${response.code}" }
          response.body?.charStream()?.let { consumeSse(it) }
        }
      } catch (error: Throwable) {
        if (!closing.get()) reportException(error)
      } finally {
        listenerStarted.set(false)
      }
    }
  }

  private suspend fun consumeSse(reader: java.io.Reader) {
    SseEventParser.parse(reader).forEach { event ->
      event.id?.let(lastEventId::set)
      if ((event.event == null || event.event == "message") && event.data.isNotBlank()) {
        dispatch(deserialize(event.data))
      }
    }
  }

  private fun requestBuilder(protocolVersion: String): Request.Builder =
    Request.Builder()
      .url(endpoint)
      .apply { headers.forEach { (name, value) -> header(name, value) } }
      .header(HEADER_CACHE_CONTROL, "no-cache")
      .header(HEADER_PROTOCOL_VERSION, protocolVersion)
      .apply { sessionId.get()?.let { header(HEADER_SESSION_ID, it) } }

  override suspend fun close() =
    withContext(Dispatchers.IO) {
      if (!closing.compareAndSet(false, true)) return@withContext
      listenerCall.getAndSet(null)?.cancel()
      sessionId.getAndSet(null)?.let { currentSession ->
        val request =
          Request.Builder()
            .url(endpoint)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header(HEADER_SESSION_ID, currentSession)
            .header(HEADER_PROTOCOL_VERSION, activeProtocolVersion.get())
            .delete()
            .build()
        try {
          client.newCall(request).execute().close()
        } catch (_: IOException) {
          // Session deletion is best-effort during shutdown.
        }
      }
      scope.cancel()
      notifyClosed()
    }
}

private const val PROTOCOL_2024_11_05 = "2024-11-05"
private const val PROTOCOL_2025_11_25 = "2025-11-25"
private const val HEADER_ACCEPT = "Accept"
private const val HEADER_CACHE_CONTROL = "Cache-Control"
private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_LAST_EVENT_ID = "Last-Event-ID"
private const val HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"
private const val HEADER_SESSION_ID = "Mcp-Session-Id"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private fun resolveUrl(base: HttpUrl, endpoint: String): HttpUrl =
  if (endpoint.isBlank()) base
  else base.resolve(endpoint) ?: throw IllegalArgumentException("Invalid MCP endpoint: $endpoint")

/** Legacy SSE treats its configured endpoint as a suffix, not as an RFC relative replacement. */
private fun appendEndpoint(base: HttpUrl, endpoint: String): HttpUrl =
  if (endpoint.isBlank()) {
    base
  } else {
    base.newBuilder().encodedPath("${base.encodedPath.trimEnd('/')}/${endpoint.trimStart('/')}").build()
  }

private fun requireSameOrigin(expected: HttpUrl, actual: HttpUrl) {
  require(expected.scheme == actual.scheme && expected.host == actual.host && expected.port == actual.port) {
    "MCP SSE message endpoint must use the same origin as the SSE connection"
  }
}
