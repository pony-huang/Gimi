package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Android MCP session pool backed by the official Kotlin SDK client. */
internal class McpSessionManager(
  private val connectionParams: McpConnectionParameters,
  private val transportBuilder: McpTransportBuilder = DefaultMcpTransportBuilder(),
  private val progressConsumers: List<(Progress) -> Unit> = emptyList(),
  private val sessionOpener: (suspend (Map<String, String>) -> McpSession)? = null,
) : SessionManager {
  override val hasProgressConsumers: Boolean
    get() = progressConsumers.isNotEmpty()

  override fun requestOptions(): RequestOptions =
    RequestOptions(
      onProgress =
        if (progressConsumers.isEmpty()) null
        else ({ progress -> progressConsumers.forEach { it(progress) } }),
      timeout = requestTimeout(connectionParams),
    )

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
    return createSession(params, transportBuilder)
  }

  private suspend fun McpSession.initialize() {
    try {
      withTimeout(initializationTimeout(connectionParams)) { connect() }
      logger.debug { "Initialized pooled MCP session: ${client.serverVersion}" }
    } catch (error: Exception) {
      close()
      throw error
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
    ): McpSession {
      val handle = transportBuilder.build(connectionParams)
      val client =
        Client(
          clientInfo = Implementation(name = CLIENT_NAME, version = CLIENT_VERSION),
          options = ClientOptions(capabilities = ClientCapabilities()),
        )
      client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) {
        notification ->
        val data = notification.params.data
        when (notification.params.level) {
          LoggingLevel.Debug -> logger.debug { data.toString() }
          LoggingLevel.Info,
          LoggingLevel.Notice -> logger.info { data.toString() }
          LoggingLevel.Warning -> logger.warn { data.toString() }
          LoggingLevel.Error,
          LoggingLevel.Critical,
          LoggingLevel.Alert,
          LoggingLevel.Emergency -> logger.error { data.toString() }
        }
        CompletableDeferred(Unit)
      }
      return McpSession(client, handle)
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
  }
}
