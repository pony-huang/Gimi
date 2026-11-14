package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.environment.EnvironmentJobStatus
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.contribution.EnvironmentToolContribution
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.environment.EnvironmentCommandResult
import github.ponyhuang.gimi.domain.environment.EnvironmentDirectoryEntry
import github.ponyhuang.gimi.domain.environment.EnvironmentRepository
import github.ponyhuang.gimi.domain.environment.RuntimeVersion
import github.ponyhuang.gimi.domain.environment.EnvironmentStatus
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 环境贡献方的工具面：启用门控、全小写命名、任务工具与会话键派生。 */
class EnvironmentToolContributionTest {

    /** 内存态仓库；enabled 模拟跨进程开关，jobs 记录任务生命周期供查询映射断言。 */
    private class FakeRepository : EnvironmentRepository {
        var enabled = false
        var apiVersion = 1
        var connected = true
        var keepAlive = false
        var liveOutput = ""
        var executed = 0
        var executeError: IOException? = null
        var executeResult = EnvironmentCommandResult("out", "", 0)
        val started = mutableListOf<Pair<String, String>>()
        val stopped = mutableListOf<Pair<String, String>>()
        private val jobs = linkedMapOf<String, EnvironmentCommandResult?>()

        fun complete(id: String, result: EnvironmentCommandResult) { jobs[id] = result }
        override suspend fun companionApk() = "apk"
        override suspend fun status() = EnvironmentStatus(available = true, installed = enabled, enabled = enabled,
            runtimeVersion = RuntimeVersion(66, "installed", apiVersion), bundledRuntimeVersion = RuntimeVersion(66, "bundled", 1), connectionAvailable = connected)
        override suspend fun install() {}
        override suspend fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override suspend fun variables(): Map<String, String> = emptyMap()
        override suspend fun saveVariables(values: Map<String, String>) {}
        override suspend fun listDirectory(path: String): List<EnvironmentDirectoryEntry> = emptyList()
        override suspend fun execute(session: String, command: String, timeoutMillis: Long): EnvironmentCommandResult {
            executed++
            executeError?.let { throw it }
            return executeResult
        }
        override suspend fun readFile(session: String, path: String): ByteArray = byteArrayOf()
        override suspend fun writeFile(session: String, path: String, content: ByteArray) {}
        override suspend fun startJob(session: String, command: String, keepAlive: Boolean): String {
            this.keepAlive = keepAlive
            started += session to command
            return "job-1".also { jobs[it] = null }
        }
        override suspend fun queryJob(session: String, id: String): EnvironmentJobStatus {
            val result = jobs.getValue(id)
            return if (result == null) EnvironmentJobStatus(true, stdout = liveOutput)
            else EnvironmentJobStatus(false, result.stdout, result.stderr, result.exitCode, result.timedOut)
        }
        override suspend fun stopJob(session: String, id: String) { stopped += session to id; jobs.remove(id) }
    }

    private val repository = FakeRepository()
    private val contribution = EnvironmentToolContribution(repository, mockk { io.mockk.every { tools() } returns emptyList() })
    private fun spec(mode: ToolAccessMode) = AgentBuildSpec(toolAccessMode = mode, pluginRuntime = mockk())

    /** 会话键 (user, s1) 派生结果为 "4:users1"，覆盖跨用户隔离格式。 */
    private fun context() = ToolContext(
        InvocationContext(session = Session(key = SessionKey("app", "user", "s1")), agent = mockk<BaseAgent>()),
    )

