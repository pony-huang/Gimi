package github.ponyhuang.gimi.data.agent.chatbot

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Chatbot 形态只放行当前厂商自有的联网检索工具：跨厂商工具与图像生成一律不进 Chatbot Agent。 */
class ChatbotOfficialToolsetTest {

    private val catalog = object : OfficialToolFunctionCatalog {
        override val all = OfficialToolSupport().all
        override fun availableSpecsFor(templateId: String, protocol: ApiProtocol, modelId: String): List<OfficialToolSpec> = emptyList()
        override fun enabledSearchCandidateSpecs(): List<OfficialToolSpec> = emptyList()
        override fun supportedToolIds(templateId: String, protocol: ApiProtocol): Set<String> = emptySet()
        override fun availableTools(activeInstance: ModelServiceInstance, activeModelId: String): List<OfficialToolAvailability> = emptyList()
        override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> = emptyList()
    }

    @Test fun `deepseek keeps only its own web tools`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        coEvery { delegate.resolveTools(any(), any()) } returns listOf(
            tool("deepseek_web_search"),
            tool("deepseek_web_fetch"),
            tool("minimax_image_generation"),
            tool("kimi_search"),
        )
        val resolved = ChatbotOfficialToolset(delegate, catalog).resolveTools(runtime("deepseek"), null)
        assertEquals(listOf("deepseek_web_search", "deepseek_web_fetch"), resolved.map { it.name })
    }

    @Test fun `provider declaration tools are kept under the owning vendor only`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        // provider_web_search 是 Anthropic/MiniMax 声明式工具的本地声明名：miniMax 模型放行，
        // deepSeek 模型同名声明也必须拦下（DeepSeek 的 Chatbot 白名单没有声明式工具）。
        coEvery { delegate.resolveTools(any(), any()) } returns listOf(tool("provider_web_search"))
        assertEquals(listOf("provider_web_search"), ChatbotOfficialToolset(delegate, catalog)
            .resolveTools(runtime("minimax"), null).map { it.name })
        assertEquals(emptyList<String>(), ChatbotOfficialToolset(delegate, catalog)
            .resolveTools(runtime("deepseek"), null).map { it.name })
    }

    @Test fun `glm keeps its local function names`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        coEvery { delegate.resolveTools(any(), any()) } returns listOf(
            tool("web_search"),
            tool("web_reader"),
            tool("deepseek_web_search"),
        )
        val resolved = ChatbotOfficialToolset(delegate, catalog).resolveTools(runtime("glm"), null)
        assertEquals(listOf("web_search", "web_reader"), resolved.map { it.name })
    }

    @Test fun `vendors without web search tools resolve nothing`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        coEvery { delegate.resolveTools(any(), any()) } returns listOf(tool("kimi_search"), tool("web_search"))
        // Qwen/MiMo 等厂商没有 Chatbot 检索工具声明：解析为空，即无工具的普通 agent。
        assertEquals(emptyList<String>(), ChatbotOfficialToolset(delegate, catalog)
            .resolveTools(runtime("qwen"), null).map { it.name })
    }

    @Test fun `kimi keeps its bundled search and fetch functions`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        coEvery { delegate.resolveTools(any(), any()) } returns listOf(
            tool("kimi_search"),
            tool("kimi_search_pro"),
            tool("kimi_fetch"),
            tool("deepseek_web_search"),
        )
        val resolved = ChatbotOfficialToolset(delegate, catalog).resolveTools(runtime("kimi"), null)
        assertEquals(listOf("kimi_search", "kimi_search_pro", "kimi_fetch"), resolved.map { it.name })
    }

    @Test fun `session level switches are delegated to the official toolset`() = runTest {
        val delegate = mockk<DefaultOfficialToolset>()
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf("deepseek_web_search" to setOf("deepseek_web_search")),
        )
        coEvery { delegate.resolveTools(any(), selection) } returns listOf(tool("deepseek_web_search"))
        val resolved = ChatbotOfficialToolset(delegate, catalog).resolveTools(runtime("deepseek"), selection)
        assertEquals(listOf("deepseek_web_search"), resolved.map { it.name })
    }

    private fun tool(name: String): BaseTool = object : FunctionTool(name = name, description = name) {
        override fun declaration(): FunctionDeclaration = FunctionDeclaration(name = name, description = description)
        override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any = mapOf("ok" to true)
    }

    private fun runtime(templateId: String) = ModelRuntimeMetadata(
        serviceId = "$templateId-service",
        templateId = templateId,
        baseType = ApiProtocol.Anthropic,
        modelId = "$templateId-flash",
        fullBaseUrl = "https://api.example.com/anthropic",
        supportsImages = false,
    )
}
