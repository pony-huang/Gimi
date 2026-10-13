package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.apps.App
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentConfirmationResumeTest {
    @Test
    fun eachApprovalExecutesItsToolWhenTwoConfirmationsShareAnInvocation() = runTest {
        verifyDecisions(listOf(true, true), listOf("first", "second"))
    }

    @Test
    fun rejectingOneToolDoesNotApproveItWhenTheBatchResumes() = runTest {
        verifyDecisions(listOf(true, false), listOf("first"))
    }

    @Test
    fun oneConfirmationResumesImmediately() = runTest {
        verifyDecisions(listOf(true), listOf("first"))
    }

    private suspend fun verifyDecisions(decisions: List<Boolean>, expected: List<String>) {
        val names = listOf("first", "second").take(decisions.size)
        val executed = mutableListOf<String>()
        var modelCalls = 0
        val model = object : Model {
            override val name = "confirmation-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                modelCalls++
                val parts = if (modelCalls == 1) {
                    names.map { name ->
                        Part(functionCall = FunctionCall(id = "call-$name", name = name, args = emptyMap()))
                    }
                } else listOf(Part(text = "done"))
                emit(LlmResponse(content = Content(role = "model", parts = parts)))
            }
        }
        val tools = names.map { name ->
            object : BaseTool(name, "fixture") {
                override fun declaration() = FunctionDeclaration(name, "fixture")
                override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
                    if (context.toolConfirmation == null) {
                        context.requestConfirmation()
                        context.actions.skipSummarization = true
                        return mapOf("status" to "pending")
                    }
                    if (context.toolConfirmation?.confirmed == true) executed += name
                    return mapOf("status" to "done")
                }
            }
        }
        val native = InMemoryRunner(App(
            appName = AgentChatRunner.APP_NAME,
            rootAgent = LlmAgent(name = "agent", model = model, tools = tools),
            resumabilityConfig = ResumabilityConfig(isResumable = true),
        ))
        val execution = AgentChatExecution("user", "session", native, emptyMap())
        val pending = execution.send("execute both").toList().flatMap { it.functionCalls() }
            .filter { it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }
            .sortedBy { (it.args["originalFunctionCall"] as? Map<*, *>)?.get("name").toString() }
        assertEquals(decisions.size, pending.size)
        pending.forEachIndexed { index, call ->
            execution.respondToToolConfirmation(requireNotNull(call.id), decisions[index]).toList()
            if (index < pending.lastIndex) {
                assertEquals(emptyList<String>(), executed)
                assertEquals(1, modelCalls)
            }
        }
        assertEquals(expected.sorted(), executed.sorted())
    }
}
