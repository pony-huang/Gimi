package github.ponyhuang.asssistantai.feature.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

internal fun decodeSampledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    targetSize: Int,
): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
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
    return rotateBitmap(bitmap, orientation)
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
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it != bitmap) bitmap.recycle()
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
    val safeReqWidth = reqWidth.coerceAtLeast(1)
    val safeReqHeight = reqHeight.coerceAtLeast(1)
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > safeReqHeight || width > safeReqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (
            halfHeight / inSampleSize >= safeReqHeight &&
            halfWidth / inSampleSize >= safeReqWidth
        ) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
