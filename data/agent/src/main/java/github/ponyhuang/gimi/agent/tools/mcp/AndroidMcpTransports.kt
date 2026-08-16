package github.ponyhuang.gimi.agent.tools.mcp

import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.TypeRef
import io.modelcontextprotocol.spec.HttpHeaders
import io.modelcontextprotocol.spec.McpClientTransport
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage
import io.modelcontextprotocol.spec.McpTransportException
import io.modelcontextprotocol.spec.ProtocolVersions
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Function
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import reactor.core.publisher.Mono

/** MCP transport 共用的消息分发、异常上报与 JSON 映射逻辑。 */
internal abstract class BaseAndroidMcpTransport(
  protected val jsonMapper: McpJsonMapper,
) : McpClientTransport {
  protected val inboundHandler = AtomicReference<Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>>()
  private val exceptionHandler = AtomicReference<Consumer<Throwable>>()

  override fun setExceptionHandler(handler: Consumer<Throwable>) {
    exceptionHandler.set(handler)
  }

  protected fun registerHandler(
    handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>,
  ) {
    inboundHandler.set(handler)
  }

  protected fun dispatch(message: JSONRPCMessage) {
    val handler = inboundHandler.get() ?: throw IllegalStateException("MCP transport is not connected")
    handler.apply(Mono.just(message)).subscribe({}, ::reportException)
  }

  protected fun reportException(error: Throwable) {
    exceptionHandler.get()?.accept(error)
  }

  protected fun deserialize(data: String): JSONRPCMessage =
    try {
      McpSchema.deserializeJsonRpcMessage(jsonMapper, data)
    } catch (e: IOException) {
      throw McpTransportException("Failed to deserialize JSON-RPC message", e)
    }

  protected fun serialize(message: JSONRPCMessage): String =
    try {
      jsonMapper.writeValueAsString(message)
    } catch (e: IOException) {
      throw McpTransportException("Failed to serialize JSON-RPC message", e)
    }

  override fun <T : Any?> unmarshalFrom(data: Any, typeRef: TypeRef<T>): T =
    jsonMapper.convertValue(data, typeRef)
}

/** Android-compatible stdio transport that avoids the unavailable `Process.onExit()` API. */
internal class AndroidStdioClientTransport(
  private val parameters: StdioServerParameters,
  jsonMapper: McpJsonMapper,
) : BaseAndroidMcpTransport(jsonMapper) {
  private val closing = AtomicBoolean(false)
  private val executor: ExecutorService = Executors.newFixedThreadPool(2)
  private val process = AtomicReference<Process>()

  override fun connect(
    handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>,
  ): Mono<Void> =
    Mono.fromRunnable {
      require(parameters.command.isNotBlank()) { "Stdio server command must not be blank." }
      registerHandler(handler)
      val child =
        ProcessBuilder(listOf(parameters.command) + parameters.args)
          .apply { environment().putAll(parameters.env) }
          .start()
      process.set(child)
      executor.execute { readMessages(child) }
      executor.execute { drainErrors(child) }
    }

  override fun sendMessage(message: JSONRPCMessage): Mono<Void> =
    Mono.fromRunnable {
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
          if (line.isNotBlank()) dispatch(deserialize(line))
        }
      }
    } catch (e: Exception) {
      if (!closing.get()) reportException(e)
    }
  }

  private fun drainErrors(child: Process) {
    try {
      child.errorStream.bufferedReader().useLines { lines ->
        lines.takeWhile { !closing.get() }.forEach { /* stderr must never enter JSON-RPC stdout. */ }
      }
    } catch (e: Exception) {
      if (!closing.get()) reportException(e)
    }
  }

  override fun closeGracefully(): Mono<Void> =
    Mono.fromRunnable {
      if (!closing.compareAndSet(false, true)) return@fromRunnable
      val child = process.getAndSet(null)
      closeQuietly(child?.outputStream)
      closeQuietly(child?.inputStream)
      closeQuietly(child?.errorStream)
      child?.destroy()
      executor.shutdownNow()
    }

  private fun closeQuietly(stream: AutoCloseable?) {
    try {
      stream?.close()
    } catch (_: Exception) {
      // Best-effort shutdown: process destruction below is the final cleanup boundary.
    }
  }
}

