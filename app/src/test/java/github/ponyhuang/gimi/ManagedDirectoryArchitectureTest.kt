package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedDirectoryArchitectureTest {
    @Test
    fun productionModulesDoNotBuildAppSpecificDirectoriesFromContext() {
        val violations = productionKotlinFiles()
            .filterNot { it.inCoreStorage() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (FORBIDDEN_CONTEXT_DIRECTORIES.any(line::contains)) {
                        "${file.invariantSeparatorsPath}:${index + 1}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertEquals("Use a ManagedDirectorySpec and AppDirectoryResolver", emptyList<String>(), violations)
    }

    @Test
    fun fileProviderCallsAreCentralizedAndXmlExposesOnlyShareableCache() {
        val directCalls = productionKotlinFiles()
            .filterNot { it.inCoreStorage() }
            .filter { it.readText().contains("FileProvider.getUriForFile") }
            .map(File::getPath)
            .toList()
        assertEquals("Use ShareableFileUriFactory", emptyList<String>(), directCalls)

        val filePaths = File("src/main/res/xml/file_paths.xml")
        assertTrue(filePaths.isFile)
        val content = filePaths.readText()
        assertEquals(1, Regex("<cache-path").findAll(content).count())
        assertTrue(content.contains("path=\"shareable/\""))
    }

    private fun productionKotlinFiles(): Sequence<File> {
        val root = File("..").canonicalFile
        return sequenceOf("app", "core", "data", "feature")
            .map { File(root, it) }
            .filter(File::isDirectory)
            .flatMap(File::walkTopDown)
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    "/src/main/" in file.invariantSeparatorsPath
            }
    }

    private fun File.inCoreStorage(): Boolean =
        "/core/storage/" in invariantSeparatorsPath

    private companion object {
        val FORBIDDEN_CONTEXT_DIRECTORIES = listOf(
            ".filesDir",
            ".cacheDir",
            ".codeCacheDir",
            ".externalCacheDir",
            ".getExternalFilesDir(",
        )
    }
}
