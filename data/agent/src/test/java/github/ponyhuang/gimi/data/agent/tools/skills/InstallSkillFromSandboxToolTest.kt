package github.ponyhuang.gimi.data.agent.tools.skills

import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.domain.sandbox.*
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import github.ponyhuang.gimi.domain.skills.usecase.InstallSkillArchiveUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InstallSkillFromSandboxToolTest {
    private val configuration = MutableStateFlow(SandboxConfiguration(true, SandboxConnection(apiKey = "secret")))
    private val settings = mockk<SandboxSettingsRepository> { every { this@mockk.configuration } returns this@InstallSkillFromSandboxToolTest.configuration }
    private val execution = mockk<SandboxExecutionRepository>()
    private val repository = mockk<SkillRepository>()
    private val tool = InstallSkillFromSandboxTool(SandboxSkillInstaller(settings, execution, InstallSkillArchiveUseCase(repository)))
    private fun context(id: String? = "chat") = mockk<ToolContext> {
        every { this@mockk.context.session } returns Session(SessionKey("Gimi", "user", id))
    }

    @Test fun rejectsMalformedArgumentsAndMissingIdentityBeforeDownload() = runTest {
        listOf(emptyMap(), mapOf("path" to " "), mapOf("path" to "bad\u0000path"),
            mapOf("path" to "/skill.zip", "allow_replace" to "true"),
            mapOf("path" to "/skill.zip", "allow_replace" to null)).forEach { args ->
            assertEquals(false, (tool.run(context(), args) as Map<*, *>)["success"])
        }
        assertEquals(false, (tool.run(context(null), mapOf("path" to "/skill.zip")) as Map<*, *>)["success"])
        coVerify(exactly = 0) { execution.execute(any(), any()) }
    }

    @Test fun cachedInstallationToolRechecksDisabledConfiguration() = runTest {
        configuration.value = configuration.value.copy(enabled = false)
        assertEquals(false, (tool.run(context(), mapOf("path" to "/skill.zip")) as Map<*, *>)["success"])
        coVerify(exactly = 0) { execution.execute(any(), any()) }
    }

    @Test fun forwardsExplicitReplacementAndUsesContextIdentity() = runTest {
        coEvery { execution.execute(SandboxSessionKey("user", "chat"), any()) } returns
            SandboxExecutionResult(true, SandboxOutput.ReadBytes("/skill.zip", byteArrayOf(1)))
        coEvery { repository.prepareImport(any()) } returns PreparedSkillImport("id", "demo", "Demo", true)
        coEvery { repository.commitImport("id", true) } returns Unit
        coEvery { repository.discardImport("id") } returns Unit
        val response = tool.run(context(), mapOf("path" to "/skill.zip", "allow_replace" to true)) as Map<*, *>
        assertEquals(true, response["success"])
        coVerify(exactly = 1) { repository.commitImport("id", true) }
        assertEquals(listOf("path"), tool.declaration().parameters!!.required)
    }

    @Test(expected = CancellationException::class)
    fun cancellationDuringDownloadPropagatesWithoutPreparingImport() = runTest {
        coEvery { execution.execute(any(), any()) } throws CancellationException("cancelled")
        try {
            tool.run(context(), mapOf("path" to "/skill.zip"))
        } finally {
            coVerify(exactly = 0) { repository.prepareImport(any()) }
        }
    }

    @Test fun configurationChangeDuringDownloadPreventsLocalMutation() = runTest {
        coEvery { execution.execute(any(), any()) } answers {
            configuration.value = configuration.value.copy(enabled = false)
            SandboxExecutionResult(true, SandboxOutput.ReadBytes("/skill.zip", byteArrayOf(1)))
        }
        val response = tool.run(context(), mapOf("path" to "/skill.zip")) as Map<*, *>
        assertEquals("SandboxChanged", response["error_code"])
        coVerify(exactly = 0) { repository.prepareImport(any()) }
    }
}
