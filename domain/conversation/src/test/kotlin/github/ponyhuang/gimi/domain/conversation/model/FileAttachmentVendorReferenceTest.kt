package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 厂商 file 引用附件：工厂、私有 URI 约定与序列化兼容性。 */
class FileAttachmentVendorReferenceTest {

    @Test
    fun vendorReferenceRoundTripsThroughUriHelpers() {
        val uri = VendorFileReference.uri("deepseek", "file-1")
        assertEquals("gimi-file://deepseek/file-1", uri)
        assertEquals("deepseek" to "file-1", VendorFileReference.parse(uri))
    }

    @Test
    fun parseRejectsForeignSchemesAndIncompleteReferences() {
        assertNull(VendorFileReference.parse("file:///tmp/a.pdf"))
        assertNull(VendorFileReference.parse("https://example.com/a.pdf"))
        assertNull(VendorFileReference.parse("gimi-file://deepseek/"))
        assertNull(VendorFileReference.parse("gimi-file:///file-1"))
    }

    @Test
    fun fromVendorReferenceProducesFileIdOnlyAttachment() {
        val attachment = FileAttachment.fromVendorReference(
            vendor = "deepseek",
            fileId = "file-1",
            mimeType = "application/pdf",
            displayName = "doc.pdf",
            bytes = 1024,
        )
        assertNull(attachment.payloadReference)
        assertNull(attachment.inlineData)
        assertEquals("file-1", attachment.fileId)
        assertEquals("deepseek", attachment.fileVendor)
        assertEquals(1024, attachment.sizeBytes)
    }

    @Test
    fun legacyJsonWithoutFileIdFieldsStillDecodes() {
        val legacy = """
            {"mimeType":"application/pdf","id":"a","sizeBytes":1,
             "displayName":"doc.pdf","payloadReference":"/tmp/doc.pdf"}
        """.trimIndent()
        val attachment = Json.decodeFromString<FileAttachment>(legacy)
        assertNull(attachment.fileId)
        assertNull(attachment.fileVendor)
    }

    @Test
    fun fileIdOnlyAttachmentSerializesRoundTrip() {
        val attachment = FileAttachment.fromVendorReference(
            vendor = "deepseek",
            fileId = "file-1",
            mimeType = "application/pdf",
            displayName = "doc.pdf",
        )
        val json = Json.encodeToString(FileAttachment.serializer(), attachment)
        val restored = Json.decodeFromString(FileAttachment.serializer(), json)
        assertEquals(attachment, restored)
    }

    @Test
    fun attachmentWithoutAnyPayloadIsRejected() {
        val failure = runCatching {
            FileAttachment(mimeType = "application/pdf", id = "a", sizeBytes = 1, displayName = "x.pdf")
        }
        assertTrue(failure.isFailure)
    }
}
