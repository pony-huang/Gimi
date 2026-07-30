package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkillArchiveStoreTest {
    private lateinit var testRoot: File
    private lateinit var skillsRoot: File
    private lateinit var stagingRoot: File
    private lateinit var store: SkillArchiveStore

    @Before
    fun setUp() {
        testRoot = createTempDirectory("skill-store-").toFile()
        skillsRoot = File(testRoot, "skills")
        stagingRoot = File(testRoot, "staging")
        store = SkillArchiveStore(skillsRoot, stagingRoot)
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun preparesAndCommitsSingleSkillArchive() = runTest {
        val prepared = store.prepare(validArchive("zhihu-search", "搜索知乎"))

        assertEquals("zhihu-search", prepared.name)
        assertEquals("搜索知乎", prepared.description)
        assertFalse(prepared.replacesExisting)

        store.commit(prepared.id, allowReplace = false)

        assertTrue(File(skillsRoot, "zhihu-search/SKILL.md").isFile)
        assertEquals(listOf("zhihu-search"), store.listInstalled().map { it.name })
    }

    @Test
    fun duplicateRequiresExplicitReplacementAndReplacesAtomically() = runTest {
        val first = store.prepare(validArchive("demo", "旧描述"))
        store.commit(first.id, allowReplace = false)
        val replacement = store.prepare(validArchive("demo", "新描述"))

        assertTrue(replacement.replacesExisting)
        val failure = runCatching {
            store.commit(replacement.id, allowReplace = false)
        }.exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.Reason.ReplacementNotAllowed, failure.reason)
        assertEquals("旧描述", store.listInstalled().single().description)

        store.commit(replacement.id, allowReplace = true)
        assertEquals("新描述", store.listInstalled().single().description)
    }

    @Test
    fun rejectsArchiveWithMultipleTopLevelDirectories() = runTest {
        val failure = runCatching {
            store.prepare(
                archive(
                    "first/SKILL.md" to skillMd("first", "First"),
                    "second/SKILL.md" to skillMd("second", "Second"),
                ),
            )
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.InvalidStructure, failure.reason)
        assertTrue(store.listInstalled().isEmpty())
    }

    @Test
    fun rejectsZipSlipEntry() = runTest {
        val failure = runCatching {
            store.prepare(
                archive(
                    "safe/SKILL.md" to skillMd("safe", "Safe"),
                    "../escaped.txt" to "escaped",
                ),
            )
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.UnsafeArchive, failure.reason)
        assertFalse(File(testRoot.parentFile, "escaped.txt").exists())
    }

    @Test
    fun rejectsMalformedFrontmatter() = runTest {
        val failure = runCatching {
            store.prepare(archive("demo/SKILL.md" to "# Missing frontmatter"))
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.InvalidManifest, failure.reason)
    }

    @Test
    fun rejectsArchiveThatExceedsCompressedLimit() = runTest {
        store = SkillArchiveStore(
            skillsRoot,
            stagingRoot,
            SkillArchiveLimits(
                archiveBytes = 16,
                extractedBytes = 1_024,
                entries = 10,
            ),
        )

        val failure = runCatching {
            store.prepare(validArchive("demo", "Demo"))
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.ArchiveTooLarge, failure.reason)
    }

    @Test
    fun rejectsArchiveThatExceedsExtractedLimit() = runTest {
        store = SkillArchiveStore(
            skillsRoot,
            stagingRoot,
            SkillArchiveLimits(
                archiveBytes = 10_000,
                extractedBytes = 10,
                entries = 10,
            ),
        )

        val failure = runCatching {
            store.prepare(validArchive("demo", "Demo"))
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.ArchiveTooLarge, failure.reason)
    }

    @Test
    fun rejectsArchiveThatExceedsEntryLimit() = runTest {
        store = SkillArchiveStore(
            skillsRoot,
            stagingRoot,
            SkillArchiveLimits(
                archiveBytes = 10_000,
                extractedBytes = 10_000,
                entries = 1,
            ),
        )

        val failure = runCatching {
            store.prepare(validArchive("demo", "Demo"))
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.TooManyEntries, failure.reason)
    }

    @Test
    fun removesOnlyInstalledSkill() = runTest {
        val prepared = store.prepare(validArchive("demo", "Demo"))
        store.commit(prepared.id, allowReplace = false)

        store.remove("demo")

        assertTrue(store.listInstalled().isEmpty())
        val failure = runCatching { store.remove("../outside") }
            .exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.Reason.InvalidSkillName, failure.reason)
    }

    private fun validArchive(name: String, description: String) =
        archive(
            "$name/SKILL.md" to skillMd(name, description),
            "$name/scripts/example.py" to "print('ok')",
        )

    private fun skillMd(name: String, description: String) = """
        ---
        name: $name
        description: $description
        ---

        # Instructions
    """.trimIndent()

    private fun archive(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
