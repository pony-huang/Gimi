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

/**
 * Owns and manages MCP client sessions.
 *
 * Sessions are pooled by a key derived from the connection parameters and the per-call headers: a
 * stdio connection ignores headers and always maps to a single shared session, while SSE and
 * Streamable HTTP connections get one session per distinct header set. The pool is the single owner
 * of every session, so [close] can tear them all down and [getSession] replaces a dead one in place
 * (via its `stale` parameter) for everyone sharing it.
 */
internal class McpSessionManager(
  private val connectionParams: McpConnectionParameters,
  private val transportBuilder: McpTransportBuilder = DefaultMcpTransportBuilder(),
  private val progressConsumers: List<(Progress) -> Unit> = emptyList(),
  // Test seam: builds and initializes a ready-to-use session for the given headers. Defaults to the
  // real transport-backed client; unit tests inject a fake to exercise pooling without a server.
  private val sessionOpener: (suspend (Map<String, String>) -> McpSession)? = null,
) : SessionManager {

  override val hasProgressConsumers: Boolean
    get() = progressConsumers.isNotEmpty()

  override fun requestOptions(): RequestOptions =
    RequestOptions(
      onProgress =
        if (hasProgressConsumers) {
          { progress -> progressConsumers.forEach { it(progress) } }
        } else {
          null
        },
      timeout = requestTimeout(connectionParams),
    )

  /**
   * Guards [sessions] across the suspending create+initialize critical section.
   *
   * A single instance-wide lock is held for the whole of [openSession], including the network
   * `connect()` round-trip, so a session is created at most once per key even under concurrent
   * callers. This mirrors adk-python's `MCPSessionManager`, which serializes on a single
   * `asyncio.Lock`.
   *
   * Tradeoff: because the lock spans initialization, a slow [openSession] blocks every other
   * [getSession] -- even for distinct header keys -- and a concurrent [close] (which acquires this
   * lock via `runBlocking`) blocks its calling thread until the in-flight init completes. This is
   * acceptable for the expected workloads (stdio and static-header setups have a single session;
   * per-header sessions are low-concurrency). If highly concurrent per-user sessions become a
   * bottleneck, pool `Deferred<McpSession>` and `await` it outside the lock so distinct keys
   * initialize in parallel and the lock hold-time stays tiny.
   */
  private val mutex = Mutex()
  private val sessions = mutableMapOf<String, McpSession>()

  override suspend fun getSession(headers: Map<String, String>, stale: McpSession?): McpSession {
    val key = sessionKey(headers)
    // Evict-then-get-or-create, all under one lock. If a known-dead `stale` is named and still
    // pooled, drop it so we recreate below; whichever caller wins that race closes it, while other
    // sharers holding the same dead session find the replacement already in place (created once).
    val (session, evicted) =
      mutex.withLock {
        val evicted = if (stale != null && sessions[key] === stale) sessions.remove(key) else null
        val session = sessions[key] ?: openSession(headers).also { sessions[key] = it }
        session to evicted
      }
    evicted?.close() // Closed outside the lock; the stale session is already dead.
    return session
  }

  /**
   * Builds and initializes a fresh session, or delegates to the injected [sessionOpener] in tests.
   */
  private suspend fun openSession(headers: Map<String, String>): McpSession =
    sessionOpener?.invoke(headers) ?: createSession(headers).also { it.initialize() }

  // Not suspend: AutoCloseable.close() isn't. runBlocking bridges the coroutine Mutex that
  // getSession holds across a suspending connect(), and the suspending McpSession.close(). The
  // critical section is brief: snapshot + clear so an in-flight getSession can't re-pool
  // afterwards, then the session closes run outside it.
  //
  // Warning: acquiring the lock can still block this thread if a getSession is mid-initialize (it
  // holds the lock across the network round-trip); the wait is bounded by the init timeout, not a
  // deadlock. See the [mutex] doc for the coarse-lock tradeoff and how to avoid it if needed.
  override fun close() {
    runBlocking {
      val toClose = mutex.withLock { sessions.values.toList().also { sessions.clear() } }
      toClose.forEach { it.close() }
    }
  }

  /**
   * Builds (but does not connect) a session for [headers], merging them into the base params.
   *
   * Not part of [SessionManager]: callers must go through [getSession] so sessions stay pooled and
   * owned. Exposed within the module only so unit tests can assert transport/timeout configuration.
   */
  fun createSession(headers: Map<String, String> = emptyMap()): McpSession {
    val params =
      if (headers.isNotEmpty()) {
        when (connectionParams) {
          is McpConnectionParameters.Sse ->
            connectionParams.copy(headers = connectionParams.headers + headers)
          is McpConnectionParameters.StreamableHttp ->
            connectionParams.copy(headers = connectionParams.headers + headers)
          else -> connectionParams
        }
      } else {
        connectionParams
      }
    return createSession(params, transportBuilder)
  }

  /**
   * Runs the MCP initialization handshake, bounded by the connection's initialization timeout.
   *
   * The session is built (a stdio transport already spawned a child process) but not yet pooled, so
   * nothing else would close it: close it here to avoid leaking it when initialization fails.
   */
  private suspend fun McpSession.initialize() {
    try {
      withTimeout(initializationTimeout(connectionParams)) { connect() }
      logger.debug { "Initialized pooled MCP session: ${client.serverVersion}" }
    } catch (e: Exception) {
      close()
      throw e
    }
  }

  /**
   * Pool key for [headers]. Stdio ignores headers (a single shared session); SSE/Streamable HTTP
   * get one session per distinct header set.
   */
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

    /**
     * Builds an MCP client and its transport, without connecting.
     *
     * @param connectionParams The parameters for the MCP connection.
     * @param transportBuilder The builder for the MCP transport.
     * @return A session whose client still has to be connected.
     */
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
      client.setNotificationHandler<LoggingMessageNotification>(
        Method.Defined.NotificationsMessage
      ) { notification ->
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

    /** Bound on the `initialize` handshake; the Kotlin SDK's `connect` has no timeout of its own. */
    private fun initializationTimeout(connectionParams: McpConnectionParameters): Duration =
      when (connectionParams) {
        is McpConnectionParameters.Stdio -> DEFAULT_TIMEOUT
        is McpConnectionParameters.Sse -> connectionParams.timeout
        is McpConnectionParameters.StreamableHttp -> connectionParams.timeout
      }

    /** Bound on every non-handshake request made on the session. */
    private fun requestTimeout(connectionParams: McpConnectionParameters): Duration =
      when (connectionParams) {
        is McpConnectionParameters.Stdio -> connectionParams.timeoutDuration
        is McpConnectionParameters.Sse -> connectionParams.sseReadTimeout
        is McpConnectionParameters.StreamableHttp -> connectionParams.readTimeout
      }
  }
}
