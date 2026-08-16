package github.ponyhuang.gimi.agent.tools.mcp

import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.spec.McpClientTransport
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Creates MCP transports that rely only on Android and OkHttp runtime APIs. */
internal class DefaultMcpTransportBuilder : McpTransportBuilder {
  override fun build(connectionParams: McpConnectionParameters): McpClientTransport =
    when (connectionParams) {
      is McpConnectionParameters.Stdio ->
        AndroidStdioClientTransport(connectionParams.serverParameters, McpJsonDefaults.getMapper())
      is McpConnectionParameters.Sse ->
        OkHttpSseClientTransport(
          baseUrl = connectionParams.url,
          sseEndpoint = connectionParams.sseEndpoint,
          headers = connectionParams.headers,
          client = httpClient(connectionParams.timeout.inWholeMilliseconds, connectionParams.sseReadTimeout.inWholeMilliseconds),
          jsonMapper = McpJsonDefaults.getMapper(),
        )
      is McpConnectionParameters.StreamableHttp ->
        OkHttpStreamableHttpTransport(
          url = connectionParams.url,
          headers = connectionParams.headers,
          client = httpClient(connectionParams.timeout.inWholeMilliseconds, connectionParams.readTimeout.inWholeMilliseconds),
          jsonMapper = McpJsonDefaults.getMapper(),
        )
    }

  private fun httpClient(connectTimeoutMillis: Long, readTimeoutMillis: Long): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
      .build()
}
