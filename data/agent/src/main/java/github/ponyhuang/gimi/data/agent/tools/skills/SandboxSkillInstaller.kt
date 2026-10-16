package github.ponyhuang.gimi.data.agent.tools.skills

import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import github.ponyhuang.gimi.domain.sandbox.SandboxSessionKey
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.usecase.InstallSkillArchiveUseCase
import javax.inject.Inject

/** 连接当前聊天的沙箱二进制读取与宿主技能安装；压缩包不进入模型历史。 */
class SandboxSkillInstaller @Inject constructor(
    private val settings: SandboxSettingsRepository,
    private val execution: SandboxExecutionRepository,
    private val installArchive: InstallSkillArchiveUseCase,
) {
    suspend fun install(session: SandboxSessionKey, path: String, allowReplace: Boolean): Map<String, Any> {
        val configuration = settings.configuration.value
        if (!configuration.isReady) return failure("SandboxUnavailable", "The sandbox is disabled or not configured.")
        if (session.userId.isBlank() || session.sessionId.isBlank()) {
            return failure("MissingConversation", "A persistent conversation identity is required.")
        }
        if (path.isBlank() || '\u0000' in path) return failure("InvalidArguments", "A valid sandbox ZIP path is required.")

        val downloaded = execution.execute(session, SandboxOperation.ReadBytes(path, MAX_ARCHIVE_BYTES))
        // 重建后的同路径文件不再代表调用者准备的产物，禁止继续安装或自动重放。
        if (downloaded.environmentReset) {
            return failure("EnvironmentReset", "The sandbox was reset. Prepare the archive again before installing.") +
                ("environment_reset" to true)
        }
        if (!downloaded.success) return failure("DownloadFailed", "The sandbox archive could not be downloaded. Check that the ZIP exists and is at most 20 MiB.")
        if (settings.configuration.value != configuration) return failure("SandboxChanged", "Sandbox settings changed during download. Installation was not started.")
        val bytes = (downloaded.output as? SandboxOutput.ReadBytes)?.content
            ?: return failure("DownloadFailed", "The sandbox did not return a binary archive.")
        if (bytes.size > MAX_ARCHIVE_BYTES) {
            return failure("ArchiveTooLarge", importFailureMessage(SkillImportFailure.Reason.ArchiveTooLarge))
        }
        return try {
            val installed = installArchive(SkillImportSource.ArchiveBytes(bytes), allowReplace)
            mapOf("success" to true, "name" to installed.name, "description" to installed.description)
        } catch (error: SkillImportFailure) {
            failure(error.reason.name, importFailureMessage(error.reason))
        }
    }

    private fun failure(code: String, message: String): Map<String, Any> =
        mapOf("success" to false, "error_code" to code, "error" to message)

    private fun importFailureMessage(reason: SkillImportFailure.Reason): String = when (reason) {
        SkillImportFailure.Reason.ReplacementNotAllowed -> "A skill with this name is already installed. Retry with allow_replace=true only after the user explicitly requests replacement or update."
        SkillImportFailure.Reason.ArchiveTooLarge -> "The archive exceeds the skill import size limits."
        SkillImportFailure.Reason.TooManyEntries -> "The archive contains too many entries."
        SkillImportFailure.Reason.UnsafeArchive -> "The archive contains unsafe paths or entries."
        SkillImportFailure.Reason.InvalidStructure -> "The ZIP must contain exactly one top-level skill directory."
        SkillImportFailure.Reason.InvalidManifest -> "The skill must contain a valid SKILL.md whose name matches its directory."
        SkillImportFailure.Reason.InvalidSkillName -> "The skill name is invalid."
        SkillImportFailure.Reason.PreparedImportNotFound -> "The prepared import is no longer available. Prepare the archive again."
        SkillImportFailure.Reason.StorageFailure -> "The skill could not be saved to local storage."
        SkillImportFailure.Reason.InvalidSource -> "The skill archive source is invalid."
        SkillImportFailure.Reason.DownloadFailed -> "The skill archive could not be downloaded."
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 20 * 1024 * 1024
    }
}
