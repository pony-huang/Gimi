package github.ponyhuang.asssistantai.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import github.ponyhuang.asssistantai.model.ImageAttachment
import java.io.ByteArrayOutputStream

private const val ATTACHMENT_MAX_DIMENSION_PX = 1280
private const val ATTACHMENT_MAX_BYTES = 512 * 1024
private const val INITIAL_JPEG_QUALITY = 85
private const val MIN_JPEG_QUALITY = 45
private const val JPEG_QUALITY_STEP = 10
private const val MAX_RESIZE_ATTEMPTS = 4

/**
 * Decodes a bitmap from a URI, downsampling it to approximately the target size
 * and applying the correct EXIF orientation.
 * This prevents loading huge images into memory when only a thumbnail is needed.
 *
 * @param contentResolver The content resolver to use for opening the URI
 * @param uri The URI of the image to decode
 * @param targetSize The target size in pixels for the decoded bitmap
 * @return The decoded and correctly oriented bitmap, or null if decoding failed
 */
internal fun decodeSampledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    targetSize: Int,
): Bitmap? {
    // First, decode just the bounds to get the image dimensions
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, options)
    }

    // Calculate the sample size
    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
    options.inJustDecodeBounds = false

    // Decode the bitmap with the calculated sample size
    val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, options)
    } ?: return null

    // Read EXIF orientation and rotate if needed
    val orientation = contentResolver.openInputStream(uri)?.use { inputStream ->
        ExifInterface(inputStream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    return rotateBitmap(bitmap, orientation)
}

/**
 * Prepares an image for a chat event.
 *
 * ADK persists inline message data in Room. Keeping the encoded attachment bounded avoids a
 * single StorageEvent row exceeding Android's CursorWindow capacity when the conversation is
 * restored. The normalized JPEG is also the exact image shown in restored message history and
 * sent to the model.
 */
internal fun prepareImageAttachment(
    contentResolver: ContentResolver,
    uri: Uri,
): ImageAttachment {
    var bitmap = decodeSampledBitmap(
        contentResolver = contentResolver,
        uri = uri,
        targetSize = ATTACHMENT_MAX_DIMENSION_PX,
    ) ?: throw IllegalArgumentException("The selected image could not be decoded")

    try {
        repeat(MAX_RESIZE_ATTEMPTS) {
            compressAsBoundedJpeg(bitmap)?.let { bytes ->
                return ImageAttachment(mimeType = "image/jpeg", data = bytes)
            }

            val nextWidth = (bitmap.width * 3 / 4).coerceAtLeast(1)
            val nextHeight = (bitmap.height * 3 / 4).coerceAtLeast(1)
            if (nextWidth == bitmap.width && nextHeight == bitmap.height) return@repeat

            val resized = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
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

private fun compressAsBoundedJpeg(bitmap: Bitmap): ByteArray? {
    for (quality in INITIAL_JPEG_QUALITY downTo MIN_JPEG_QUALITY step JPEG_QUALITY_STEP) {
        val output = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        val bytes = output.toByteArray()
        if (compressed && bytes.size <= ATTACHMENT_MAX_BYTES) return bytes
    }
    return null
}

/** Decodes in-memory image bytes into a thumbnail without retaining the full bitmap. */
internal fun decodeSampledBitmap(
    data: ByteArray,
    targetSize: Int,
): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

/**
 * Rotates a bitmap according to the EXIF orientation value.
 *
 * @param bitmap The bitmap to rotate
 * @param orientation The EXIF orientation value
 * @return The rotated bitmap, or the original if no rotation was needed
 */
@Suppress("MagicNumber")
private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
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

        else -> return bitmap // No rotation needed
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it != bitmap) {
            bitmap.recycle()
        }
    }
}

/**
 * Calculates the largest inSampleSize value that is a power of 2 and keeps both
 * height and width larger than the requested height and width.
 *
 * @param options The BitmapFactory.Options containing the image dimensions
 * @param reqWidth The requested width
 * @param reqHeight The requested height
 * @return The calculated sample size
 */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
