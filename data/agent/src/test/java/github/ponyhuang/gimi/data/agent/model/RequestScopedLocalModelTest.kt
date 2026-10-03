package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test

class RequestScopedLocalModelTest {
    @Test fun createsLazilyAndClosesAfterSuccessAndFailure() = runBlocking {
        for (fails in listOf(false, true)) {
            val opened = AtomicInteger()
            val closed = AtomicInteger()
            val delegate = object : Model {
                override val name = "fake"
                override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                    if (fails) error("generation failed")
                    emit(LlmResponse())
                }
            }
            val model = RequestScopedLocalModel("local", Mutex()) { opened.incrementAndGet(); LocalModelHandle(delegate) { closed.incrementAndGet() } }
            val output = model.generateContent(LlmRequest(), true)
            assertEquals(0, opened.get())
            try { output.toList(); assertFalse(fails) } catch (error: IllegalStateException) { assertTrue(fails) }
            assertEquals(1, opened.get())
            assertEquals(1, closed.get())
        }
    }

    @Test fun cancelledGenerationClosesAndReleasesExclusiveEngineSlot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val closed = AtomicInteger()
        val mutex = Mutex()
        val delegate = object : Model {
            override val name = "fake"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow { started.complete(Unit); awaitCancellation() }
        }
        val model = RequestScopedLocalModel("local", mutex) { LocalModelHandle(delegate) { closed.incrementAndGet() } }
        val job = launch { model.generateContent(LlmRequest(), true).collect() }
        withTimeout(5_000) { started.await(); job.cancelAndJoin() }
        assertEquals(1, closed.get())
        assertFalse(mutex.isLocked)
    }
}