    private suspend fun enabledTools(): List<BaseTool> =
        contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).flatMap { it.getTools(null) }

    @Test fun disabledEnvironmentHidesEveryToolSurface() = runTest {
        assertTrue(contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).flatMap { it.getTools(null) }.isEmpty())
        assertTrue(contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND)).isEmpty())
        assertTrue(contribution.toolCatalog(AgentToolCatalogContext(null)).none { it.source == "environment:local" })
    }

    @Test fun incompatibleRuntimeHidesToolsAndRejectsPreviouslyLoadedTools() = runTest {
        repository.enabled = true
        val execute = enabledTools().first { it.name == "execute" }
        repository.apiVersion = 0
        assertTrue(enabledTools().isEmpty())
        assertTrue(contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND)).isEmpty())
        assertTrue(contribution.toolCatalog(AgentToolCatalogContext(null)).none { it.source == "environment:local" })
        val result = execute.run(context(), mapOf("command" to "echo probe")) as Map<*, *>
        assertTrue(result.containsKey("error"))
        assertEquals(0, repository.executed)
    }

    @Test fun disconnectedRuntimeHidesAndRejectsCachedTools() = runTest {
        repository.enabled = true
        val execute = enabledTools().first { it.name == "execute" }
        repository.connected = false
        assertTrue(enabledTools().isEmpty())
        val result = execute.run(context(), mapOf("command" to "echo probe")) as Map<*, *>
        assertTrue(result.containsKey("error"))
        assertEquals(0, repository.executed)
    }

    @Test fun enabledEnvironmentDeclaresFourLowercaseTools() = runTest {
        repository.enabled = true
        val expected = setOf("execute", "start_job", "query_job", "stop_job")
        val always = enabledTools()
        // 全小写、无前缀：模型可见的工具名与其它执行环境保持一致。
        assertEquals(expected, always.map { it.name }.toSet())
        assertEquals(4, always.size)
        assertEquals(expected, always.map { it.declaration()!!.name }.toSet())
        val source = contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND)).single()
        assertEquals("environment:local", source.id)
        assertEquals(expected, source.loadEnabledTools(null).map { it.name }.toSet())
        val catalog = contribution.toolCatalog(AgentToolCatalogContext(null)).single { it.source == "environment:local" }
        assertEquals("environment:local", catalog.source)
        assertEquals(expected, catalog.tools.map { it.name }.toSet())
    }

    @Test fun statusIsResidentInBothModesEvenWhenExecutionToolsAreHidden() = runTest {
        for (mode in ToolAccessMode.entries) {
            assertEquals(listOf("get_environment_status"), contribution.tools(spec(mode)).map { it.name })
        }
        assertEquals(listOf("get_environment_status"), contribution.toolCatalog(AgentToolCatalogContext(null))
            .single { it.source == "environment:status" }.tools.map { it.name })
    }

    @Test fun managedJobsReturnLiveOutputAndRequireExplicitKeepAliveFlag() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val start = tools.first { it.name == "start_job" }
        start.run(context(), mapOf("command" to "cli login", "keep_alive" to true))
        assertEquals(true, repository.keepAlive)
        repository.liveOutput = "Open login link"
        val response = tools.first { it.name == "query_job" }.run(context(), mapOf("id" to "job-1")) as Map<*, *>
        assertEquals(true, response["running"])
        assertEquals("Open login link", response["stdout"])
        assertTrue((start.run(context(), mapOf("command" to "cli login", "keep_alive" to "true")) as Map<*, *>).containsKey("error"))
    }

    @Test fun revisionStaysConstantBecauseGatingIsDynamic() {
        // 启停走 getTools 实时复查，不依赖 Agent 重建，因此 revision 恒为 null。
        assertEquals(null, contribution.revision())
    }

    @Test fun executeRejectsMissingCommand() = runTest {
        repository.enabled = true
        val execute = enabledTools().first { it.name == "execute" }
        val result = execute.run(context(), emptyMap()) as Map<*, *>
        assertTrue((result["error"] as String).contains("command is required"))
        assertEquals(0, repository.executed)
    }

    @Test fun jobToolsRouteToSessionScopedRepositoryCalls() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val start = tools.first { it.name == "start_job" }
        val startedResult = start.run(context(), mapOf("command" to "pip install requests")) as Map<*, *>
        assertEquals("job-1", startedResult["id"])
        assertEquals("4:users1" to "pip install requests", repository.started.single())

        val stop = tools.first { it.name == "stop_job" }
        val stoppedResult = stop.run(context(), mapOf("id" to "job-1")) as Map<*, *>
        assertEquals(true, stoppedResult["stopped"])
        assertEquals("4:users1" to "job-1", repository.stopped.single())
    }

    @Test fun queryJobReportsRunningThenCompletedResult() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val start = tools.first { it.name == "start_job" }
        start.run(context(), mapOf("command" to "apt-get install -y python3"))

        val query = tools.first { it.name == "query_job" }
        val running = query.run(context(), mapOf("id" to "job-1")) as Map<*, *>
        assertEquals(true, running["running"])

        repository.complete("job-1", EnvironmentCommandResult("done", "warning", 2, timedOut = false))
        val completed = query.run(context(), mapOf("id" to "job-1")) as Map<*, *>
        assertEquals(false, completed["running"])
        assertEquals(2, completed["exit_code"])
        assertEquals("done", completed["stdout"])
        assertEquals("warning", completed["stderr"])
    }

    @Test fun jobToolsRejectMissingArguments() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val start = tools.first { it.name == "start_job" }
        assertTrue((start.run(context(), emptyMap()) as Map<*, *>)["error"].toString().contains("command required"))
        val query = tools.first { it.name == "query_job" }
        assertTrue((query.run(context(), emptyMap()) as Map<*, *>)["error"].toString().contains("id required"))
    }

    @Test fun disabledEnvironmentShortCircuitsExecutionAtRunTime() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val execute = tools.first { it.name == "execute" }
        // 工具面构建后开关被关闭：运行时必须直接短路，不得触达仓库。
        repository.enabled = false
        val result = execute.run(context(), mapOf("command" to "ls")) as Map<*, *>
        assertEquals("Local environment is disabled.", result["error"])
        assertEquals(0, repository.executed)
    }

    @Test fun repositoryFailureBecomesNonReplayableEnvironmentError() = runTest {
        repository.enabled = true
        val tools = enabledTools()
        val execute = tools.first { it.name == "execute" }
        repository.executeError = IOException("connection lost")
        val result = execute.run(context(), mapOf("command" to "ls")) as Map<*, *>
        assertEquals("error", result["status"])
        // 副作用未知，错误提示必须阻止模型自动重放同一命令。
        assertTrue((result["error"] as String).contains("do not replay automatically"))
    }

    @Test fun executeMapsExitCodeAndStreamsIntoToolResult() = runTest {
        repository.enabled = true
        repository.executeResult = EnvironmentCommandResult("hello", "warn", 3, timedOut = false)
        val execute = enabledTools().first { it.name == "execute" }
        val result = execute.run(context(), mapOf("command" to "demo")) as Map<*, *>
        assertEquals("hello", result["stdout"])
        assertEquals("warn", result["stderr"])
        assertEquals(3, result["exit_code"])
        assertEquals("error", result["status"])
    }
}
