package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelBackend
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelLoadPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class LoadedLocalModelPoolTest {
    private fun config(id: String) = LocalModelRuntimeConfig("/$id", LocalModelBackend.CPU)
    private fun model() = object : Model {
        override val name = "fake"
        override fun generateContent(request: LlmRequest, stream: Boolean) = flowOf(LlmResponse())
    }

    @Test fun preloadedEngineIsReusedAcrossTurnsAndOldEngineClosesBeforeSwitch() = runBlocking {
        val events = mutableListOf<String>()
        val pool = LoadedLocalModelPool { name, _ ->
            events += "open:$name"
            LocalModelHandle(model()) { events += "close:$name" }
        }
        pool.prepare("a", config("a"))
        assertEquals(LocalModelLoadPhase.Ready, pool.state.value.phase)
        repeat(2) { pool.model("a", config("a")).generateContent(LlmRequest(), true).toList() }
        assertEquals(listOf("open:a"), events)
        pool.prepare("b", config("b"))
        assertEquals(listOf("open:a", "close:a", "open:b"), events)
        assertEquals("b", pool.state.value.modelId)
        pool.unload()
        assertEquals("close:b", events.last())
        assertEquals(LocalModelLoadPhase.Unloaded, pool.state.value.phase)
    }

    @Test fun failedInitializationDoesNotPublishReadyAndCanBeRetried() = runBlocking {
        var attempts = 0
        val pool = LoadedLocalModelPool { _, _ ->
            if (++attempts == 1) error("native initialization failed")
            LocalModelHandle(model()) {}
        }
        pool.prepare("a", config("a"))
        assertEquals(LocalModelLoadPhase.Failed, pool.state.value.phase)
        pool.prepare("a", config("a"))
        assertEquals(LocalModelLoadPhase.Ready, pool.state.value.phase)
        pool.unload()
    }

    @Test fun backgroundRequestWithoutPreloadingReleasesEngineAfterCompletion() = runBlocking {
        var closed = 0
        val pool = LoadedLocalModelPool { _, _ -> LocalModelHandle(model()) { closed++ } }
        pool.model("background", config("background")).generateContent(LlmRequest(), true).toList()
        assertEquals(1, closed)
        assertEquals(LocalModelLoadPhase.Unloaded, pool.state.value.phase)
    }

    @Test fun cancelledInitializationClosesEngineWithoutPublishingReady() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.atomic.AtomicInteger()
        val pool = LoadedLocalModelPool { _, _ ->
            started.complete(Unit)
            check(finish.await(5, java.util.concurrent.TimeUnit.SECONDS))
            LocalModelHandle(model()) { closed.incrementAndGet() }
        }
        val job = launch { pool.prepare("a", config("a")) }
        withTimeout(5_000) { started.await() }
        job.cancel()
        finish.countDown()
        job.join()
        assertEquals(1, closed.get())
        assertEquals(LocalModelLoadPhase.Unloaded, pool.state.value.phase)
    }

    @Test fun unloadingWaitsForGenerationAndCancellationReleasesEngine() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val closed = java.util.concurrent.atomic.AtomicInteger()
        val pool = LoadedLocalModelPool { _, _ ->
            val delegate = object : Model {
                override val name = "fake"
                override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            LocalModelHandle(delegate) { closed.incrementAndGet() }
        }
        pool.prepare("a", config("a"))
        val generating = launch { pool.model("a", config("a")).generateContent(LlmRequest(), true).collect() }
        withTimeout(5_000) { started.await() }
        val unloading = launch { pool.unload() }
        yield()
        assertEquals(0, closed.get())
        generating.cancelAndJoin()
        withTimeout(5_000) { unloading.join() }
        assertEquals(1, closed.get())
    }

    @Test fun removalClosesEngineBeforeDeletingAndBlocksNewLoads() = runBlocking {
        var closed = false
        val pool = LoadedLocalModelPool { _, _ -> LocalModelHandle(model()) { closed = true } }
        pool.prepare("a", config("a"))
        pool.remove { assertTrue(closed) }
        assertEquals(LocalModelLoadPhase.Unloaded, pool.state.value.phase)
    }
}