/** Deprecated HTTP+SSE MCP transport implemented with OkHttp for Android. */
internal class OkHttpSseClientTransport(
  baseUrl: String,
  sseEndpoint: String,
  private val headers: Map<String, String>,
  private val client: OkHttpClient,
  jsonMapper: McpJsonMapper,
) : BaseAndroidMcpTransport(jsonMapper) {
  private val base = baseUrl.toHttpUrl()
  private val sseUrl = appendEndpoint(base, sseEndpoint)
  private val messageEndpoint = AtomicReference<HttpUrl>()
  private val streamCall = AtomicReference<Call>()
  private val closing = AtomicBoolean(false)

  override fun protocolVersions(): List<String> = listOf(ProtocolVersions.MCP_2024_11_05)

  override fun connect(
    handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>,
  ): Mono<Void> =
    Mono.create { sink ->
      registerHandler(handler)
      val request =
        requestBuilder(sseUrl)
          .header(HttpHeaders.ACCEPT, "text/event-stream")
          .header(HttpHeaders.CACHE_CONTROL, "no-cache")
          .header(HttpHeaders.PROTOCOL_VERSION, ProtocolVersions.MCP_2024_11_05)
          .get()
          .build()
      val call = client.newCall(request)
      streamCall.set(call)
      sink.onCancel { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (!closing.get()) {
              sink.error(e)
              reportException(e)
            }
          }

          override fun onResponse(call: Call, response: Response) {
            response.use {
              if (!response.isSuccessful) {
                sink.error(McpTransportException("SSE connection failed with HTTP ${response.code}"))
                return
              }
              val body = response.body ?: run {
                sink.error(McpTransportException("SSE connection returned an empty body"))
                return
              }
              try {
                SseEventParser.parse(body.charStream()).forEach { event ->
                  when (event.event) {
                    "endpoint" -> {
                      val endpoint = resolveUrl(sseUrl, event.data)
                      requireSameOrigin(sseUrl, endpoint)
                      messageEndpoint.set(endpoint)
                      sink.success()
                    }
                    "message" -> if (event.data.isNotBlank()) dispatch(deserialize(event.data))
                  }
                }
                if (messageEndpoint.get() == null && !closing.get()) {
                  sink.error(McpTransportException("SSE stream ended before endpoint discovery"))
                }
              } catch (e: Exception) {
                if (!closing.get()) {
                  sink.error(e)
                  reportException(e)
                }
              }
            }
          }
        }
      )
    }

  override fun sendMessage(message: JSONRPCMessage): Mono<Void> =
    Mono.create { sink ->
      val endpoint = messageEndpoint.get() ?: run {
        sink.error(IllegalStateException("SSE message endpoint has not been discovered"))
        return@create
      }
      val request =
        requestBuilder(endpoint)
          .header(HttpHeaders.CONTENT_TYPE, JSON_MEDIA_TYPE.toString())
          .header(HttpHeaders.PROTOCOL_VERSION, ProtocolVersions.MCP_2024_11_05)
          .post(serialize(message).toRequestBody(JSON_MEDIA_TYPE))
          .build()
      val call = client.newCall(request)
      sink.onCancel { call.cancel() }
      call.enqueue(simpleCompletionCallback(sink, setOf(200, 201, 202, 206)))
    }

  override fun closeGracefully(): Mono<Void> =
    Mono.fromRunnable {
      if (!closing.compareAndSet(false, true)) return@fromRunnable
      streamCall.getAndSet(null)?.cancel()
      shutdown(client)
    }

  private fun requestBuilder(url: HttpUrl): Request.Builder =
    Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }
}

