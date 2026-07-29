package github.ponyhuang.asssistantai.agent.tools.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlin.time.Duration
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * The default builder for creating MCP client transports. Supports [StdioClientTransport] based on
 * [McpConnectionParameters.Stdio], [SseClientTransport] based on [McpConnectionParameters.Sse], and
 * [StreamableHttpClientTransport] based on [McpConnectionParameters.StreamableHttp].
 *
 * Each build allocates the resources the transport needs -- a child process for stdio, a Ktor
 * `HttpClient` for the HTTP transports -- and returns them alongside the transport in an
 * [McpTransportHandle] so the session owning the transport can dispose of them.
 */
internal class DefaultMcpTransportBuilder : McpTransportBuilder {

  override fun build(connectionParams: McpConnectionParameters): McpTransportHandle =
    when (connectionParams) {
      is McpConnectionParameters.Stdio -> buildStdio(connectionParams)
      is McpConnectionParameters.Sse -> buildSse(connectionParams)
      is McpConnectionParameters.StreamableHttp -> buildStreamableHttp(connectionParams)
    }

  private fun buildStdio(params: McpConnectionParameters.Stdio): McpTransportHandle {
    val serverParameters = params.serverParameters
    require(serverParameters.command.isNotBlank()) { "Stdio server command must not be blank." }

    val process =
      ProcessBuilder(listOf(serverParameters.command) + serverParameters.args)
        .apply { environment().putAll(serverParameters.env) }
        .start()

    val transport =
      StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered(),
        error = process.errorStream.asSource().buffered(),
      )
    return McpTransportHandle(transport) { process.destroy() }
  }

  private fun buildSse(params: McpConnectionParameters.Sse): McpTransportHandle {
    // The SSE stream is long-lived, so only the connect and per-read socket timeouts are bounded;
    // a request timeout would cut the stream off mid-session.
    val httpClient = httpClient(connectTimeout = params.timeout, socketTimeout = params.sseReadTimeout)
    val transport =
      SseClientTransport(
        client = httpClient,
        urlString = params.url.resolveSseEndpoint(params.sseEndpoint),
        requestBuilder = params.headers.asRequestBuilder(),
      )
    return McpTransportHandle(transport) { httpClient.close() }
  }

  private fun buildStreamableHttp(
    params: McpConnectionParameters.StreamableHttp
  ): McpTransportHandle {
    val httpClient = httpClient(connectTimeout = params.timeout, socketTimeout = params.readTimeout)
    val transport =
      StreamableHttpClientTransport(
        client = httpClient,
        url = params.url,
        requestBuilder = params.headers.asRequestBuilder(),
      )
    return McpTransportHandle(transport) { httpClient.close() }
  }

  private companion object {
    /** The MCP HTTP transports require an SSE-capable client, which callers must supply. */
    fun httpClient(connectTimeout: Duration, socketTimeout: Duration): HttpClient =
      HttpClient(OkHttp) {
        install(SSE)
        install(HttpTimeout) {
          connectTimeoutMillis = connectTimeout.inWholeMilliseconds
          socketTimeoutMillis = socketTimeout.inWholeMilliseconds
        }
      }

    /**
     * Appends [sseEndpoint] to this base URL. The Java SDK took the two separately; the Kotlin SDK
     * takes a single URL.
     */
    fun String.resolveSseEndpoint(sseEndpoint: String): String =
      if (sseEndpoint.isBlank()) this else "${trimEnd('/')}/${sseEndpoint.trimStart('/')}"

    fun Map<String, String>.asRequestBuilder(): HttpRequestBuilder.() -> Unit = {
      this@asRequestBuilder.forEach { (key, value) -> header(key, value) }
    }
  }
}
