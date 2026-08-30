package github.ponyhuang.gimi.data.workfiles.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentDirectoryRepositoryTest {
    private val treeUri = mockk<Uri>(relaxed = true) {
        every { lastPathSegment } returns "primary:Documents"
        every { authority } returns "com.android.externalstorage.documents"
    }
    private val childUri = mockk<Uri>(relaxed = true)
    private val treeUriString = treeUri.toString()
    private val childUriString = childUri.toString()

    @Before
    fun setUpAndroidStatics() {
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
        every { Uri.parse(treeUriString) } returns treeUri
        every { Uri.parse(childUriString) } returns childUri
        every { DocumentsContract.isTreeUri(treeUri) } returns true
        every { DocumentsContract.isTreeUri(childUri) } returns false
    }

    @After
    fun tearDownAndroidStatics() {
        unmockkStatic(Uri::class)
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun addDirectoryPersistsGrantAndMapsDomainDirectory() = runTest {
        val fixture = fixture()

        val result = fixture.repository.addDirectory(treeUriString)

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertEquals(
            listOf(
                github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory(
                    uri = treeUriString,
                    displayName = "primary:Documents",
                    authority = "com.android.externalstorage.documents",
                ),
            ),
            fixture.repository.currentDirectories(),
        )
        assertEquals(setOf(treeUriString), fixture.preferences.values[DIRECTORIES_KEY])
        verify {
            fixture.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Test
    fun addDirectoryRejectsNonTreeUriWithoutPersistingPermission() = runTest {
        val fixture = fixture()

        val result = fixture.repository.addDirectory(childUriString)

        assertEquals(WorkDirectoryOperationResult.Failure.InvalidDirectory, result)
        assertTrue(fixture.repository.currentDirectories().isEmpty())
        verify(exactly = 0) { fixture.contentResolver.takePersistableUriPermission(any(), any()) }
    }

    @Test
    fun addDirectoryMapsSecurityFailureToPermissionDenied() = runTest {
        val fixture = fixture()
        every { fixture.contentResolver.takePersistableUriPermission(treeUri, any()) } throws
            SecurityException("grant denied")

        val result = fixture.repository.addDirectory(treeUriString)

        assertEquals(WorkDirectoryOperationResult.Failure.PermissionDenied, result)
        assertTrue(fixture.repository.currentDirectories().isEmpty())
    }

    @Test
    fun removeDirectoryStillRemovesLocalEntryAfterPlatformGrantWasRevoked() = runTest {
        val fixture = fixture(setOf(treeUriString))
        every { fixture.contentResolver.releasePersistableUriPermission(treeUri, any()) } throws
            SecurityException("already revoked")

        val result = fixture.repository.removeDirectory(treeUriString)

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertTrue(fixture.repository.currentDirectories().isEmpty())
        assertEquals(emptySet<String>(), fixture.preferences.values[DIRECTORIES_KEY])
    }

    @Test
    fun containsDelegatesMembershipCheckToDocumentsProvider() {
        val fixture = fixture(setOf(treeUriString))
        every {
            DocumentsContract.isChildDocument(fixture.contentResolver, treeUri, childUri)
        } returns true

        assertTrue(fixture.repository.contains(childUriString))

        every {
            DocumentsContract.isChildDocument(fixture.contentResolver, treeUri, childUri)
        } throws IllegalArgumentException("provider failure")
        assertFalse(fixture.repository.contains(childUriString))
    }

    private fun fixture(initialUris: Set<String> = emptySet()): Fixture {
        val preferences = FakePreferences(initialUris)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences.delegate
            every { this@mockk.contentResolver } returns contentResolver
        }
        return Fixture(
            repository = DocumentDirectoryRepository(context),
            preferences = preferences,
            contentResolver = contentResolver,
        )
    }

    /** Test dependencies and observable persistence state for one repository instance. */
    private data class Fixture(
        val repository: DocumentDirectoryRepository,
        val preferences: FakePreferences,
        val contentResolver: ContentResolver,
    )

    private class FakePreferences(initialUris: Set<String>) {
        val values = mutableMapOf<String, Any?>(DIRECTORIES_KEY to initialUris)
        private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val delegate = mockk<SharedPreferences>()

        init {
            every { delegate.getStringSet(any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                (values[firstArg()] as? Set<String>)?.toMutableSet() ?: secondArg()
            }
            every { delegate.edit() } returns editor
            every { editor.putStringSet(any(), any()) } answers {
                values[firstArg()] = (secondArg<Any?>() as? Set<*>)
                    ?.filterIsInstance<String>()
                    ?.toSet()
                editor
            }
            every { editor.apply() } returns Unit
        }
    }

    private companion object {
        const val DIRECTORIES_KEY = "tree_uris"
    }
}