/** MCP Streamable HTTP transport implemented with OkHttp and resumable SSE reads. */
internal class OkHttpStreamableHttpTransport(
  url: String,
  private val headers: Map<String, String>,
  private val client: OkHttpClient,
  jsonMapper: McpJsonMapper,
) : BaseAndroidMcpTransport(jsonMapper) {
  private val endpoint = url.toHttpUrl()
  private val sessionId = AtomicReference<String>()
  private val lastEventId = AtomicReference<String>()
  private val listenerCall = AtomicReference<Call>()
  private val closing = AtomicBoolean(false)
  private val listenerStarted = AtomicBoolean(false)
  private val activeProtocolVersion = AtomicReference(ProtocolVersions.MCP_2025_11_25)

  override fun protocolVersions(): List<String> =
    listOf(
      ProtocolVersions.MCP_2025_11_25,
      ProtocolVersions.MCP_2025_06_18,
      ProtocolVersions.MCP_2025_03_26,
      ProtocolVersions.MCP_2024_11_05,
    )

  override fun connect(
    handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>,
  ): Mono<Void> = Mono.fromRunnable { registerHandler(handler) }

  override fun sendMessage(message: JSONRPCMessage): Mono<Void> =
    Mono.deferContextual { context ->
      val protocolVersion =
        context.getOrDefault(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, ProtocolVersions.MCP_2025_11_25)
          ?: ProtocolVersions.MCP_2025_11_25
      activeProtocolVersion.set(protocolVersion)
      Mono.create { sink ->
        if (closing.get()) {
          sink.error(IllegalStateException("MCP Streamable HTTP transport is closed"))
          return@create
        }
        val request =
          requestBuilder(protocolVersion)
            .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
            .header(HttpHeaders.CONTENT_TYPE, JSON_MEDIA_TYPE.toString())
            .post(serialize(message).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        sink.onCancel { call.cancel() }
        call.enqueue(
          object : Callback {
            override fun onFailure(call: Call, e: IOException) {
              sink.error(e)
              reportException(e)
            }

            override fun onResponse(call: Call, response: Response) {
              response.use {
                if (!response.isSuccessful) {
                  val error = McpTransportException("MCP request failed with HTTP ${response.code}")
                  if (response.code == 400 || response.code == 404) sessionId.set(null)
                  sink.error(error)
                  reportException(error)
                  return
                }
                response.header(HttpHeaders.MCP_SESSION_ID)?.let { received ->
                  if (sessionId.compareAndSet(null, received)) openListener(protocolVersion)
                }
                if (response.code == 202) {
                  sink.success()
                  return
                }
                val body = response.body
                if (body == null || body.contentLength() == 0L) {
                  sink.success()
                  return
                }
                try {
                  when {
                    response.header(HttpHeaders.CONTENT_TYPE).orEmpty().contains("text/event-stream", ignoreCase = true) -> {
                      sink.success()
                      consumeSse(body.charStream())
                    }
                    response.header(HttpHeaders.CONTENT_TYPE).orEmpty().contains("application/json", ignoreCase = true) -> {
                      dispatch(deserialize(body.string()))
                      sink.success()
                    }
                    else -> sink.error(McpTransportException("Unsupported MCP response media type"))
                  }
                } catch (e: Exception) {
                  sink.error(e)
                  reportException(e)
                }
              }
            }
          }
        )
      }
    }

  private fun openListener(protocolVersion: String) {
    if (!listenerStarted.compareAndSet(false, true) || closing.get()) return
    val request =
      requestBuilder(protocolVersion)
        .apply { lastEventId.get()?.let { header(HttpHeaders.LAST_EVENT_ID, it) } }
        .header(HttpHeaders.ACCEPT, "text/event-stream")
        .get()
        .build()
    val call = client.newCall(request)
    listenerCall.set(call)
    call.enqueue(
      object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          listenerStarted.set(false)
          if (!closing.get()) reportException(e)
        }

        override fun onResponse(call: Call, response: Response) {
          response.use {
            if (response.code == 405) return
            if (!response.isSuccessful) {
              reportException(McpTransportException("MCP listener failed with HTTP ${response.code}"))
              return
            }
            try {
              response.body?.charStream()?.let(::consumeSse)
            } catch (e: Exception) {
              if (!closing.get()) reportException(e)
            } finally {
              listenerStarted.set(false)
            }
          }
        }
      }
    )
  }

  private fun consumeSse(reader: java.io.Reader) {
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
      .header(HttpHeaders.CACHE_CONTROL, "no-cache")
      .header(HttpHeaders.PROTOCOL_VERSION, protocolVersion)
      .apply { sessionId.get()?.let { header(HttpHeaders.MCP_SESSION_ID, it) } }

  override fun closeGracefully(): Mono<Void> =
    Mono.create { sink ->
      if (!closing.compareAndSet(false, true)) {
        sink.success()
        return@create
      }
      listenerCall.getAndSet(null)?.cancel()
      val currentSession = sessionId.getAndSet(null)
      if (currentSession == null) {
        shutdown(client)
        sink.success()
        return@create
      }
      val request =
        Request.Builder()
          .url(endpoint)
          .apply { headers.forEach { (name, value) -> header(name, value) } }
          .header(HttpHeaders.MCP_SESSION_ID, currentSession)
          .header(HttpHeaders.PROTOCOL_VERSION, activeProtocolVersion.get())
          .delete()
          .build()
      client.newCall(request).enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            shutdown(client)
            sink.success()
          }

          override fun onResponse(call: Call, response: Response) {
            response.close()
            shutdown(client)
            sink.success()
          }
        }
      )
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private fun resolveUrl(base: HttpUrl, endpoint: String): HttpUrl =
  if (endpoint.isBlank()) base else base.resolve(endpoint) ?: throw IllegalArgumentException("Invalid MCP endpoint: $endpoint")

/** Legacy SSE treats its configured endpoint as a suffix, not as an RFC relative replacement. */
private fun appendEndpoint(base: HttpUrl, endpoint: String): HttpUrl =
  if (endpoint.isBlank()) {
    base
  } else {
    base
      .newBuilder()
      .encodedPath("${base.encodedPath.trimEnd('/')}/${endpoint.trimStart('/')}")
      .build()
  }

private fun requireSameOrigin(expected: HttpUrl, actual: HttpUrl) {
  require(expected.scheme == actual.scheme && expected.host == actual.host && expected.port == actual.port) {
    "MCP SSE message endpoint must use the same origin as the SSE connection"
  }
}

private fun simpleCompletionCallback(
  sink: reactor.core.publisher.MonoSink<Void>,
  acceptedCodes: Set<Int>,
): Callback =
  object : Callback {
    override fun onFailure(call: Call, e: IOException) {
      sink.error(e)
    }

    override fun onResponse(call: Call, response: Response) {
      response.use {
        if (response.code in acceptedCodes) sink.success()
        else sink.error(McpTransportException("MCP request failed with HTTP ${response.code}"))
      }
    }
  }

private fun shutdown(client: OkHttpClient) {
  client.dispatcher.cancelAll()
  client.connectionPool.evictAll()
  client.dispatcher.executorService.shutdown()
}
