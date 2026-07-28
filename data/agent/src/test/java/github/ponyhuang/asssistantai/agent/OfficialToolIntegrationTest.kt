package github.ponyhuang.asssistantai.agent

import github.ponyhuang.asssistantai.agent.tools.official.KimiFormulaOfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.agent.tools.official.WebSearchOfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialToolIntegrationTest {


    @Test
    fun registryCombinesNativePlaceholdersAndAgentToolsetsFromDeclaredIds() {
        val registry = OfficialToolRegistry(
            providers = setOf(
                WebSearchOfficialToolProvider(),
                KimiFormulaOfficialToolProvider(OkHttpClient()),
            ),
        )

        val native = registry.resolve(
            config(
                serviceId = "mimo",
                baseType = ApiProtocol.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )
        val agentTool = registry.resolve(
            config(
                serviceId = "kimi",
                baseType = ApiProtocol.Standard,
                officialTools = listOf(OfficialToolIds.KIMI_FORMULAS),
            ),
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), native.tools.map { it.name })
        assertTrue(native.toolsets.isEmpty())
        assertTrue(agentTool.tools.isEmpty())
        assertTrue(agentTool.toolsets.single() is KimiFormulaToolset)
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol,
        officialTools: List<String>,
    ) = ModelConfig(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = officialTools,
    )

}
