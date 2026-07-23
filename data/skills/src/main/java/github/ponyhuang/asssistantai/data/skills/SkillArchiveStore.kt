package github.ponyhuang.asssistantai.data.skills

import com.google.adk.kt.skills.NewFileSystemSource
import github.ponyhuang.asssistantai.domain.skills.model.InstalledSkill
import github.ponyhuang.asssistantai.domain.skills.model.PreparedSkillImport
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportFailure
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SkillArchiveStore(
    private val skillsRoot: File,
    private val stagingRoot: File,
    private val limits: SkillArchiveLimits = SkillArchiveLimits(),
) {
    private val mutex = Mutex()

    init {
        skillsRoot.mkdirs()
        if (stagingRoot.exists()) stagingRoot.deleteRecursively()
        stagingRoot.mkdirs()
    }

    suspend fun prepare(input: InputStream): PreparedSkillImport = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val pendingRoot = File(stagingRoot, id)
        pendingRoot.mkdirs()
        try {
            extract(input, pendingRoot)
            val children = pendingRoot.listFiles().orEmpty().filter { it.isDirectory }
            if (children.size != 1 || pendingRoot.listFiles().orEmpty().size != 1) {
                fail(
                    SkillImportFailure.Reason.InvalidStructure,
                    "The archive must contain exactly one top-level skill directory.",
                )
            }
            val skillDirectory = children.single()
            val source = NewFileSystemSource(pendingRoot.absolutePath)
            val frontmatters = source.listFrontmatters().getOrElse {
                fail(
                    SkillImportFailure.Reason.InvalidManifest,
                    it.message ?: "The SKILL.md file is invalid.",
                    it,
                )
            }
            if (frontmatters.size != 1) {
                fail(
                    SkillImportFailure.Reason.InvalidManifest,
                    "The archive must contain exactly one valid SKILL.md.",
                )
            }
            val frontmatter = frontmatters.single()
            if (frontmatter.name != skillDirectory.name) {
                fail(
                    SkillImportFailure.Reason.InvalidManifest,
                    "The skill name must match its directory name.",
                )
            }
            PreparedSkillImport(
                id = id,
                name = frontmatter.name,
                description = frontmatter.description,
                replacesExisting = File(skillsRoot, frontmatter.name).exists(),
            )
        } catch (failure: SkillImportFailure) {
            pendingRoot.deleteRecursively()
            throw failure
        } catch (error: ZipException) {
            pendingRoot.deleteRecursively()
            fail(SkillImportFailure.Reason.InvalidStructure, "The ZIP archive is invalid.", error)
        } catch (error: IOException) {
            pendingRoot.deleteRecursively()
            fail(SkillImportFailure.Reason.StorageFailure, "The skill archive could not be read.", error)
        } catch (error: IllegalArgumentException) {
            pendingRoot.deleteRecursively()
            fail(SkillImportFailure.Reason.InvalidManifest, "The SKILL.md file is invalid.", error)
        }
    }

    suspend fun commit(preparedId: String, allowReplace: Boolean) = mutex.withLock {
        val pendingRoot = preparedRoot(preparedId)
        val skillDirectory = pendingRoot.listFiles().orEmpty().singleOrNull { it.isDirectory }
            ?: fail(
                SkillImportFailure.Reason.PreparedImportNotFound,
                "The prepared skill import no longer exists.",
            )
        validateSkillName(skillDirectory.name)
        val destination = File(skillsRoot, skillDirectory.name)
        if (destination.exists() && !allowReplace) {
            fail(
                SkillImportFailure.Reason.ReplacementNotAllowed,
                "Replacing an installed skill requires explicit confirmation.",
            )
        }

        val backup = File(stagingRoot, "$preparedId-backup")
        try {
            if (destination.exists()) move(destination, backup)
            move(skillDirectory, destination)
            backup.deleteRecursively()
            pendingRoot.deleteRecursively()
        } catch (error: Exception) {
            if (!destination.exists() && backup.exists()) runCatching { move(backup, destination) }
            fail(
                SkillImportFailure.Reason.StorageFailure,
                "The skill could not be installed.",
                error,
            )
        }
    }

    suspend fun discard(preparedId: String) = mutex.withLock {
        preparedRoot(preparedId).deleteRecursively()
    }

    suspend fun remove(name: String) = mutex.withLock {
        validateSkillName(name)
        val target = File(skillsRoot, name)
        ensureDirectChild(target, skillsRoot)
        if (target.exists() && !target.deleteRecursively()) {
            fail(SkillImportFailure.Reason.StorageFailure, "The skill could not be removed.")
        }
    }

    suspend fun listInstalled(): List<InstalledSkill> = mutex.withLock {
        val source = NewFileSystemSource(skillsRoot.absolutePath)
        source.listFrontmatters().getOrElse {
            fail(
                SkillImportFailure.Reason.InvalidManifest,
                it.message ?: "An installed skill is invalid.",
                it,
            )
        }.map { InstalledSkill(it.name, it.description) }.sortedBy { it.name }
    }

    private fun extract(input: InputStream, pendingRoot: File) {
        val limited = LimitedInputStream(input, limits.archiveBytes)
        var entries = 0
        var extractedBytes = 0L
        val canonicalRoot = pendingRoot.canonicalFile
        ZipInputStream(limited.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                if (entries > limits.entries) {
                    fail(SkillImportFailure.Reason.TooManyEntries, "The ZIP contains too many entries.")
                }
                val entryName = entry.name
                if (
                    entryName.isBlank() ||
                    entryName.startsWith("/") ||
                    entryName.startsWith("\\") ||
                    '\\' in entryName ||
                    entryName.split('/').any { it == ".." }
                ) {
                    fail(SkillImportFailure.Reason.UnsafeArchive, "The ZIP contains an unsafe path.")
                }
                val target = File(pendingRoot, entryName).canonicalFile
                ensureDescendant(target, canonicalRoot)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            extractedBytes += count
                            if (extractedBytes > limits.extractedBytes) {
                                fail(
                                    SkillImportFailure.Reason.ArchiveTooLarge,
                                    "The extracted skill is too large.",
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (entries == 0) {
            fail(SkillImportFailure.Reason.InvalidStructure, "The ZIP archive is empty.")
        }
    }

    private fun preparedRoot(id: String): File {
        runCatching { UUID.fromString(id) }.getOrElse {
            fail(
                SkillImportFailure.Reason.PreparedImportNotFound,
                "The prepared skill import no longer exists.",
            )
        }
        val root = File(stagingRoot, id)
        ensureDirectChild(root, stagingRoot)
        if (!root.isDirectory) {
            fail(
                SkillImportFailure.Reason.PreparedImportNotFound,
                "The prepared skill import no longer exists.",
            )
        }
        return root
    }

    private fun validateSkillName(name: String) {
        if (
            name.isEmpty() ||
            name.length > 64 ||
            name.startsWith("-") ||
            name.endsWith("-") ||
            "--" in name ||
            name.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }
        ) {
            fail(SkillImportFailure.Reason.InvalidSkillName, "The skill name is invalid.")
        }
    }

    private fun move(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun ensureDirectChild(child: File, parent: File) {
        val canonicalParent = parent.canonicalFile
        if (child.canonicalFile.parentFile != canonicalParent) {
            fail(SkillImportFailure.Reason.InvalidSkillName, "The skill path is invalid.")
        }
    }

    private fun ensureDescendant(child: File, parent: File) {
        val prefix = parent.path + File.separator
        if (!child.path.startsWith(prefix)) {
            fail(SkillImportFailure.Reason.UnsafeArchive, "The ZIP contains an unsafe path.")
        }
    }

    private fun fail(
        reason: SkillImportFailure.Reason,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw SkillImportFailure(reason, message, cause)

    private class LimitedInputStream(
        input: InputStream,
        private val limit: Long,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) increment(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) increment(read)
            return read
        }

        private fun increment(amount: Int) {
            count += amount
            if (count > limit) {
                throw SkillImportFailure(
                    SkillImportFailure.Reason.ArchiveTooLarge,
                    "The ZIP archive is too large.",
                )
            }
        }
    }
}

internal data class SkillArchiveLimits(
    val archiveBytes: Long = 20L * 1024L * 1024L,
    val extractedBytes: Long = 100L * 1024L * 1024L,
    val entries: Int = 2_000,
)
