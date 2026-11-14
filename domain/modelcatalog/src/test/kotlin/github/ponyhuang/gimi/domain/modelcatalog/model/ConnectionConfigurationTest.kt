package github.ponyhuang.gimi.domain.modelcatalog.model

import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.usecase.toConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.workspaceIdIsValid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保存格式校验、草稿派生与可取消的远端操作边界。 */
class ConnectionConfigurationTest {
    @Test
    fun connectionViewRequiresCredentialAndWellFormedEndpoint() {
        assertTrue(connection().hasValidConnectionConfiguration())
        assertFalse(connection(apiKey = "").hasValidConnectionConfiguration())
    }

    @Test
    fun invalidEndpointsAndIndependentKeysAreRejected() {
        listOf("", "file:///tmp/key", "https://", "https://user:password@example.com", "https://example.com/#fragment")
            .forEach { assertFalse(connection(endpoint = it).hasValidConnectionConfiguration()) }
    }

    @Test
    fun draftRequiresAtLeastOneSelectedModel() {
        val draft = draft(
            sharedApiKey = "key",
            connections = listOf(conversation().copy(models = listOf(model()))),
        )
        assertTrue(draft.hasValidConfiguration(template()))
        assertFalse(draft.copy(connections = listOf(conversation())).hasValidConfiguration(template()))
    }

    @Test
    fun unselectedCapabilitiesDoNotGateSaving() {
        val draft = draft(
            sharedApiKey = "key",
            connections = listOf(
                conversation().copy(models = listOf(model())),
                recognition(), // 未选模型的识别连接：结构不完整也不阻碍保存
            ),
        )
        assertTrue(draft.hasValidConfiguration(template()))
    }

    @Test
    fun selectedConnectionsMustBeStructurallyComplete() {
        val draft = draft(
            sharedApiKey = "key",
            connections = listOf(
                conversation().copy(models = listOf(model()), endpoint = "https://bad example"),
            ),
        )
        assertFalse(draft.hasValidConfiguration(template()))
        // 独立凭据为空的已选连接同样拒绝保存
        val independentBlank = draft.copy(
            connections = listOf(
                conversation().copy(
                    models = listOf(model()),
                    credential = CredentialSource.Independent(" "),
                ),
            ),
        )
        assertFalse(independentBlank.hasValidConfiguration(template()))
    }

    @Test
    fun sharedCredentialMustBePresentWhenAnySelectedConnectionUsesIt() {
        val draft = draft(
            sharedApiKey = " ",
            connections = listOf(conversation().copy(models = listOf(model()))),
        )
        assertFalse(draft.hasValidConfiguration(template()))
    }

    @Test
    fun workspaceIdIsCheckedOnlyForTemplatesThatSupportIt() {
        val draft = draft(
            sharedApiKey = "key",
            connections = listOf(
                recognition().copy(models = listOf(model()), workspaceId = "ws 123"),
            ),
        )
        // 模板未支持业务空间时该字段被忽略
        assertTrue(draft.hasValidConfiguration(template()))
        // 百炼模板支持业务空间：非法字符在保存前拒绝
        assertFalse(draft.hasValidConfiguration(template(supportsWorkspace = true)))
    }

    @Test
    fun workspaceIdAcceptsHostnameSafeCharactersOnly() {
        // 业务空间 ID 会被拼进专属域名的主机名：字母/数字/连字符/下划线合法，空白视为未填写。
        assertTrue(workspaceIdIsValid("ws-123"))
        assertTrue(workspaceIdIsValid(" ws_456 "))
        assertTrue(workspaceIdIsValid(null))
        assertTrue(workspaceIdIsValid(""))
        // 空白与路径分隔符会让专属域名主机名非法，保存前直接拒绝。
        assertFalse(workspaceIdIsValid("ws 123"))
        assertFalse(workspaceIdIsValid("ws/123"))
        assertFalse(workspaceIdIsValid("ws.123"))
    }

    @Test
    fun cancellationIsNotReportedAsAConnectionOrRefreshFailure() = runBlocking {
        val gateway = object : ModelServiceRemoteGateway {
            override suspend fun validateConnection(connection: ResolvedServiceConnection): ServiceConnectionResult =
                throw CancellationException()
            override suspend fun fetchModels(connection: ResolvedServiceConnection): List<Model> =
                throw CancellationException()
        }
        assertTrue(
            runCatching { TestModelServiceConnectionUseCase(gateway)(resolvedView()) }.exceptionOrNull()
                is CancellationException,
        )
        assertTrue(
            runCatching { RefreshModelCatalogUseCase(gateway)(resolvedView()) }.exceptionOrNull()
                is CancellationException,
        )
    }

    @Test
    fun templateConnectionsDeriveDraftConnectionsWithStableCapabilityMapping() {
        val template = template(supportsWorkspace = true)
        val draft = draft(
            connections = template.connections.map { it.toConnection() },
        )
        assertEquals(
            template.connections.map { it.capability },
            draft.connections.map { it.capability },
        )
        // 端点与协议跟随模板默认值
        val vendorManaged = template.connection(ServiceCapability.Realtime)
        val derived = draft.connection(ServiceCapability.Realtime)
        assertEquals(vendorManaged?.defaultEndpoint(ApiProtocol.OpenaiChatcompletions), derived?.endpoint)
        assertEquals(vendorManaged?.protocols?.first(), derived?.protocol)
        assertNull(draft.connection("missing"))
    }

    private fun draft(
        sharedApiKey: String = "",
        connections: List<ServiceConnection>,
    ) = ModelServiceDraft(
        instanceId = null,
        templateId = "openai",
        name = "OpenAI 个人",
        isOfficialToolsEnabled = true,
        sharedApiKey = sharedApiKey,
        connections = connections,
    )

    private fun template(supportsWorkspace: Boolean = false) = ModelServiceTemplate(
        id = "openai",
        name = "OpenAI",
        connections = listOf(
            TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Recognition,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to SpeechEndpoints.OPENAI_SPEECH_BASE_URL),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
                supportsWorkspace = supportsWorkspace,
            ),
            TemplateConnection(
                capability = ServiceCapability.Synthesis,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to SpeechEndpoints.OPENAI_SPEECH_BASE_URL),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Realtime,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to SpeechEndpoints.OPENAI_REALTIME_DEFAULT),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("wss"),
            ),
        ),
    )

    private fun conversation() = ServiceConnection(
        id = "conn-conversation-1",
        capability = ServiceCapability.Conversation,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = "https://api.example.com/v1",
        credential = CredentialSource.Shared,
    )

    private fun recognition() = ServiceConnection(
        id = "conn-recognition-1",
        capability = ServiceCapability.Recognition,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = SpeechEndpoints.OPENAI_SPEECH_BASE_URL,
        credential = CredentialSource.Shared,
    )

    private fun model() = Model("gpt-4.1", "gpt-4.1")

    private fun connection(
        endpoint: String = "https://api.example.com/v1",
        apiKey: String = "key",
    ) = ResolvedServiceConnection(
        instanceId = "instance-1",
        templateId = "openai",
        connectionId = "conn-conversation-1",
        capability = ServiceCapability.Conversation,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = endpoint,
        apiKey = apiKey,
        workspaceId = null,
        model = model(),
    )

    private fun resolvedView() = connection()
}
