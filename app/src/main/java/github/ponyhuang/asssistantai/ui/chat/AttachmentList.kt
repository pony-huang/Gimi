package github.ponyhuang.asssistantai.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import github.ponyhuang.asssistantai.R
import github.ponyhuang.asssistantai.model.ImageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays a horizontal scrollable list of image attachments with remove functionality.
 *
 * @param uris The ordered [List] of [Uri]s representing the image attachments to display.
 * @param onRemoveAttachment Callback invoked when the user taps the remove button on an attachment,
 * providing the [Uri] of the attachment to be removed.
 * @param modifier Optional [Modifier] for customizing the layout of the list.
 */
@Composable
internal fun AttachmentList(
    uris: List<Uri>,
    onRemoveAttachment: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = uris,
            key = Uri::toString,
        ) { uri ->
            SelectedAttachment(
                modifier = Modifier.animateItem(),
                uri = uri,
                onRemove = { onRemoveAttachment(uri) },
            )
        }
    }
}

/** Renders persisted user-message images from their ADK inline-data bytes. */
@Composable
internal fun MessageImageAttachments(
    images: List<ImageAttachment>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    var previewImage by remember { mutableStateOf<ImageAttachment?>(null) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            InlineImage(
                image = image,
                onClick = { previewImage = image },
            )
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            image = image,
            onDismiss = { previewImage = null },
        )
    }
}

/** Displays a sent image at a screen-appropriate resolution. */
@Composable
private fun ImagePreviewDialog(
    image: ImageAttachment,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim),
        ) {
            val density = LocalDensity.current
            val targetSize = with(density) {
                maxOf(maxWidth.toPx(), maxHeight.toPx()).toInt()
            }
            var bitmap by remember(image.data, targetSize) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(image.data, targetSize) {
                bitmap = withContext(Dispatchers.Default) {
                    decodeSampledBitmap(image.data, targetSize = targetSize)
                }
            }

            bitmap?.let { currentBitmap ->
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Sent image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
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
                    contentDescription = "Close image preview",
                )
            }
        }
    }
}

@Composable
private fun InlineImage(
    image: ImageAttachment,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.data) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image.data) {
        bitmap = withContext(Dispatchers.Default) {
            decodeSampledBitmap(image.data, targetSize = 240)
        }
    }
    AttachmentTile(
        modifier = Modifier.clickable(onClick = onClick),
        size = 88.dp,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Sent image",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceDim),
            )
        }
    }
}

/**
 * Displays a single selected attachment as a thumbnail image with a remove button overlay.
 */
@Composable
private fun SelectedAttachment(
    uri: Uri,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit = {},
) {
    AttachmentTile(
        modifier = modifier,
    ) {
        UriImage(
            uri = uri,
            modifier = Modifier.matchParentSize(),
            placeholder = {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceDim),
                )
            },
            error = {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceDim),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.stream_ai_compose_ic_image_placeholder),
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        contentDescription = null,
                    )
                }
            },
        )
        RemoveButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            onClick = onRemove,
        )
    }
}

/**
 * A container composable that provides a rounded square tile for displaying attachment content.
 */
@Composable
private fun AttachmentTile(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
    ) {
        content()
    }
}

/**
 * Loads and displays an image from a content [Uri] with support for placeholder and error states.
 *
 * The image is loaded asynchronously on a background thread and downsampled to the target size
 * for memory efficiency.
 *
 * @param uri The content [Uri] of the image to load and display.
 * @param modifier Optional [Modifier] for customizing the image layout.
 * @param contentScale The [ContentScale] to apply when rendering the image. Defaults to [ContentScale.Crop].
 * @param placeholder Composable to display while the image is loading.
 * @param error Composable to display if the image fails to load.
 */
@Composable
private fun UriImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { },
    error: @Composable () -> Unit = { },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = with(density) { 100.dp.toPx().toInt() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        isLoading = true
        hasError = false
        withContext(Dispatchers.IO) {
            try {
                bitmap = decodeSampledBitmap(context.contentResolver, uri, targetSizePx)
                hasError = bitmap == null
            } catch (_: Exception) {
                hasError = true
            }
        }
        isLoading = false
    }

    when {
        isLoading -> placeholder()

        hasError -> error()

        bitmap != null -> Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/**
 * A circular button with a remove icon, typically used as an overlay on attachments.
 *
 * @param modifier [Modifier] for positioning and sizing the button.
 * @param onClick Callback invoked when the button is clicked.
 */
@Composable
private fun RemoveButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilledIconButton(
        modifier = modifier.size(22.dp),
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.stream_ai_compose_ic_cancel),
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            contentDescription = "Remove attachment",
        )
    }
}

@Preview
@Composable
private fun SelectedAttachmentPreview() {
    SelectedAttachment(uri = "1".toUri())
}
