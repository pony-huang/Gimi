package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.models.*
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 模型调用计费与取消边界：仅格式错误允许一次纠错，其他失败立即传播。 */
class A2uiGenerationTest {
    private val capabilities = A2uiCapabilities("catalog", "{}")

    @Test
    fun retriesInvalidOutputOnceAndRestrictsSubagentToRenderTool() = runTest {
        val model = FakeModel()
        var validations = 0
        val protocol = object : A2uiProtocol {
            override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
                if (validations++ == 0) throw IllegalArgumentException("Invalid component")
            }
            override fun replay(records: List<A2uiRecord>) = records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
        }
        val document = A2uiGeneration(model, protocol).generate("surface", capabilities, "Create form")
        assertEquals(2, model.requests.size)
        assertEquals("surface", document.surfaceId)
        assertEquals(listOf("render_a2ui"), model.requests[0].config.tools!!.flatMap { it.functionDeclarations.orEmpty() }.map { it.name })
        assertTrue(model.requests[1].contents.single().parts.single().text!!.contains("Invalid component"))
    }

    @Test
    fun exhaustedValidationStopsAfterTwoCalls() = runTest {
        val model = FakeModel()
        try {
            A2uiGeneration(model, rejectingProtocol()).generate("surface", capabilities, "Create")
            fail("Expected failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, model.requests.size)
    }

    @Test
    fun networkErrorsAndCancellationAreNeverRetried() = runTest {
        for (failure in listOf(IllegalStateException("Network failure"), CancellationException("Stopped"))) {
            val model = FakeModel(failure)
            try { A2uiGeneration(model, rejectingProtocol()).generate("surface", capabilities, "Create"); fail("Expected failure") }
            catch (actual: IllegalStateException) { assertSame(failure, actual) }
            assertEquals(1, model.requests.size)
        }
    }

    private fun rejectingProtocol() = object : A2uiProtocol {
        override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
            throw IllegalArgumentException("Rejected")
        }
        override fun replay(records: List<A2uiRecord>) = records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
    }

    private class FakeModel(private val failure: RuntimeException? = null) : Model {
        override val name = "test-model"
        val requests = mutableListOf<LlmRequest>()
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
            requests += request
            failure?.let { throw it }
            emit(LlmResponse(content = Content(parts = listOf(Part(functionCall = FunctionCall(
                name = "render_a2ui", args = mapOf("components" to "[]", "data" to "{}"),
            ))))))
        }
    }
}
