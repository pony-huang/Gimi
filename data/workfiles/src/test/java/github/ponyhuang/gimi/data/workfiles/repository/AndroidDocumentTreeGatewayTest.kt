package github.ponyhuang.gimi.data.workfiles.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AndroidDocumentTreeGatewayTest {
    @Before
    fun setUpStatics() {
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
    }

    @After
    fun tearDownStatics() {
        unmockkStatic(Uri::class)
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun relationshipUsesDocumentUrisForBothParentChildChecks() {
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context> { every { contentResolver } returns resolver }
        val parentTree = mockk<Uri> { every { authority } returns "documents" }
        val childTree = mockk<Uri> { every { authority } returns "documents" }
        val parentDocument = mockk<Uri>()
        val childDocument = mockk<Uri>()
        every { Uri.parse(PARENT_URI) } returns parentTree
        every { Uri.parse(CHILD_URI) } returns childTree
        every { DocumentsContract.getTreeDocumentId(parentTree) } returns "root"
        every { DocumentsContract.getTreeDocumentId(childTree) } returns "root/child"
        every {
            DocumentsContract.buildDocumentUriUsingTree(parentTree, "root")
        } returns parentDocument
        every {
            DocumentsContract.buildDocumentUriUsingTree(childTree, "root/child")
        } returns childDocument
        every {
            DocumentsContract.isChildDocument(resolver, parentDocument, childDocument)
        } returns true

        val result = AndroidDocumentTreeGateway(context).relationship(PARENT_URI, CHILD_URI)

        assertEquals(DocumentTreeRelationship.OVERLAPPING, result)
    }

    private companion object {
        const val PARENT_URI = "content://documents/tree/root"
        const val CHILD_URI = "content://documents/tree/root%2Fchild"
    }
}
