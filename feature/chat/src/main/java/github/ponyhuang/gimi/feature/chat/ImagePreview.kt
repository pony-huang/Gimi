package github.ponyhuang.gimi.feature.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlin.math.max

/** Current zoom and pan values for the full-screen image preview. */
internal data class ImagePreviewTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/** Applies a zoom/pan delta while keeping the image reachable inside the preview viewport. */
internal fun updateImagePreviewTransform(
    current: ImagePreviewTransform,
    zoomChange: Float,
    panChange: Offset,
    viewportSize: Size,
): ImagePreviewTransform {
    val scale = (current.scale * zoomChange).coerceIn(MinPreviewScale, MaxPreviewScale)
    if (scale == MinPreviewScale) return ImagePreviewTransform()
    val maxX = viewportSize.width * (scale - 1f) / 2f
    val maxY = viewportSize.height * (scale - 1f) / 2f
    return ImagePreviewTransform(
        scale = scale,
        offset = Offset(
            x = (current.offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (current.offset.y + panChange.y).coerceIn(-maxY, maxY),
        ),
    )
}

/** Full-screen image preview shared by sent attachments and local search results. */
@Composable
internal fun ZoomableImagePreviewDialog(
    imageKey: Any,
    contentDescription: String,
    loadBitmap: suspend (targetSize: Int) -> Bitmap?,
    onDismiss: () -> Unit,
) {
    ZoomableImagePreviewFrame(
        imageKey = imageKey,
        onDismiss = onDismiss,
    ) { imageModifier, targetSize ->
        var bitmap by remember(imageKey, targetSize) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(imageKey, targetSize) {
            bitmap = loadBitmap(targetSize)
        }
        DisposableEffect(bitmap) {
            val managedBitmap = bitmap
            onDispose { managedBitmap?.recycle() }
        }

        bitmap?.let { currentBitmap ->
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Full-screen zoomable preview for anything Coil can load — a remote URL, or a persisted
 * attachment addressed by [java.io.File] or by its raw bytes.
 */
@Composable
internal fun ZoomableCoilImagePreviewDialog(
    model: Any?,
    imageKey: Any,
    contentDescription: String,
    onDismiss: () -> Unit,
) {
    ZoomableImagePreviewFrame(
        imageKey = imageKey,
        onDismiss = onDismiss,
    ) { imageModifier, _ ->
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ZoomableImagePreviewFrame(
    imageKey: Any,
    onDismiss: () -> Unit,
    image: @Composable (modifier: Modifier, targetSize: Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .testTag("chat_image_preview"),
        ) {
            val density = LocalDensity.current
            val viewportSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val targetSize = max(viewportSize.width, viewportSize.height).toInt()
            var transform by remember(imageKey) { mutableStateOf(ImagePreviewTransform()) }
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                transform = updateImagePreviewTransform(
                    current = transform,
                    zoomChange = zoomChange,
                    panChange = panChange,
                    viewportSize = viewportSize,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageKey) {
                        detectTapGestures(
                            onDoubleTap = {
                                transform = if (transform.scale > 1f) {
                                    ImagePreviewTransform()
                                } else {
                                    ImagePreviewTransform(scale = 2f)
                                }
                            },
                        )
                    }
                    .transformable(transformableState),
                contentAlignment = Alignment.Center,
            ) {
                image(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = transform.scale
                            scaleY = transform.scale
                            translationX = transform.offset.x
                            translationY = transform.offset.y
                        },
                    targetSize,
                )
            }

            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                onClick = onDismiss,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.chat_attachment_close_preview),
                )
            }
        }
    }
}

private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 5f

@Preview(showBackground = true)
@Composable
private fun ZoomableImagePreviewDialogPreview() {
    AsssistantaiTheme {
        ZoomableImagePreviewDialog(
            imageKey = "preview-bitmap",
            contentDescription = "预览图片",
            loadBitmap = { Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888) },
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ZoomableCoilImagePreviewDialogPreview() {
    AsssistantaiTheme {
        ZoomableCoilImagePreviewDialog(
            model = "https://example.com/sample-image.png",
            imageKey = "https://example.com/sample-image.png",
            contentDescription = "预览远程图片",
            onDismiss = {},
        )
    }
}
