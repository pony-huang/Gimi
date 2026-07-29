package github.ponyhuang.asssistantai.agent

import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.agent.tools.official.WebSearchOfficialToolProvider
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialToolIntegrationTest {

    @Test
    fun registryContributesNativePlaceholderForDeclaredWebSearchId() {
        val registry = OfficialToolRegistry(
            providers = setOf(WebSearchOfficialToolProvider()),
        )

        val native = registry.resolve(
            ModelConfig(
                serviceId = "mimo",
                baseType = ApiProtocol.Standard,
                modelId = "model",
                apiKey = "key",
                fullBaseUrl = "https://example.com",
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), native.tools.map { it.name })
        assertTrue(native.toolsets.isEmpty())
    }
}
