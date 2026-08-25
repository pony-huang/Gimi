package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftAttachmentStorageTest {

    @Test
    fun importsFileUriWithOriginalNameAndMimeType() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "shared-photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val attachment = try {
            importDraftAttachment(context, Uri.fromFile(source))
        } finally {
            source.delete()
        }

        assertEquals("shared-photo.jpg", attachment.displayName)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals(AttachmentCategory.IMAGE, attachment.category)
        assertEquals(4L, attachment.sizeBytes)

        val draftFile = File(attachment.reference)
        try {
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), draftFile.readBytes().toList())
        } finally {
            draftFile.delete()
        }
    }
}
