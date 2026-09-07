package github.ponyhuang.gimi.data.workfiles.repository

import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkDirectoryConfigStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replaceEmitsOrderedStructuredDirectories() = runTest {
        val file = File(temporaryFolder.root, "work-directories.json")
        val store = store(file, backgroundScope)
        val expected = listOf(directory("first", 1L), directory("second", 2L, enabled = false))

        store.replace(expected)

        store.directories.test {
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun serializerRoundTripsStructuredConfigurationInOrder() = runTest {
        val expected = StoredWorkDirectoryConfig(
            directories = listOf(
                StoredWorkDirectory.fromDomain(directory("first", 1L)),
                StoredWorkDirectory.fromDomain(directory("second", 2L, enabled = false)),
            ),
        )
        val output = ByteArrayOutputStream()

        WorkDirectoryConfigSerializer.writeTo(expected, output)

        assertEquals(
            expected,
            WorkDirectoryConfigSerializer.readFrom(ByteArrayInputStream(output.toByteArray())),
        )
    }

    @Test
    fun missingFileStartsWithEmptyConfiguration() = runTest {
        val store = store(File(temporaryFolder.root, "missing.json"), backgroundScope)

        store.directories.test {
            assertEquals(emptyList<WorkDirectory>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun store(file: File, scope: CoroutineScope) = WorkDirectoryConfigStore(
        DataStoreFactory.create(
            serializer = WorkDirectoryConfigSerializer,
            scope = scope,
            produceFile = { file },
        ),
    )

    private fun directory(id: String, addedAt: Long, enabled: Boolean = true) = WorkDirectory(
        id = id,
        treeUri = "content://documents/tree/$id",
        displayName = id,
        authority = "documents",
        enabled = enabled,
        accessStatus = WorkDirectoryAccessStatus.AVAILABLE,
        addedAtEpochMillis = addedAt,
    )
}
