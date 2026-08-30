package github.ponyhuang.gimi.data.agent.tools.mcp

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KotlinSdkAndroidRuntimeTest {
    @Test
    fun initializesAndClosesKotlinSdkClientOnAndroidRuntime() = runBlocking {
        val transport = InMemoryTransport()
        val client =
            Client(
                clientInfo = Implementation(name = "android-test", version = "1"),
                options = ClientOptions(capabilities = ClientCapabilities()),
            )

        client.connect(transport)
        client.close()

        assertEquals("android-test-server", client.serverVersion?.name)
        assertTrue(transport.closed.get())
    }

    private class InMemoryTransport : Transport {
        private lateinit var messageHandler: suspend (JSONRPCMessage) -> Unit
        private var closeHandler: () -> Unit = {}
        val closed = AtomicBoolean(false)

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message is JSONRPCNotification) return
            require(message is JSONRPCRequest && message.method == "initialize")
            val result =
                InitializeResult(
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(name = "android-test-server", version = "1"),
                )
            messageHandler(JSONRPCResponse(message.id, result))
        }

        override suspend fun close() {
            closed.set(true)
            closeHandler()
        }

        override fun onClose(block: () -> Unit) {
            closeHandler = block
        }

        override fun onError(block: (Throwable) -> Unit) = Unit

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
            messageHandler = block
        }
    }
}
