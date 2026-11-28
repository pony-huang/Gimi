package github.ponyhuang.gimi.data.workspace

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import github.ponyhuang.gimi.domain.workspace.WorkspaceDocumentsContract
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkspaceDocumentsProviderTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var provider: WorkspaceDocumentsProvider

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        root = File(context.filesDir, "workspace").apply {
            deleteRecursively()
            mkdirs()
        }
        val appContext = context
        provider = WorkspaceDocumentsProvider().apply {
            attachInfo(
                appContext,
                ProviderInfo().apply {
                    authority = WorkspaceDocumentsContract.authority(appContext.packageName)
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                },
            )
        }
    }

    @Test
    fun exposesWorkspaceRootWithoutCreateFlag() {
        provider.queryRoots(null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                WorkspaceDocumentsContract.ROOT_ID,
                cursor.getString(cursor.getColumnIndexOrThrow(Root.COLUMN_ROOT_ID)),
            )
            assertEquals(
                WorkspaceDocumentsContract.ROOT_DOCUMENT_ID,
                cursor.getString(cursor.getColumnIndexOrThrow(Root.COLUMN_DOCUMENT_ID)),
            )
            val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Root.COLUMN_FLAGS))
            assertEquals(0, flags and Root.FLAG_SUPPORTS_CREATE)
        }
    }

    @Test
    fun listsFilesNewestFirstHidesTmpStagingAndMarksFilesDeletable() {
        File(root, "b-older.txt").apply { writeText("old") }.setLastModified(1_000L)
        File(root, "a-newer.md").apply { writeText("new") }.setLastModified(2_000L)
        File(root, "staging.tmp").apply { writeText("tmp") }.setLastModified(3_000L)

        provider.queryChildDocuments("root:", null, "").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("root:a-newer.md", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
            assertEquals("text/plain", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
            assertEquals(
                Document.FLAG_SUPPORTS_DELETE,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS)),
            )
            assertTrue(cursor.moveToNext())
            assertEquals("root:b-older.txt", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
        }

        assertThrows(FileNotFoundException::class.java) {
            provider.openDocument("root:staging.tmp", "r", null)
        }
    }

    @Test
    fun readDescriptorReadsSourceAndRejectsWriteModes() {
        File(root, "report.txt").writeText("cached content")
        val descriptor = provider.openDocument("root:report.txt", "r", null)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            assertEquals("cached content", it.readText())
        }

        listOf("w", "wt", "wa", "rw", "rwt").forEach { mode ->
            assertThrows(UnsupportedOperationException::class.java) {
                provider.openDocument("root:report.txt", mode, null)
            }
        }
    }

    @Test
    fun deleteDocumentRemovesFileInsideWorkspaceAndProtectsRoot() {
        val file = File(root, "resume.txt").apply { writeText("resume") }

        provider.deleteDocument("root:resume.txt")

        assertFalse(file.exists())
        assertThrows(UnsupportedOperationException::class.java) {
            provider.deleteDocument("root:")
        }
    }

    @Test
    fun traversalAndSiblingPrefixesCannotExposePrivateFiles() {
        File(context.filesDir, "private.txt").writeText("private")
        File(context.filesDir, "workspace-other").apply { mkdirs() }
        File(context.filesDir, "workspace-other/private.txt").writeText("private")

        listOf("root:../private.txt", "root:../workspace-other/private.txt", "unknown", "root:missing").forEach { id ->
            assertThrows(FileNotFoundException::class.java) { provider.openDocument(id, "r", null) }
            assertThrows(FileNotFoundException::class.java) { provider.deleteDocument(id) }
        }
    }

    @Test
    fun childCheckUsesDirectoryBoundaries() {
        File(root, "report.txt").writeText("content")
        assertTrue(provider.isChildDocument("root:", "root:report.txt"))
        assertFalse(provider.isChildDocument("root:", "root:"))
    }

    @Test
    fun cancelledReadDoesNotOpenFile() {
        File(root, "report.txt").writeText("content")
        val signal = CancellationSignal().apply { cancel() }
        assertThrows(OperationCanceledException::class.java) {
            provider.openDocument("root:report.txt", "r", signal)
        }
    }
}
