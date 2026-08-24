package github.ponyhuang.gimi.feature.chat

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
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
    attachments: List<DraftAttachment>,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = attachments,
            key = DraftAttachment::reference,
        ) { attachment ->
            SelectedAttachment(
                modifier = Modifier.animateItem(),
                attachment = attachment,
                onRemove = { onRemoveAttachment(attachment) },
            )
        }
    }
}

/** Renders persisted user-message images from their ADK inline-data bytes. */
@Composable
internal fun MessageAttachments(
    attachments: List<FileAttachment>,
    onOpenDocument: (FileAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val images = attachments.filter { it.category == AttachmentCategory.IMAGE }
    val files = attachments.filterNot { it.category == AttachmentCategory.IMAGE }
    var previewImage by remember { mutableStateOf<FileAttachment?>(null) }
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
    files.forEach { attachment ->
        PersistedFileAttachment(attachment, onOpenDocument)
    }
}

/** Displays a sent image at a screen-appropriate resolution with zoom and pan gestures. */
@Composable
private fun ImagePreviewDialog(
    image: FileAttachment,
    onDismiss: () -> Unit,
) {
    val description = stringResource(R.string.chat_attachment_sent_image_preview)
    ZoomableImagePreviewDialog(
        imageKey = image.id,
        contentDescription = description,
        loadBitmap = { targetSize ->
            withContext(Dispatchers.Default) {
                decodeSampledBitmap(image.data, targetSize = targetSize)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun InlineImage(
    image: FileAttachment,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.data) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image.data) {
        bitmap = withContext(Dispatchers.Default) {
            decodeSampledBitmap(image.data, targetSize = 240)
        }
    }
    DisposableEffect(bitmap) {
        val managedBitmap = bitmap
        onDispose { managedBitmap?.recycle() }
    }
    AttachmentTile(
        modifier = Modifier.clickable(onClick = onClick),
        size = 88.dp,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.chat_attachment_sent_image),
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
    attachment: DraftAttachment,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit = {},
) {
    AttachmentTile(
        modifier = modifier.testTag("chat_composer_attachment"),
    ) {
        if (attachment.category == AttachmentCategory.IMAGE) {
            UriImage(
                uri = Uri.fromFile(File(attachment.reference)),
                modifier = Modifier.matchParentSize(),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surfaceDim),
                    )
                },
                error = {
                    AttachmentPlaceholder()
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceDim)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (attachment.category == AttachmentCategory.AUDIO) {
                        Icons.Default.AudioFile
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        RemoveButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("chat_composer_attachment_remove"),
            onClick = onRemove,
        )
    }
}

@Composable
private fun PersistedFileAttachment(
    attachment: FileAttachment,
    onOpenDocument: (FileAttachment) -> Unit,
) {
    val context = LocalContext.current
    var isPlaying by remember(attachment.id) { mutableStateOf(false) }
    val mediaPlayer = remember(attachment.id) {
        if (attachment.category == AttachmentCategory.AUDIO) MediaPlayer() else null
    }
    DisposableEffect(mediaPlayer) {
        onDispose { mediaPlayer?.release() }
    }
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                if (attachment.category == AttachmentCategory.AUDIO) {
                    val directory = File(context.cacheDir, "message-attachments").apply { mkdirs() }
                    val safeName = attachment.displayName.replace(Regex("""[^\w.\-]"""), "_")
                    val file = File(directory, "${attachment.id}-$safeName")
                    if (!file.exists()) file.writeBytes(attachment.data)
                    val player = mediaPlayer ?: return@clickable
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (player.currentPosition == 0) {
                            player.reset()
                            player.setDataSource(file.absolutePath)
                            player.prepare()
                            player.setOnCompletionListener { isPlaying = false }
                        }
                        player.start()
                        isPlaying = true
                    }
                } else onOpenDocument(attachment)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                attachment.category == AttachmentCategory.AUDIO && isPlaying -> Icons.Default.Pause
                attachment.category == AttachmentCategory.AUDIO -> Icons.Default.PlayArrow
                else -> Icons.Default.Description
            },
            contentDescription = null,
        )
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun AttachmentPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.stream_ai_compose_ic_image_placeholder),
            tint = MaterialTheme.colorScheme.surfaceVariant,
            contentDescription = null,
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

    var bitmap by remember(uri, targetSizePx) { mutableStateOf<Bitmap?>(null) }
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
    DisposableEffect(bitmap) {
        val managedBitmap = bitmap
        onDispose { managedBitmap?.recycle() }
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
    IconButton(
        modifier = modifier.size(AttachmentRemoveButtonTokens.touchTargetSize),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .size(AttachmentRemoveButtonTokens.visualSize)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                )
                .testTag("chat_composer_attachment_remove_visual"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.chat_attachment_remove),
                modifier = Modifier.size(AttachmentRemoveButtonTokens.iconSize),
            )
        }
    }
}

internal object AttachmentRemoveButtonTokens {
    val touchTargetSize = 48.dp
    val visualSize = 28.dp
    val iconSize = 16.dp
}

@Preview
@Composable
private fun SelectedAttachmentPreview() {
    SelectedAttachment(
        attachment = DraftAttachment(
            reference = "1",
            displayName = "preview.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1,
            category = AttachmentCategory.DOCUMENT,
        ),
    )
}
