package github.ponyhuang.gimi.data.agent.tools.mcp

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Creates MCP transports that rely only on Android and OkHttp runtime APIs. */
internal class DefaultMcpTransportBuilder : McpTransportBuilder {
  override fun build(connectionParams: McpConnectionParameters): McpTransportHandle =
    when (connectionParams) {
      is McpConnectionParameters.Stdio ->
        McpTransportHandle(AndroidStdioClientTransport(connectionParams.serverParameters))
      is McpConnectionParameters.Sse ->
        httpClient(
            connectionParams.timeout.inWholeMilliseconds,
            connectionParams.sseReadTimeout.inWholeMilliseconds,
          )
          .let { client ->
            McpTransportHandle(
              transport =
                OkHttpSseClientTransport(
                  baseUrl = connectionParams.url,
                  sseEndpoint = connectionParams.sseEndpoint,
                  headers = connectionParams.headers,
                  client = client,
                ),
              release = { shutdown(client) },
            )
          }
      is McpConnectionParameters.StreamableHttp ->
        httpClient(
            connectionParams.timeout.inWholeMilliseconds,
            connectionParams.readTimeout.inWholeMilliseconds,
          )
          .let { client ->
            McpTransportHandle(
              transport =
                OkHttpStreamableHttpTransport(
                  url = connectionParams.url,
                  headers = connectionParams.headers,
                  client = client,
                ),
              release = { shutdown(client) },
            )
          }
    }

  private fun httpClient(connectTimeoutMillis: Long, readTimeoutMillis: Long): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
      .build()

  private fun shutdown(client: OkHttpClient) {
    client.dispatcher.cancelAll()
    client.connectionPool.evictAll()
    client.dispatcher.executorService.shutdown()
  }
}
