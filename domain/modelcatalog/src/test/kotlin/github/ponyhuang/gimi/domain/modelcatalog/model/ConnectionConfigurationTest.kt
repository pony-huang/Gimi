package github.ponyhuang.gimi.domain.modelcatalog.model

import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保存格式校验与可取消的远端操作边界。 */
class ConnectionConfigurationTest {
    @Test
    fun validConnectionDoesNotRequireModelsOrSuccessfulNetworkTest() {
        assertTrue(service().hasValidConnectionConfiguration())
        assertTrue(service().copy(apiBaseUrl = "http://localhost:8080/v1").hasValidConnectionConfiguration())
    }

    @Test
    fun invalidKeysAddressesAndUnsupportedProtocolsCannotBeSaved() {
        listOf("", "file:///tmp/key", "https://", "https://user:password@example.com", "https://example.com/#fragment")
            .forEach { assertFalse(service().copy(apiBaseUrl = it).hasValidConnectionConfiguration()) }
        assertFalse(service().copy(apiKey = " ").hasValidConnectionConfiguration())
        assertFalse(service().copy(apiProtocol = ApiProtocol.Anthropic).hasValidConnectionConfiguration())
    }

    @Test
    fun cancellationIsNotReportedAsAConnectionOrRefreshFailure() = runBlocking {
        val gateway = object : ModelServiceRemoteGateway {
            override suspend fun validateConnection(service: LLMModelSetting): ServiceConnectionResult =
                throw CancellationException()
            override suspend fun fetchModels(service: LLMModelSetting): List<Model> = throw CancellationException()
        }
        assertTrue(runCatching { TestModelServiceConnectionUseCase(gateway)(service()) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { RefreshModelCatalogUseCase(gateway)(service()) }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun speechServiceRequiresWebSocketEndpoint() {
        val speech = service().copy(
            kind = ModelServiceKind.Speech,
            endpointSchemes = setOf("wss"),
            apiBaseUrl = SpeechEndpoints.QWEN_AUDIO_CN,
        )
        assertTrue(speech.hasValidConnectionConfiguration())
        // 语音服务只接受 wss:// 端点，HTTP 地址无法建立协议握手。
        assertFalse(speech.copy(apiBaseUrl = "https://api.example.com/v1").hasValidConnectionConfiguration())
        assertFalse(speech.copy(apiKey = " ").hasValidConnectionConfiguration())
    }

    @Test
    fun httpSpeechServiceUsesItsOwnEndpointSchemeContract() {
        val speech = service().copy(kind = ModelServiceKind.Speech, endpointSchemes = setOf("http", "https"))
        assertTrue(speech.hasValidConnectionConfiguration())
        assertFalse(speech.copy(apiBaseUrl = SpeechEndpoints.QWEN_AUDIO_CN).hasValidConnectionConfiguration())
    }

    @Test
    fun workspaceIdAcceptsHostnameSafeCharactersOnly() {
        val speech = service().copy(
            kind = ModelServiceKind.Speech,
            endpointSchemes = setOf("wss"),
            apiBaseUrl = SpeechEndpoints.QWEN_AUDIO_CN,
        )
        // 业务空间 ID 会被拼进专属域名的主机名：字母/数字/连字符/下划线合法，空白视为未填写。
        assertTrue(speech.copy(workspaceId = "ws-123").hasValidConnectionConfiguration())
        assertTrue(speech.copy(workspaceId = " ws_456 ").hasValidConnectionConfiguration())
        assertTrue(speech.copy(workspaceId = null).hasValidConnectionConfiguration())
        assertTrue(speech.copy(workspaceId = "").hasValidConnectionConfiguration())
        // 空白与路径分隔符会让专属域名主机名非法，保存前直接拒绝。
        assertFalse(speech.copy(workspaceId = "ws 123").hasValidConnectionConfiguration())
        assertFalse(speech.copy(workspaceId = "ws/123").hasValidConnectionConfiguration())
        assertFalse(speech.copy(workspaceId = "ws.123").hasValidConnectionConfiguration())
    }

    private fun service() = LLMModelSetting("openai", "OpenAI", true, apiKey = "fake-key",
        apiBaseUrl = "https://api.example.com/v1", apiProtocol = ApiProtocol.OpenaiChatcompletions,
        supportedProtocols = listOf(ApiProtocol.OpenaiChatcompletions), anthropicBaseUrl = "", groups = emptyList())
}
