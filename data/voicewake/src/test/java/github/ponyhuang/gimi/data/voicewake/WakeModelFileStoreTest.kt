package github.ponyhuang.gimi.data.voicewake

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WakeModelFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removingInstalledModelDeletesItsCompleteDirectory() {
        val modelId = "installed-model"
        val modelDir = temporaryFolder.newFolder(modelId)
        File(modelDir, "am/final.mdl").apply {
            parentFile?.mkdirs()
            writeText("model")
        }
        val store = WakeModelFileStore(temporaryFolder.root)

        val removed = store.remove(modelId)

        assertTrue(removed)
        assertFalse(modelDir.exists())
    }

    @Test
    fun removingMissingModelIsIdempotent() {
        val store = WakeModelFileStore(temporaryFolder.root)

        assertTrue(store.remove("missing-model"))
    }

    @Test
    fun failedDirectoryDeletionIsReported() {
        val modelId = "undeletable-model"
        val modelDir = temporaryFolder.newFolder(modelId)
        val store = WakeModelFileStore(
            rootDir = temporaryFolder.root,
            deleteDirectory = { false },
        )

        assertFalse(store.remove(modelId))
        assertTrue(modelDir.exists())
    }
}
