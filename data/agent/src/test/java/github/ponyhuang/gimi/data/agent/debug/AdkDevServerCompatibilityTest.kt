package github.ponyhuang.gimi.data.agent.debug

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.webserver.AdkServerConfig
import com.google.adk.kt.webserver.dev.AdkDevServer
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import io.mockk.mockk
import java.net.ServerSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

/** 用本地服务验证 AppLoader、会话和开发接口，模型不发起任何外部调用。 */
class AdkDevServerCompatibilityTest {
    @Test
    fun loaderIsLazyAndDevUiReadsTheSameSession() = runBlocking {
        var builds = 0
        val loader = AdkAgentDebugWebServer.LazySingleAgentLoader {
            builds++
            LlmAgent(name = "Assistant", model = object : Model {
                override val name = "fixture"
                override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = emptyFlow()
            })
        }
        assertEquals(listOf(AgentChatRunner.APP_NAME), loader.listApps())
        assertEquals(0, builds)
        assertNull(loader.loadApp("unknown"))
        val sessions = InMemorySessionService()
        sessions.createSession(SessionKey(AgentChatRunner.APP_NAME, "user", "saved-session"))
        val port = ServerSocket(0).use { it.localPort }
        val server = AdkDevServer(AdkServerConfig(
            appLoader = loader, sessionService = sessions, artifactService = mockk<ArtifactService>(relaxed = true),
            host = "127.0.0.1", port = port,
        ))
        val client = OkHttpClient()
        try {
            server.start()
            fun get(path: String): String = client.newCall(Request.Builder().url("http://127.0.0.1:$port$path").build()).execute().use {
                assertEquals(path, 200, it.code)
                it.body.string()
            }
            assertTrue(get("/dev-ui/").contains("<html", ignoreCase = true))
            assertTrue(get("/apps/${AgentChatRunner.APP_NAME}/users/user/sessions").contains("saved-session"))
            assertTrue(get("/dev/apps/${AgentChatRunner.APP_NAME}/build_graph").contains("Assistant"))
            get("/debug/trace/session/saved-session")
            assertEquals(1, builds)
        } finally {
            server.stop()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
