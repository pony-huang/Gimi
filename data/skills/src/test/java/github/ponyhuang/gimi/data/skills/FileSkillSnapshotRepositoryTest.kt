package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.core.storage.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 执行快照保留资源并生成稳定内容版本，禁用技能不能导出。 */
class FileSkillSnapshotRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private var disabled = emptySet<String>()
    private val enabled = object : SkillEnabledStore {
        override fun disabledNames() = disabled
        override fun setEnabled(name: String, enabled: Boolean) { }
        override fun forget(name: String) { }
    }

    private fun repository(): FileSkillSnapshotRepository {
        val root = temporary.root
        val registry = StorageRegistry(setOf(SkillsStorage.Installed, SkillsStorage.Imports),
            FileSystemAppDirectoryResolver(StorageRoots(root, File(root, "cache"), File(root, "code"), null)))
        val installed = registry.resolve(SkillsStorage.INSTALLED_ID, create = true)
        val store = SkillArchiveStore(installed, registry.resolve(SkillsStorage.IMPORTS_ID, create = true))
        File(installed, "demo/assets").mkdirs()
        File(installed, "demo/SKILL.md").writeText("instructions")
        File(installed, "demo/assets/template.txt").writeText("template")
        return FileSkillSnapshotRepository(store, registry, enabled)
    }

    @Test fun exportsStableCompleteArchiveAndReleasesIt() = runTest {
        val repository = repository()
        val first = repository.export("demo")
        val second = repository.export("demo")
        assertArrayEquals(File(first).readBytes(), File(second).readBytes())
        ZipFile(first).use { zip ->
            assertNotNull(zip.getEntry("SKILL.md"))
            assertNotNull(zip.getEntry("assets/template.txt"))
            assertEquals(0L, zip.getEntry("SKILL.md").time)
        }
        repository.release(first)
        repository.release(second)
        assertFalse(File(first).exists())
    }

    @Test fun disabledSkillCannotBeExported() = runTest {
        val repository = repository()
        disabled = setOf("demo")
        try {
            repository.export("demo")
            fail("Expected disabled skill rejection")
        } catch (_: IllegalStateException) { }
    }
}
