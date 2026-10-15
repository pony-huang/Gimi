package github.ponyhuang.gimi.data.agent.tools.skills

import github.ponyhuang.gimi.domain.sandbox.*
import github.ponyhuang.gimi.domain.skills.model.*
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import github.ponyhuang.gimi.domain.skills.usecase.InstallSkillArchiveUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SandboxSkillInstallerTest {
    private val settings = object : SandboxSettingsRepository {
        override val configuration = MutableStateFlow(SandboxConfiguration(true, SandboxConnection(apiKey = "private-key")))
        override val revision = MutableStateFlow(0L)
        override suspend fun setEnabled(enabled: Boolean) { configuration.value = configuration.value.copy(enabled = enabled) }
        override suspend fun saveConnection(connection: SandboxConnection) { configuration.value = configuration.value.copy(connection = connection) }
    }
    private val repository = Repository()
    private val session = SandboxSessionKey("user", "chat")
    private var result = SandboxExecutionResult(true, SandboxOutput.ReadBytes("/skill.zip", byteArrayOf(0, -1, 7)))
    private var calls = 0
    private var receivedSession: SandboxSessionKey? = null
    private var receivedOperation: SandboxOperation? = null
    private val execution = object : SandboxExecutionRepository {
        override suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult {
            calls++
            receivedSession = session
            receivedOperation = operation
            return result
        }
    }
    private val installer = SandboxSkillInstaller(settings, execution, InstallSkillArchiveUseCase(repository))

    @Test fun installsBinaryArchiveUsingCurrentConversationWithoutReturningBytes() = runTest {
        val response = installer.install(session, "/skill.zip", false)
        assertEquals(true, response["success"])
        assertEquals("demo", response["name"])
        assertEquals(session, receivedSession)
        assertEquals(SandboxOperation.ReadBytes("/skill.zip", 20 * 1024 * 1024), receivedOperation)
        assertArrayEquals(byteArrayOf(0, -1, 7), (repository.source as SkillImportSource.ArchiveBytes).content)
        assertEquals(false, repository.allowReplace)
        assertEquals(setOf("success", "name", "description"), response.keys)
    }

    @Test fun duplicateDoesNotCommitUntilReplacementIsExplicit() = runTest {
        repository.existing = true
        assertEquals("ReplacementNotAllowed", installer.install(session, "/skill.zip", false)["error_code"])
        assertEquals(0, repository.commits)
        assertEquals(1, repository.discards)
        assertEquals(true, installer.install(session, "/skill.zip", true)["success"])
        assertEquals(true, repository.allowReplace)
    }

    @Test fun failedOrResetDownloadNeverReachesInstaller() = runTest {
        result = SandboxExecutionResult(false, error = "remote private diagnostic", environmentReset = true)
        val response = installer.install(session, "/skill.zip", false)
        assertEquals(false, response["success"])
        assertEquals(true, response["environment_reset"])
        assertFalse(response.toString().contains("private"))
        assertNull(repository.source)
        result = SandboxExecutionResult(true, SandboxOutput.ReadBytes("/skill.zip", byteArrayOf(1)), environmentReset = true)
        assertEquals(false, installer.install(session, "/skill.zip", false)["success"])
        assertNull(repository.source)
    }

    @Test fun disabledConfigurationAndInvalidIdentityNeverReadRemoteFiles() = runTest {
        settings.setEnabled(false)
        assertEquals(false, installer.install(session, "/skill.zip", false)["success"])
        settings.setEnabled(true)
        assertEquals(false, installer.install(SandboxSessionKey("user", ""), "/skill.zip", false)["success"])
        assertEquals(false, installer.install(session, " ", false)["success"])
        assertEquals(0, calls)
    }

    @Test fun storageFailureIsSanitizedAndPreparedImportIsDiscarded() = runTest {
        repository.commitFailure = SkillImportFailure(SkillImportFailure.Reason.StorageFailure, "/private/secret")
        val response = installer.install(session, "/skill.zip", false)
        assertEquals(false, response["success"])
        assertEquals("StorageFailure", response["error_code"])
        assertFalse(response.toString().contains("/private"))
        assertEquals(1, repository.discards)
    }

    @Test fun cancellationPropagatesAndCleansPreparedImport() = runTest {
        repository.commitFailure = CancellationException("cancelled")
        try {
            installer.install(session, "/skill.zip", false)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, repository.discards)
    }

    @Test fun concurrentSameNameInstallIsStillRejectedAtCommitAndCleaned() = runTest {
        repository.commitFailure = SkillImportFailure(SkillImportFailure.Reason.ReplacementNotAllowed, "conflict")
        val response = installer.install(session, "/skill.zip", false)
        assertEquals("ReplacementNotAllowed", response["error_code"])
        assertEquals(1, repository.discards)
    }

    @Test fun preparationFailureIsSanitizedWithoutCommitting() = runTest {
        repository.prepareFailure = SkillImportFailure(SkillImportFailure.Reason.UnsafeArchive, "/private/secret")
        val response = installer.install(session, "/skill.zip", false)
        assertEquals("UnsafeArchive", response["error_code"])
        assertFalse(response.toString().contains("/private"))
        assertEquals(0, repository.commits)
    }

    @Test fun oversizedBinaryResponseNeverReachesLocalPreparation() = runTest {
        result = SandboxExecutionResult(true, SandboxOutput.ReadBytes("/skill.zip", ByteArray(20 * 1024 * 1024 + 1)))
        assertEquals("ArchiveTooLarge", installer.install(session, "/skill.zip", false)["error_code"])
        assertNull(repository.source)
    }

    private class Repository : SkillRepository {
        var source: SkillImportSource? = null
        var existing = false
        var commits = 0
        var discards = 0
        var allowReplace: Boolean? = null
        var commitFailure: Exception? = null
        var prepareFailure: SkillImportFailure? = null
        override fun observeInstalled() = MutableStateFlow(emptyList<InstalledSkill>())
        override suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport {
            prepareFailure?.let { throw it }
            this.source = source
            return PreparedSkillImport("prepared", "demo", "Demo", existing)
        }
        override suspend fun commitImport(preparedId: String, allowReplace: Boolean) {
            commitFailure?.let { throw it }
            commits++
            this.allowReplace = allowReplace
        }
        override suspend fun discardImport(preparedId: String) { discards++ }
        override suspend fun remove(name: String) = Unit
    }
}
