package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.core.storage.StorageArea
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun optimizedRootUsesCodeCacheNamespace() {
        assertEquals(StorageArea.CODE_CACHE, PluginStorage.OptimizedRoot.area)
        assertEquals("plugin/optimized", PluginStorage.OptimizedRoot.relativePath)
    }

    @Test
    fun packageDirectoryStaysInsideOptimizedRoot() {
        val root = temporaryFolder.newFolder("optimized")

        assertEquals(
            File(root, "github.ponyhuang.plugin").canonicalFile,
            pluginOptimizedDirectory(root, "github.ponyhuang.plugin"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPackageNameThatEscapesOptimizedRoot() {
        pluginOptimizedDirectory(temporaryFolder.newFolder("optimized"), "../../outside")
    }
}
