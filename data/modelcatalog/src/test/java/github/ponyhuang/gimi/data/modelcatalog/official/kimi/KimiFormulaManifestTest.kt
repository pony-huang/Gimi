package github.ponyhuang.gimi.data.modelcatalog.official.kimi

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 动态目录的原始 schema、去重、凭据传递和失败重试边界。 */
class KimiFormulaManifestTest {
    @Test
    fun keepsRawSchemaAndFirstFormulaUriWhenNamesRepeat() = runTest {
        val credentials = java.util.Collections.synchronizedList(mutableListOf<String?>())
        val manifest = KimiFormulaManifest("account-key", client { request ->
            credentials += request.header("Authorization")
            200 to BODY
        })
        val declarations = manifest.fetch()
        val declaration = declarations.single()
        assertEquals("translate", declaration.name)
        assertEquals(KimiFormulaManifest.FORMULA_URIS.first(), declaration.formulaUri)
        assertEquals("object", declaration.parameters?.get("type")?.jsonPrimitive?.content)
        assertEquals("string", declaration.parameters?.get("properties")?.jsonObject
            ?.get("text")?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals(KimiFormulaManifest.FORMULA_URIS.size, credentials.size)
        assertTrue(credentials.all { it == "Bearer account-key" })
    }

    @Test
    fun failedEndpointsCanRecoverWithoutWaitingForCacheExpiry() = runTest {
        val calls = AtomicInteger()
        var failing = true
        val cache = KimiFormulaCache(client {
            calls.incrementAndGet()
            if (failing) 503 to "{}" else 200 to BODY
        })
        assertTrue(cache.fetch("kimi", "key").isEmpty())
        failing = false
        assertEquals(listOf("translate"), cache.fetch("kimi", "key").map { it.name })
        assertEquals(2 * KimiFormulaManifest.FORMULA_URIS.size, calls.get())
    }

    @Test
    fun partialFailureKeepsSuccessfulDeclarations() = runTest {
        val manifest = KimiFormulaManifest("key", client { request ->
            if (request.url.encodedPath.contains("moonshot/convert")) 200 to BODY else 503 to "{}"
        })
        assertEquals(listOf("translate"), manifest.fetch().map { it.name })
    }

    private fun client(response: (okhttp3.Request) -> Pair<Int, String>) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val (code, body) = response(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(body.toResponseBody()).build()
        }.build()

    private companion object {
        const val BODY = """{"tools":[{"function":{"name":"translate","description":"Translate text","parameters":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}}]}"""
    }
}
