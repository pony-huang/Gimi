package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities
import io.modelcontextprotocol.spec.McpSchema.Implementation
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import reactor.core.publisher.Mono

/** Android MCP session pool backed by the official Java SDK client. */
internal class McpSessionManager(
  private val connectionParams: McpConnectionParameters,
  private val transportBuilder: McpTransportBuilder = DefaultMcpTransportBuilder(),
  private val progressConsumers: List<(McpProgressUpdate) -> Unit> = emptyList(),
  private val sessionOpener: (suspend (Map<String, String>) -> McpSession)? = null,
) : SessionManager {
  override val hasProgressConsumers: Boolean
    get() = progressConsumers.isNotEmpty()

  private val mutex = Mutex()
  private val sessions = mutableMapOf<String, McpSession>()

  override suspend fun getSession(headers: Map<String, String>, stale: McpSession?): McpSession {
    val key = sessionKey(headers)
    val (session, evicted) =
      mutex.withLock {
        val evicted = if (stale != null && sessions[key] === stale) sessions.remove(key) else null
        val session = sessions[key] ?: openSession(headers).also { sessions[key] = it }
        session to evicted
      }
    evicted?.close()
    return session
  }

  private suspend fun openSession(headers: Map<String, String>): McpSession =
    sessionOpener?.invoke(headers) ?: createSession(headers).also { it.initialize() }

  override fun close() {
    runBlocking {
      val toClose = mutex.withLock { sessions.values.toList().also { sessions.clear() } }
      toClose.forEach { it.close() }
    }
  }

  fun createSession(headers: Map<String, String> = emptyMap()): McpSession {
    val params =
      when {
        headers.isEmpty() -> connectionParams
        connectionParams is McpConnectionParameters.Sse ->
          connectionParams.copy(headers = connectionParams.headers + headers)
        connectionParams is McpConnectionParameters.StreamableHttp ->
          connectionParams.copy(headers = connectionParams.headers + headers)
        else -> connectionParams
      }
    return createSession(params, transportBuilder, progressConsumers)
  }

  private suspend fun McpSession.initialize() {
    try {
      withTimeout(initializationTimeout(connectionParams)) { connect() }
      logger.debug { "Initialized pooled MCP session: ${client.serverInfo}" }
    } catch (e: Exception) {
      close()
      throw e
    }
  }

  private fun sessionKey(headers: Map<String, String>): String =
    when (connectionParams) {
      is McpConnectionParameters.Stdio -> STDIO_SESSION_KEY
      else -> if (headers.isEmpty()) NO_HEADERS_SESSION_KEY else headers.toSortedMap().toString()
    }

  companion object {
    private const val STDIO_SESSION_KEY = "stdio_session"
    private const val NO_HEADERS_SESSION_KEY = "session_no_headers"
    private val DEFAULT_TIMEOUT = 5.minutes
    private val logger = LoggerFactory.getLogger(McpSessionManager::class)

    fun createSession(
      connectionParams: McpConnectionParameters,
      transportBuilder: McpTransportBuilder = DefaultMcpTransportBuilder(),
      progressConsumers: List<(McpProgressUpdate) -> Unit> = emptyList(),
    ): McpSession {
      val builder =
        McpClient.async(transportBuilder.build(connectionParams))
          .clientInfo(Implementation.builder(CLIENT_NAME, CLIENT_VERSION).build())
          .capabilities(ClientCapabilities.builder().build())
          .requestTimeout(requestTimeout(connectionParams).toJavaDuration())
          .initializationTimeout(initializationTimeout(connectionParams).toJavaDuration())
          .loggingConsumer { notification ->
            val data = notification.data()
            when (notification.level()) {
              LoggingLevel.DEBUG -> logger.debug { data }
              LoggingLevel.INFO,
              LoggingLevel.NOTICE -> logger.info { data }
              LoggingLevel.WARNING -> logger.warn { data }
              LoggingLevel.ERROR,
              LoggingLevel.CRITICAL,
              LoggingLevel.ALERT,
              LoggingLevel.EMERGENCY -> logger.error { data }
              null -> logger.info { data }
            }
            Mono.empty()
          }

      if (progressConsumers.isNotEmpty()) {
        builder.progressConsumer { notification ->
          val update =
            McpProgressUpdate(
              progressToken = notification.progressToken(),
              progress = notification.progress(),
              total = notification.total(),
              message = notification.message(),
            )
          progressConsumers.forEach { it(update) }
          Mono.empty()
        }
      }
      return McpSession(builder.build())
    }

    private const val CLIENT_NAME = "asssistantai-adk"
    private const val CLIENT_VERSION = "1.0.0"

    private fun initializationTimeout(connectionParams: McpConnectionParameters): Duration =
      when (connectionParams) {
        is McpConnectionParameters.Stdio -> DEFAULT_TIMEOUT
        is McpConnectionParameters.Sse -> connectionParams.timeout
        is McpConnectionParameters.StreamableHttp -> connectionParams.timeout
      }

    private fun requestTimeout(connectionParams: McpConnectionParameters): Duration =
      when (connectionParams) {
        is McpConnectionParameters.Stdio -> connectionParams.timeoutDuration
        is McpConnectionParameters.Sse -> connectionParams.sseReadTimeout
        is McpConnectionParameters.StreamableHttp -> connectionParams.readTimeout
      }

    private fun Duration.toJavaDuration(): JavaDuration = JavaDuration.ofMillis(inWholeMilliseconds)
  }
}

/**
 * Progress notification exposed without leaking Java SDK records into callers.
 *
 * @property progressToken Correlates this update with its originating request.
 * @property progress Current progress value reported by the server.
 * @property total Optional total used to calculate completion percentage.
 * @property message Optional human-readable status supplied by the server.
 */
data class McpProgressUpdate(
  val progressToken: Any?,
  val progress: Double,
  val total: Double?,
  val message: String?,
)
