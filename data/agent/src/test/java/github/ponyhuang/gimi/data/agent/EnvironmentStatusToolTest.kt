package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.contribution.EnvironmentStatusTool
import github.ponyhuang.gimi.domain.environment.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 状态入口始终解释执行工具缺失原因，不把空技能库混入环境状态。 */
class EnvironmentStatusToolTest {
    private val repository = mockk<EnvironmentRepository>()
    private val tool = EnvironmentStatusTool(repository)

    @Test fun distinguishesUnavailableDisabledIncompatibleAndReady() = runTest {
        val cases = listOf(
            EnvironmentStatus() to "runtime_missing",
            EnvironmentStatus(available = true, connectionAvailable = false) to "connection_unavailable",
            EnvironmentStatus(available = true, runtimeVersion = RuntimeVersion(1, "old", 1), bundledRuntimeVersion = RuntimeVersion(2, "new", 3)) to "update_required",
            EnvironmentStatus(available = true, installed = true) to "disabled",
            EnvironmentStatus(available = true) to "environment_not_installed",
            EnvironmentStatus(available = true, installing = true) to "installing",
            EnvironmentStatus(available = true, installed = true, enabled = true) to "ready",
        )
        for ((status, state) in cases) {
            coEvery { repository.status() } returns status
            val response = tool.run(mockk(), emptyMap()) as Map<*, *>
            assertEquals(state, response["state"])
            assertEquals(state == "ready", response["execution_available"])
            assertEquals(true, response["skill_installation_available"])
        }
    }

    @Test fun communicationErrorHasRecoveryGuidance() = runTest {
        coEvery { repository.status() } throws IOException("disconnected")
        val response = tool.run(mockk(), emptyMap()) as Map<*, *>
        assertEquals("connection_unavailable", response["state"])
        assertTrue(response["next_step"].toString().contains("reconnect"))
    }
}
