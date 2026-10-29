package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveFastModelAvailabilityUseCaseTest {

    @Test
    fun emitsFalseWhenNoFastModelSelected() = runBlocking {
        val source = FakeConfigurationSource { null }

        val available = ObserveFastModelAvailabilityUseCase(source)().first()

        assertEquals(false, available)
    }

    @Test
    fun emitsTrueWhenSelectionResolvesToUsableModel() = runBlocking {
        val selection = ModelSelection("service-1", "group-1", "model-1")
        val source = FakeConfigurationSource { requested ->
            ResolvedAgentModel(
                serviceId = "service-1",
                protocol = ApiProtocol.OpenaiChatcompletions,
                modelId = "model-1",
                apiKey = "key",
                modelBaseUrl = "https://example.com/v1",
            ).takeIf { requested == selection }
        }
        source.fastSelectionFlow.value = selection

        val available = ObserveFastModelAvailabilityUseCase(source)().first()

        assertEquals(true, available)
    }

    @Test
    fun recomputesWhenServiceConfigurationChangesWithoutSelectionChange() = runBlocking {
        val selection = ModelSelection("service-1", "group-1", "model-1")
        var resolvable = true
        val source = FakeConfigurationSource { requested ->
            ResolvedAgentModel(
                serviceId = "service-1",
                protocol = ApiProtocol.OpenaiChatcompletions,
                modelId = "model-1",
                apiKey = "key",
                modelBaseUrl = "https://example.com/v1",
            ).takeIf { resolvable && requested == selection }
        }
        source.fastSelectionFlow.value = selection
        val flow = ObserveFastModelAvailabilityUseCase(source)()
        assertEquals(true, flow.first())

        // 模拟服务被禁用/凭据被清空：selection 不变，仅配置版本变化也应触发重算。
        resolvable = false
        source.configurationRevisionFlow.value = 1L

        assertEquals(false, flow.first())
    }

    /** 只覆盖 use case 所需契约的最小假实现，resolver 行为由测试注入。 */
    private class FakeConfigurationSource(
        private val resolver: (ModelSelection?) -> ResolvedAgentModel?,
    ) : AgentModelConfigurationSource {
        val fastSelectionFlow: MutableStateFlow<ModelSelection?> = MutableStateFlow(null)
        val configurationRevisionFlow: MutableStateFlow<Long> = MutableStateFlow(0L)

        override val runtimeSelection: StateFlow<ModelSelection?> = MutableStateFlow(null)
        override val fastSelection: StateFlow<ModelSelection?> = fastSelectionFlow
        override val configurationRevision: StateFlow<Long> = configurationRevisionFlow

        override suspend fun awaitReady() = Unit

        override fun defaultSelection(): ModelSelection? = null

        override fun resolveChatModel(selection: ModelSelection?): ResolvedAgentModel? =
            resolver(selection)

        override fun currentServices(): List<LLMModelSetting> = emptyList()
    }
}
