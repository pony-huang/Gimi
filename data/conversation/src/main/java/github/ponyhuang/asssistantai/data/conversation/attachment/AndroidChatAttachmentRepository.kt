package github.ponyhuang.asssistantai.data.conversation.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.conversation.model.ImageAttachment
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAttachmentRepository
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidChatAttachmentRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ChatAttachmentRepository {
    private val resolver = context.contentResolver

    override suspend fun read(references: List<String>): List<ImageAttachment> =
        withContext(Dispatchers.IO) {
            references.map { reference -> prepare(resolver, Uri.parse(reference)) }
        }

    private fun prepare(contentResolver: ContentResolver, uri: Uri): ImageAttachment {
        var bitmap = decode(contentResolver, uri)
            ?: throw IllegalArgumentException("The selected image could not be decoded")
        try {
            repeat(MAX_RESIZE_ATTEMPTS) {
                compress(bitmap)?.let { bytes ->
                    return ImageAttachment(mimeType = "image/jpeg", data = bytes)
                }
                val width = (bitmap.width * 3 / 4).coerceAtLeast(1)
                val height = (bitmap.height * 3 / 4).coerceAtLeast(1)
                if (width == bitmap.width && height == bitmap.height) return@repeat
                val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (resized != bitmap) {
                    bitmap.recycle()
                    bitmap = resized
                }
            }
        } finally {
            bitmap.recycle()
        }
        throw IllegalArgumentException("The selected image is too large after compression")
    }

    private fun decode(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.inSampleSize = sampleSize(options, MAX_DIMENSION_PX, MAX_DIMENSION_PX)
        options.inJustDecodeBounds = false
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return rotate(bitmap, orientation)
    }

    private fun compress(bitmap: Bitmap): ByteArray? {
        for (quality in INITIAL_JPEG_QUALITY downTo MIN_JPEG_QUALITY step JPEG_QUALITY_STEP) {
            val output = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) &&
                output.size() <= MAX_BYTES
            ) {
                return output.toByteArray()
            }
        }
        return null
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }

    private fun sampleSize(options: BitmapFactory.Options, width: Int, height: Int): Int {
        val sourceHeight = options.outHeight
        val sourceWidth = options.outWidth
        var result = 1
        if (sourceHeight > height || sourceWidth > width) {
            val halfHeight = sourceHeight / 2
            val halfWidth = sourceWidth / 2
            while (halfHeight / result >= height && halfWidth / result >= width) result *= 2
        }
        return result
    }

    private companion object {
        const val MAX_DIMENSION_PX = 1280
        const val MAX_BYTES = 512 * 1024
        const val INITIAL_JPEG_QUALITY = 85
        const val MIN_JPEG_QUALITY = 45
        const val JPEG_QUALITY_STEP = 10
        const val MAX_RESIZE_ATTEMPTS = 4
    }
}
