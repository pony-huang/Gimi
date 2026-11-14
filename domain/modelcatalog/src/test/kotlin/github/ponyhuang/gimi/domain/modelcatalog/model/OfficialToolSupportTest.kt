package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 独立于 Android、网络和 Agent SDK 的官方工具可用性规则。 */
class OfficialToolSupportTest {
    private val support = OfficialToolSupport()

    @Test
    fun modelFamilyMatchingRequiresASeparatorAndIgnoresPathAndCase() {
        for (modelId in listOf("GLM", "models/GLM-4.6", "glm_4", "glm.4")) {
            assertEquals(
                listOf(OfficialToolIds.GLM_WEB_SEARCH),
                support.specsFor("glm", ApiProtocol.OpenaiChatcompletions, modelId).map { it.toolId },
            )
        }
        assertTrue(support.specsFor("glm", ApiProtocol.OpenaiChatcompletions, "glmatrix").isEmpty())
    }

    @Test
    fun crossModelAvailabilityIncludesOnlyIndependentApiTools() {
        val specs = available(
            listOf(instance("anthropic"), instance("openai"), instance("minimax"), instance("kimi")),
        )
        assertEquals(
            listOf(OfficialToolIds.ANTHROPIC_WEB_SEARCH, OfficialToolIds.MINIMAX_IMAGE_GENERATION, OfficialToolIds.KIMI_WEB_TOOLS),
            specs.map { it.toolId },
        )
        assertFalse(specs.any { it.toolId == OfficialToolIds.MINIMAX_WEB_SEARCH })
    }

    @Test
    fun disabledOrUncredentialedSourcesCannotContributeTools() {
        for (source in listOf(
            instance("minimax").copy(isEnabled = false),
            instance("minimax").copy(isOfficialToolsEnabled = false),
            instance("minimax").copy(sharedApiKey = " "),
        )) {
            assertTrue(available(listOf(source)).isEmpty())
        }
    }

    @Test
    fun crossModelToolsRequireSupportedSourceProtocol() {
        // MiniMax 实例的对话连接换成不支持声明工具的协议后，图像生成不能再跨模型提供。
        assertTrue(available(listOf(minimaxInstance(protocol = ApiProtocol.Gemini))).isEmpty())
    }

    @Test
    fun kimiIndependentToolsRequireEnabledCredentialedSource() {
        assertEquals(listOf(OfficialToolIds.KIMI_WEB_TOOLS), available(listOf(instance("kimi"))).map { it.toolId })
        for (source in listOf(
            instance("kimi").copy(isEnabled = false),
            instance("kimi").copy(isOfficialToolsEnabled = false),
            instance("kimi").copy(sharedApiKey = " "),
        )) {
            assertTrue(available(listOf(source)).isEmpty())
        }
        // 对话连接使用独立凭据时，工具凭据跟随连接覆盖值。
        assertTrue(
            available(
                listOf(
                    instance("kimi", sharedApiKey = " ").copy(
                        connections = listOf(
                            ServiceConnection(
                                id = "conn-conversation-1",
                                capability = ServiceCapability.Conversation,
                                protocol = ApiProtocol.Anthropic,
                                endpoint = "https://api.kimi.example/v1",
                                credential = CredentialSource.Independent("independent-key"),
                            ),
                        ),
                    ),
                ),
            ).isNotEmpty(),
        )
    }

    @Test
    fun sameTemplateInstancesKeepCredentialOwnershipSeparate() {
        // 两个同模板实例：启用 + 有凭据的实例提供工具，凭据为空的实例不提供。
        val specs = available(
            listOf(
                instance("minimax", sharedApiKey = "mm-key", uniqueId = "instance-a"),
                instance("minimax", sharedApiKey = " ", uniqueId = "instance-b"),
            ),
        )
        assertEquals(listOf(OfficialToolIds.MINIMAX_IMAGE_GENERATION), specs.map { it.toolId })
    }

    @Test
    fun absentActiveInstanceCanStillReceiveIndependentApiTools() {
        assertEquals(
            listOf(OfficialToolIds.MINIMAX_IMAGE_GENERATION),
            available(listOf(instance("minimax"))).map { it.toolId },
        )
    }

    private fun available(instances: List<ModelServiceInstance>) = support.availableSpecsFor(
        "anthropic", ApiProtocol.Anthropic, "claude-sonnet-4-6", instances,
    )

    private fun minimaxInstance(protocol: ApiProtocol) = instance("minimax").let { base ->
        base.copy(connections = base.connections.map { connection ->
            if (connection.capability == ServiceCapability.Conversation) {
                connection.copy(protocol = protocol)
            } else {
                connection
            }
        })
    }

    private fun instance(
        templateId: String,
        sharedApiKey: String = "key",
        uniqueId: String = "instance-$templateId",
    ): ModelServiceInstance {
        val protocol = when (templateId) {
            "anthropic" -> ApiProtocol.Anthropic
            else -> ApiProtocol.OpenaiChatcompletions
        }
        return ModelServiceInstance(
            id = uniqueId,
            name = templateId,
            templateId = templateId,
            isEnabled = true,
            isOfficialToolsEnabled = true,
            sharedApiKey = sharedApiKey,
            connections = listOf(
                ServiceConnection(
                    id = "conn-conversation-1",
                    capability = ServiceCapability.Conversation,
                    protocol = protocol,
                    endpoint = "https://example.com",
                    credential = CredentialSource.Shared,
                ),
            ),
        )
    }
}
