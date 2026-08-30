package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsetConfirmationResumeToolTest {

    @Test
    fun delegatesToToolThatIsStillAvailableInCurrentRequestContext() = runTest {
        var receivedArgs: Map<String, Any?>? = null
        val activeTool = object : BaseTool("adjust_media_volume", "Adjust volume") {
            override fun declaration(): FunctionDeclaration? = null

            override suspend fun run(
                context: ToolContext,
                args: Map<String, Any?>,
            ): Any {
                receivedArgs = args
                return mapOf("appliedLevel" to 50)
            }
        }
        val source = object : Toolset {
            override suspend fun getTools(
                readonlyContext: ReadonlyContext?,
            ): List<BaseTool> = listOf(activeTool)
        }
        val context = mockk<ToolContext>()
        every { context.context } returns mockk()
        val resumeTool = ToolsetConfirmationResumeTool(source, activeTool)
        val args = mapOf<String, Any>("delta" to -10)

        val result = resumeTool.run(context, args)

        assertEquals(args, receivedArgs)
        assertEquals(mapOf("appliedLevel" to 50), result)
    }
}
