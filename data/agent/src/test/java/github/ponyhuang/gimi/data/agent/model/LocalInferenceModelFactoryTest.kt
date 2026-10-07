package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.litertlm.LiteRtLmEngine
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.ai.edge.litertlm.EngineConfig
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelBackend
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelLoadPhase
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalInferenceModelFactoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun preparationInitializesNativeEngineBeforePublishingReady() = runBlocking {
        verifyPreparation(fails = false)
    }

    @Test fun nativeInitializationFailureClosesWrapperAndKeepsInputUnavailable() = runBlocking {
        verifyPreparation(fails = true)
    }

    private suspend fun verifyPreparation(fails: Boolean) {
        mockkObject(LiteRtLmModel.Companion)
        try {
            val file = folder.newFile("model.litertlm")
            val config = LocalModelRuntimeConfig(file.absolutePath, LocalModelBackend.CPU)
            val repository = mockk<LocalModelRepository>(relaxed = true) { every { resolve("a") } returns config }
            val engine = mockk<LiteRtLmEngine>(relaxed = true)
            val wrapper = mockk<LiteRtLmModel>(relaxed = true) { every { this@mockk.engine } returns engine }
            every { LiteRtLmModel.create(any<EngineConfig>(), "a") } returns wrapper
            if (fails) every { engine.initialize() } throws IllegalStateException("native loading failed")
            val factory = LocalInferenceModelFactory(repository)
            factory.prepare("a")
            verify(exactly = 1) { engine.initialize() }
            assertEquals(if (fails) LocalModelLoadPhase.Failed else LocalModelLoadPhase.Ready, factory.state.value.phase)
            if (!fails) verify(exactly = 0) { wrapper.close() }
            factory.unload()
            verify(exactly = 1) { wrapper.close() }
        } finally {
            unmockkObject(LiteRtLmModel.Companion)
        }
    }
}
