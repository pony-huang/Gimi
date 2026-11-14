package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.tools.skills.InstallSkillTool
import github.ponyhuang.gimi.domain.skills.model.*
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import github.ponyhuang.gimi.domain.skills.usecase.InstallSkillArchiveUseCase
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 无执行环境时直接安装、覆盖授权、参数边界与失败清理。 */
class InstallSkillToolTest {
    private val repository = mockk<SkillRepository>()
    private val tool = InstallSkillTool(InstallSkillArchiveUseCase(repository))
    private val url = "https://example.com/repo.zip"

    private fun prepare(replaces: Boolean = false) {
        coEvery { repository.prepareImport(any()) } returns PreparedSkillImport("ticket", "demo", "Demo", replaces)
        coEvery { repository.commitImport(any(), any()) } just Runs
        coEvery { repository.discardImport(any()) } just Runs
    }

    @Test fun installsSelectedRepositoryDirectoryWithoutAnyRuntime() = runTest {
        prepare()
        val response = tool.run(mockk(), mapOf("url" to url, "directory" to "repo-main/demo")) as Map<*, *>
        assertEquals(true, response["success"])
        assertEquals("demo", response["name"])
        coVerify { repository.prepareImport(SkillImportSource.Url(url, "repo-main/demo")) }
        coVerify { repository.commitImport("ticket", false) }
        coVerify { repository.discardImport("ticket") }
    }

    @Test fun replacementRequiresExplicitFlagAndStillCleansTicket() = runTest {
        prepare(replaces = true)
        val response = tool.run(mockk(), mapOf("url" to url)) as Map<*, *>
        assertEquals("ReplacementNotAllowed", response["error_code"])
        coVerify(exactly = 0) { repository.commitImport(any(), any()) }
        coVerify { repository.discardImport("ticket") }
    }

    @Test fun invalidSourcesAreRejectedBeforeDownloading() = runTest {
        for (args in listOf(mapOf("url" to "http://example.com/repo.zip"),
            mapOf("url" to url, "directory" to "../demo"),
            mapOf("url" to url, "allow_replace" to "true"),
            mapOf("url" to url, "directory" to 3))) {
            assertEquals("InvalidArguments", (tool.run(mockk(), args) as Map<*, *>)["error_code"])
        }
        coVerify(exactly = 0) { repository.prepareImport(any()) }
    }

    @Test fun invalidManifestReturnsActionableFailure() = runTest {
        coEvery { repository.prepareImport(any()) } throws SkillImportFailure(SkillImportFailure.Reason.InvalidManifest, "bad")
        val response = tool.run(mockk(), mapOf("url" to url)) as Map<*, *>
        assertEquals("InvalidManifest", response["error_code"])
        assertTrue(response["error"].toString().contains("description"))
    }

    @Test fun missingBranchReportsHttp404WithBranchRecoveryHint() = runTest {
        coEvery { repository.prepareImport(any()) } throws SkillImportFailure(
            SkillImportFailure.Reason.DownloadFailed, "server details",
            downloadIssue = SkillImportFailure.DownloadIssue.HttpError, httpStatus = 404,
        )
        val response = tool.run(mockk(), mapOf("url" to url)) as Map<*, *>
        assertEquals(404, response["http_status"])
        assertEquals("HttpError", response["download_issue"])
        assertTrue(response["error"].toString().contains("branch"))
        assertFalse(response["error"].toString().contains("server details"))
    }

    @Test fun cancellationPropagatesAndReleasesPreparedImport() = runTest {
        prepare()
        coEvery { repository.commitImport(any(), any()) } throws CancellationException("cancelled")
        try {
            tool.run(mockk(), mapOf("url" to url))
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        coVerify { repository.discardImport("ticket") }
    }
}
