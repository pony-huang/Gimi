package dev.e2b.sdk

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemplatesTest {
    private val server = MockWebServer()
    private lateinit var client: E2BClient
    @Before fun setup() {
        server.start()
        client = E2BClient(E2BConfig.builder().apiKey("private-key").apiUrl(server.url("/").toString()).build())
    }
    @After fun cleanup() { client.close(); server.shutdown() }

    @Test fun `uses v2 templates and preserves names status and pagination`() {
        server.enqueue(MockResponse().setBody("""[{"templateID":"one","names":["project/tools"],"buildStatus":"ready","future":true}]""")
            .setHeader("X-Next-Token", "next+/="))
        val first = client.listTemplates(limit = 25)
        assertEquals(listOf(TemplateInfo("one", listOf("project/tools"), "ready")), first.items)
        assertEquals("next+/=", first.nextToken)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/templates", request.requestUrl!!.encodedPath)
        assertEquals("25", request.requestUrl!!.queryParameter("limit"))
        assertEquals("private-key", request.getHeader("X-API-Key"))
        server.enqueue(MockResponse().setBody("[]"))
        assertNull(client.listTemplates(nextToken = first.nextToken).nextToken)
        assertEquals("next+/=", server.takeRequest().requestUrl!!.queryParameter("nextToken"))
    }
    @Test fun `unnamed templates and unknown status remain available`() {
        server.enqueue(MockResponse().setBody("""[{"templateID":"one","names":[],"buildStatus":"new-state"},{"templateID":"two"}]"""))
        assertEquals(listOf(TemplateInfo("one", emptyList(), "new-state"), TemplateInfo("two", emptyList(), null)), client.listTemplates().items)
    }
    @Test fun `invalid response structures are protocol failures`() {
        listOf("{}", "[{}]", """[{"templateID":12}]""", """[{"templateID":"x","names":"bad"}]""",
            """[{"templateID":"x","names":[12]}]""", """[{"templateID":"x","buildStatus":{}}]""").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            assertThrows(ProtocolException::class.java) { client.listTemplates() }
        }
    }
    @Test fun `authentication failure excludes response body`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private-key"))
        val failure = assertThrows(AuthenticationException::class.java) { client.listTemplates() }
        assertFalse(failure.toString().contains("private-key"))
    }
}
