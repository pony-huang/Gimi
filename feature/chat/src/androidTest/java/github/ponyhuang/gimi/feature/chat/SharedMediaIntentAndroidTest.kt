package github.ponyhuang.gimi.feature.chat

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedMediaIntentAndroidTest {

    @Test
    fun actionSendReturnsSingleImageStream() {
        val image = Uri.parse("content://images/photo.jpg")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, image)

        assertEquals(listOf(image), sharedImageUris(intent))
    }

    @Test
    fun actionSendMultipleCollectsClipDataAndDeduplicates() {
        val first = Uri.parse("content://images/first.jpg")
        val second = Uri.parse("content://images/second.jpg")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/*")
            .apply {
                clipData = ClipData.newRawUri(null, first).apply {
                    addItem(ClipData.Item(second))
                    addItem(ClipData.Item(first))
                }
                putExtra(Intent.EXTRA_STREAM, second)
            }

        assertEquals(listOf(first, second), sharedImageUris(intent))
    }

    @Test
    fun nonImageShareReturnsEmpty() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "not an image")

        assertEquals(emptyList<Uri>(), sharedImageUris(intent))
    }

    @Test
    fun nonShareActionReturnsEmpty() {
        val intent = Intent(Intent.ACTION_VIEW).setType("image/jpeg")

        assertEquals(emptyList<Uri>(), sharedImageUris(intent))
    }
}
