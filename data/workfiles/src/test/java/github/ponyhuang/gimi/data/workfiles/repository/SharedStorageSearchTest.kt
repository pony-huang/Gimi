package github.ponyhuang.gimi.data.workfiles.repository

import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 保留此回归覆盖：全盘搜索的路径边界、私有目录排除和取消是必要风险边界。 */
class SharedStorageSearchTest {
    @get:Rule
    val temporary = TemporaryFolder(File("../../temp/workfiles-tests").apply { mkdirs() })

    @Test
    fun searchCoversNestedFilesOnMultipleVolumesAndKeepsNewestMatches() = runTest {
        val primary = temporary.newFolder("primary")
        val card = temporary.newFolder("card")
        file(primary, "Download/old-plan.pdf", 1000)
        file(primary, "Documents/nested/PLAN.txt", 3000)
        file(card, "documents/new-plan.md", 5000)
        file(primary, "Documents/unrelated.txt", 9000)
        file(primary, "Android/data/other.app/secret-plan.txt", 10000)
        file(primary, "Android/obb/other.app/secret-plan.obb", 10000)

        val result = newestSharedFiles(listOf(primary, card, primary), "plan", 2)

        assertEquals(listOf("new-plan.md", "PLAN.txt"), result.map { it.name })
    }

    @Test
    fun policyRejectsPrivateDirectoriesRootAndTraversalOutsideVolume() {
        val root = temporary.newFolder("storage")
        val policy = SharedStorageFilePolicy(listOf(root))
        assertNull(policy.resolve(root))
        assertNull(policy.resolve(File(root, "../storage-other/file.txt")))
        assertNull(policy.resolve(File(root, "Download/../../private.txt")))
        for (directory in listOf("data", "obb", "sandbox")) {
            assertNull(policy.resolve(File(root, "Android/$directory/app/secret.txt")))
        }
        val media = file(root, "Android/media/app/photo.jpg", 1000)
        assertEquals(media.canonicalFile, policy.resolve(media))
    }

    @Test
    fun traversalPropagatesCancellation() = runTest {
        val root = temporary.newFolder("storage")
        val cancelled = Job().apply { cancel() }
        var cancellationObserved = false
        try {
            withContext(cancelled) { newestSharedFiles(listOf(root), "plan", 50) }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationObserved = true
        }
        assertTrue(cancellationObserved)
    }

    private fun file(root: File, relative: String, modified: Long): File = File(root, relative).apply {
        parentFile!!.mkdirs()
        writeText("fixture")
        check(setLastModified(modified))
    }
}
