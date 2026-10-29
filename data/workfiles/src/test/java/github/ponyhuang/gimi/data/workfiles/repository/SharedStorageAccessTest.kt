package github.ponyhuang.gimi.data.workfiles.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 保留此回归覆盖：URI 不能越权读取、读取时必须重新校验授权。 */
class SharedStorageAccessTest {
    @get:Rule
    val temporary = TemporaryFolder(File("../../temp/workfiles-tests").apply { mkdirs() })
    private val context = mockk<Context>()
    private lateinit var access: SharedStorageAccess
    private lateinit var root: File
    private lateinit var document: File

    @Before
    fun setUp() {
        mockkStatic(Environment::class, Uri::class)
        root = temporary.newFolder("volume")
        document = File(root, "plan.txt").apply { writeText("plan") }
        val volume = mockk<StorageVolume> {
            every { state } returns Environment.MEDIA_MOUNTED
            every { directory } returns root
        }
        val manager = mockk<StorageManager> {
            every { storageVolumes } returns listOf(volume)
        }
        every { context.packageName } returns "test.app"
        every { context.getSystemService(StorageManager::class.java) } returns manager
        every { Environment.isExternalStorageManager() } returns true
        access = SharedStorageAccess(context)
    }

    @After
    fun tearDown() = unmockkStatic(Environment::class, Uri::class)

    @Test
    fun validUriStopsWorkingImmediatelyAfterPermissionRevocation() {
        uri(listOf("file", document.path))
        assertEquals(document.canonicalFile, access.fileForUri("uri"))
        every { Environment.isExternalStorageManager() } returns false
        assertNull(access.fileForUri("uri"))
    }

    @Test
    fun rejectsWrongAuthorityRawPathsDirectoriesAndPrivateFiles() {
        uri(listOf("file", document.path), authority = "other.app.sharedfiles")
        assertNull(access.fileForUri("uri"))
        uri(listOf("file", document.path), scheme = "file")
        assertNull(access.fileForUri("uri"))
        uri(listOf("file", root.path))
        assertNull(access.fileForUri("uri"))
        val secret = File(root, "Android/data/other.app/secret.txt").apply {
            parentFile!!.mkdirs()
            writeText("secret")
        }
        uri(listOf("file", secret.path))
        assertNull(access.fileForUri("uri"))
    }

    private fun uri(segments: List<String>, authority: String = "test.app.sharedfiles", scheme: String = "content") {
        val parsed = mockk<Uri> {
            every { this@mockk.scheme } returns scheme
            every { this@mockk.authority } returns authority
            every { pathSegments } returns segments
            every { query } returns null
            every { fragment } returns null
        }
        every { Uri.parse("uri") } returns parsed
    }
}
