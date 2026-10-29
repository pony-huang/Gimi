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
            override suspend fun validateConnection(service: LLMModelSetting): Boolean = throw CancellationException()
            override suspend fun fetchModels(service: LLMModelSetting): List<Model> = throw CancellationException()
        }
        assertTrue(runCatching { TestModelServiceConnectionUseCase(gateway)(service()) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { RefreshModelCatalogUseCase(gateway)(service()) }.exceptionOrNull() is CancellationException)
    }

    private fun service() = LLMModelSetting("openai", "OpenAI", true, apiKey = "fake-key",
        apiBaseUrl = "https://api.example.com/v1", apiProtocol = ApiProtocol.OpenaiChatcompletions,
        supportedProtocols = listOf(ApiProtocol.OpenaiChatcompletions), anthropicBaseUrl = "", groups = emptyList())
}
