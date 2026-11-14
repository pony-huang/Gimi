package github.ponyhuang.gimi.environment.runtime

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
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
class RuntimeDocumentsProviderTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var provider: RuntimeDocumentsProvider

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        root = File(context.filesDir, "rootfs").apply { mkdirs() }
        File(context.filesDir, "installed").writeText("test")
        File(root, "home/workspace").mkdirs()
        File(root, "home/workspace/report.txt").writeText("exported content")
        provider = RuntimeDocumentsProvider().apply {
            attachInfo(this@RuntimeDocumentsProviderTest.context, ProviderInfo().apply {
                authority = RuntimeDocumentsProvider.AUTHORITY
                exported = true
                grantUriPermissions = true
                readPermission = Manifest.permission.MANAGE_DOCUMENTS
                writePermission = Manifest.permission.MANAGE_DOCUMENTS
            })
        }
    }

    @Test
    fun installedEnvironmentExposesReadOnlyRoot() {
        provider.queryRoots(null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("local", cursor.getString(cursor.getColumnIndexOrThrow(Root.COLUMN_ROOT_ID)))
            val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Root.COLUMN_FLAGS))
            assertEquals(0, flags and Root.FLAG_SUPPORTS_CREATE)
        }
        File(context.filesDir, "installed").delete()
        provider.queryRoots(null).use { assertEquals(0, it.count) }
    }

    @Test
    fun listingReturnsFilesWithStableIdsAndNoMutationFlags() {
        provider.queryChildDocuments("root:home/workspace", null, "").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("root:home/workspace/report.txt", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
            assertEquals("text/plain", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS)))
        }
        provider.queryDocument("root:home", arrayOf(Document.COLUMN_DISPLAY_NAME)).use { cursor ->
            assertEquals(1, cursor.columnCount)
            assertTrue(cursor.moveToFirst())
            assertEquals("home", cursor.getString(0))
        }
    }

    @Test
    fun readDescriptorSupportsExportWithoutChangingSource() {
        val descriptor = provider.openDocument("root:home/workspace/report.txt", "r", null)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            assertEquals("exported content", it.readText())
        }
        assertEquals("exported content", File(root, "home/workspace/report.txt").readText())
    }

    @Test
    fun everyWriteModeIsRejected() {
        listOf("w", "wt", "wa", "rw", "rwt").forEach { mode ->
            assertThrows(UnsupportedOperationException::class.java) {
                provider.openDocument("root:home/workspace/report.txt", mode, null)
            }
        }
        assertEquals("exported content", File(root, "home/workspace/report.txt").readText())
    }

    @Test
    fun destructiveOperationsAndCreationAreRejected() {
        val document = "root:home/workspace/report.txt"
        assertThrows(UnsupportedOperationException::class.java) { provider.deleteDocument(document) }
        assertThrows(UnsupportedOperationException::class.java) { provider.renameDocument(document, "renamed.txt") }
        assertThrows(UnsupportedOperationException::class.java) { provider.moveDocument(document, "root:home/workspace", "root:home") }
        assertThrows(UnsupportedOperationException::class.java) { provider.createDocument("root:home", "text/plain", "new.txt") }
        assertEquals("exported content", File(root, "home/workspace/report.txt").readText())
    }

    @Test
    fun traversalAndSiblingPrefixesCannotExposePrivateFiles() {
        File(context.filesDir, "private.txt").writeText("private")
        File(context.filesDir, "rootfs-other").mkdirs()
        File(context.filesDir, "rootfs-other/private.txt").writeText("private")
        listOf("root:../private.txt", "root:../rootfs-other/private.txt", "unknown", "root:missing").forEach { id ->
            assertThrows(FileNotFoundException::class.java) { provider.openDocument(id, "r", null) }
        }
        assertThrows(FileNotFoundException::class.java) { provider.openDocument("root:home", "r", null) }
    }

    @Test
    fun childCheckUsesDirectoryBoundaries() {
        File(root, "home-other").mkdirs()
        assertTrue(provider.isChildDocument("root:home", "root:home/workspace/report.txt"))
        assertFalse(provider.isChildDocument("root:home", "root:home-other"))
        assertFalse(provider.isChildDocument("root:home", "root:home"))
    }

    @Test
    fun cancelledReadDoesNotOpenFile() {
        val signal = CancellationSignal().apply { cancel() }
        assertThrows(OperationCanceledException::class.java) {
            provider.openDocument("root:home/workspace/report.txt", "r", signal)
        }
    }
}
