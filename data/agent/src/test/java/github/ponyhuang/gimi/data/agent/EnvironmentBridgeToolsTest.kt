package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.contribution.EnvironmentBridgeTools
import github.ponyhuang.gimi.domain.environment.*
import github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotRepository
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 交换工具保护启用边界、会话路由与临时归档回收。 */
class EnvironmentBridgeToolsTest {
    private val bridge = mockk<EnvironmentFileBridge>()
    private val snapshots = mockk<SkillSnapshotRepository>()
    private val environment = mockk<EnvironmentRepository>()
    private val tools = EnvironmentBridgeTools(bridge, snapshots, environment).tools()
    private fun context() = ToolContext(InvocationContext(
        session = Session(key = SessionKey("app", "user", "s1")), agent = mockk<BaseAgent>(),
    ))
    private fun ready() {
        coEvery { environment.status() } returns EnvironmentStatus(enabled = true, available = true)
    }

    @Test fun disabledEnvironmentRejectsCachedTools() = runTest {
        coEvery { environment.status() } returns EnvironmentStatus()
        tools.forEach { tool ->
            val result = (tool as com.google.adk.kt.tools.FunctionTool).execute(context(), emptyMap()) as Map<*, *>
            assertTrue(result.containsKey("error"))
        }
        coVerify(exactly = 0) { snapshots.export(any()) }
    }

    @Test fun preparesSkillAndReleasesArchive() = runTest {
        ready()
        coEvery { snapshots.export("example") } returns "archive"
        coEvery { bridge.prepareSkill("4:users1", "archive") } returns "/skill"
        coEvery { snapshots.release("archive") } just Runs
        val result = tools.first { it.name == "prepare_skill" }.run(context(), mapOf("name" to "example")) as Map<*, *>
        assertEquals("/skill", result["skill_path"])
        coVerify(exactly = 1) { snapshots.release("archive") }
    }

    @Test fun failedPreparationStillReleasesArchive() = runTest {
        ready()
        coEvery { snapshots.export("example") } returns "archive"
        coEvery { bridge.prepareSkill(any(), any()) } throws IOException("disconnected")
        coEvery { snapshots.release("archive") } just Runs
        val result = tools.first { it.name == "prepare_skill" }.run(context(), mapOf("name" to "example")) as Map<*, *>
        assertTrue(result.containsKey("error"))
        coVerify(exactly = 1) { snapshots.release("archive") }
    }

    @Test fun missingSkillExplainsAppInstallationInsteadOfEnvironmentFailure() = runTest {
        ready()
        coEvery { snapshots.export("missing") } throws github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotFailure(
            github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotFailure.Reason.NotInstalled,
        )
        val result = tools.first { it.name == "prepare_skill" }.run(context(), mapOf("name" to "missing")) as Map<*, *>
        assertEquals("SkillNotInstalled", result["error_code"])
        assertTrue(result["error"].toString().contains("install_skill"))
        coVerify(exactly = 0) { bridge.prepareSkill(any(), any()) }
    }

    @Test fun publishesIntoAppWorkspaceUsingCurrentSession() = runTest {
        ready()
        coEvery { bridge.publishFile("4:users1", "outputs/report.pdf") } returns "/app/report.pdf"
        val result = tools.first { it.name == "publish_environment_file" }.run(context(), mapOf("path" to "outputs/report.pdf")) as Map<*, *>
        assertEquals("/app/report.pdf", result["app_path"])
    }
}
