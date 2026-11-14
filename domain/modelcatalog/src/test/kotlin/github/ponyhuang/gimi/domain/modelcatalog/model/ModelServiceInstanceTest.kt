package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 实例连接解析与凭据收敛规则；官方工具凭据归属依赖这些语义。 */
class ModelServiceInstanceTest {
    @Test
    fun independentCredentialOverridesSharedOnlyForItsConnection() {
        val instance = instance(
            sharedApiKey = "shared-key",
            connections = listOf(
                connection("chat", ServiceCapability.Conversation, CredentialSource.Shared),
                connection("realtime", ServiceCapability.Realtime, CredentialSource.Independent("rt-key")),
            ),
        )
        assertEquals("shared-key", instance.effectiveApiKey(instance.connection("chat")!!))
        assertEquals("rt-key", instance.effectiveApiKey(instance.connection("realtime")!!))
    }

    @Test
    fun connectionsResolveByIdAndCapability() {
        val instance = instance(connections = listOf(connection("chat", ServiceCapability.Conversation)))
        assertEquals("chat", instance.connection("chat")?.id)
        assertEquals("chat", instance.connection(ServiceCapability.Conversation)?.id)
        assertNull(instance.connection(ServiceCapability.Synthesis))
        assertNull(instance.connection("missing"))
    }

    @Test
    fun officialToolsRequireEnabledServiceEnabledPreferenceAndConversationCredential() {
        assertTrue(instance(connections = listOf(connection("chat", ServiceCapability.Conversation))).canProvideOfficialTools())
        for (broken in listOf(
            instance(isEnabled = false, connections = listOf(connection("chat", ServiceCapability.Conversation))),
            instance(isOfficialToolsEnabled = false, connections = listOf(connection("chat", ServiceCapability.Conversation))),
            instance(sharedApiKey = " ", connections = listOf(connection("chat", ServiceCapability.Conversation))),
            instance(sharedApiKey = "key"), // 没有对话连接也没有独立凭据
        )) {
            assertFalse(broken.canProvideOfficialTools())
        }
        // 语音连接的独立凭据不能为对话工具供凭据
        assertFalse(
            instance(
                sharedApiKey = " ",
                connections = listOf(connection("rt", ServiceCapability.Realtime, CredentialSource.Independent("rt-key"))),
            ).canProvideOfficialTools(),
        )
    }

    @Test
    fun customServicesHaveNoTemplateButStillResolveConnections() {
        val custom = instance(templateId = null, connections = listOf(connection("chat", ServiceCapability.Conversation)))
        assertNull(custom.templateId)
        assertTrue(custom.canProvideOfficialTools())
    }

    private fun instance(
        templateId: String? = "openai",
        isEnabled: Boolean = true,
        isOfficialToolsEnabled: Boolean = true,
        sharedApiKey: String = "key",
        connections: List<ServiceConnection> = emptyList(),
    ) = ModelServiceInstance(
        id = "instance-1",
        name = "OpenAI 个人",
        templateId = templateId,
        isEnabled = isEnabled,
        isOfficialToolsEnabled = isOfficialToolsEnabled,
        sharedApiKey = sharedApiKey,
        connections = connections,
    )

    private fun connection(
        id: String,
        capability: ServiceCapability,
        credential: CredentialSource = CredentialSource.Shared,
    ) = ServiceConnection(
        id = id,
        capability = capability,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = "https://api.example.com/v1",
        credential = credential,
    )
}
