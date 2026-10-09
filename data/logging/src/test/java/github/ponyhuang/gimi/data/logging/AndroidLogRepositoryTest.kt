package github.ponyhuang.gimi.data.logging

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.runTest

class AndroidLogRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()

    @Before fun setUp() {
        every { context.noBackupFilesDir } returns temporary.newFolder()
        every { context.contentResolver } returns resolver
        mockkStatic(Uri::class)
        every { Uri.parse("content://logs/document") } returns uri
    }

    @After fun tearDown() { unmockkStatic(Uri::class) }

    @Test fun `reads persisted logs and exports UTF8 to selected document`() = runTest {
        val repository = AndroidLogRepository(context)
        val output = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns output
        repository.store.append("中文日志")
        assertEquals("中文日志\n", repository.read().content)
        repository.export("content://logs/document")
        assertEquals("中文日志\n", output.toString("UTF-8"))
    }

    @Test fun `missing document stream reports failure without losing logs`() = runTest {
        val repository = AndroidLogRepository(context)
        repository.store.append("retained")
        every { resolver.openOutputStream(uri, "wt") } returns null
        try {
            repository.export("content://logs/document")
            fail("Expected IOException")
        } catch (_: IOException) { }
        assertEquals("retained\n", repository.read().content)
    }

    @Test fun `revoked document permission propagates to presentation`() = runTest {
        val repository = AndroidLogRepository(context)
        every { resolver.openOutputStream(uri, "wt") } throws SecurityException("revoked")
        try {
            repository.export("content://logs/document")
            fail("Expected SecurityException")
        } catch (_: SecurityException) { }
    }
}
