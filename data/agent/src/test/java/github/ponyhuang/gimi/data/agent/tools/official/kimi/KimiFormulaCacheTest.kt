package github.ponyhuang.gimi.data.agent.tools.official.kimi

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KimiFormulaCacheTest {

    @Test
    fun reusesManifestWithinFiveMinutes() = runTest {
        var loadCount = 0
        var nowMillis = 0L
        val cache = KimiFormulaCache(
            loader = {
                loadCount += 1
                listOf(declaration("tool_$loadCount"))
            },
            nowMillis = { nowMillis },
        )

        val first = cache.fetch(serviceId = "kimi", apiKey = "key")
        nowMillis += 5 * 60 * 1_000L - 1
        val second = cache.fetch(serviceId = "kimi", apiKey = "key")

        assertEquals(listOf("tool_1"), first.map { it.name })
        assertEquals(listOf("tool_1"), second.map { it.name })
        assertEquals(1, loadCount)
    }

    @Test
    fun reloadsManifestAfterFiveMinutes() = runTest {
        var loadCount = 0
        var nowMillis = 0L
        val cache = KimiFormulaCache(
            loader = {
                loadCount += 1
                listOf(declaration("tool_$loadCount"))
            },
            nowMillis = { nowMillis },
        )

        cache.fetch(serviceId = "kimi", apiKey = "key")
        nowMillis += 5 * 60 * 1_000L
        val refreshed = cache.fetch(serviceId = "kimi", apiKey = "key")

        assertEquals(listOf("tool_2"), refreshed.map { it.name })
        assertEquals(2, loadCount)
    }

    @Test
    fun isolatesEntriesByServiceAndCredential() = runTest {
        var loadCount = 0
        val loadedCredentials = mutableListOf<String>()
        val cache = KimiFormulaCache(
            loader = { credential ->
                loadedCredentials += credential
                loadCount += 1
                listOf(declaration("tool_$loadCount"))
            },
            nowMillis = { 0L },
        )

        val first = cache.fetch(serviceId = "service-a", apiKey = "key-a")
        val otherService = cache.fetch(serviceId = "service-b", apiKey = "key-a")
        val otherCredential = cache.fetch(serviceId = "service-a", apiKey = "key-b")
        val reused = cache.fetch(serviceId = "service-a", apiKey = "key-a")

        assertEquals(3, loadCount)
        assertEquals(listOf("key-a", "key-a", "key-b"), loadedCredentials)
        assertEquals(listOf("tool_1"), first.map { it.name })
        assertEquals(listOf("tool_2"), otherService.map { it.name })
        assertEquals(listOf("tool_3"), otherCredential.map { it.name })
        assertEquals(first, reused)
    }

    @Test
    fun failedLoadReturnsEmptyAndCanRetryImmediately() = runTest {
        var loadCount = 0
        val cache = KimiFormulaCache(
            loader = {
                loadCount += 1
                if (loadCount == 1) error("credential=https://secret.example")
                listOf(declaration("recovered"))
            },
            nowMillis = { 0L },
        )

        assertEquals(
            emptyList<FormulaDeclaration>(),
            cache.fetch(serviceId = "kimi", apiKey = "key"),
        )
        assertEquals(
            listOf("recovered"),
            cache.fetch(serviceId = "kimi", apiKey = "key").map { it.name },
        )
        assertEquals(2, loadCount)
    }

    private fun declaration(name: String) = FormulaDeclaration(
        name = name,
        description = name,
        parameters = null,
        formulaUri = "formula",
    )
}
