package github.ponyhuang.gimi.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableFloatStateOf
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
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
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
            if (image.isMissing) {
                // 载荷文件已丢失：只渲染缺失占位，不提供点击预览。
                MissingImageTile()
            } else {
                InlineImage(
                    image = image,
                    onClick = { previewImage = image },
                )
            }
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

/**
 * The Coil model for a sent image: the persisted file when there is one, otherwise the inline
 * bytes carried by attachments restored from an ADK `inlineData` part. Preferring the file
 * keeps whole payloads out of the heap and lets Coil own downsampling and caching.
 */
private val FileAttachment.imageModel: Any?
    get() = payloadReference?.let(::File) ?: inlineData

/** Displays a sent image at a screen-appropriate resolution with zoom and pan gestures. */
@Composable
private fun ImagePreviewDialog(
    image: FileAttachment,
    onDismiss: () -> Unit,
) {
    ZoomableCoilImagePreviewDialog(
        model = image.imageModel,
        imageKey = image.id,
        contentDescription = stringResource(R.string.chat_attachment_sent_image_preview),
        onDismiss = onDismiss,
    )
}

/** 用户消息图片的排布方式（对应 ChatGPT 式无边框大图设计）。 */
internal enum class SentImagesLayout {
    /** 纯图片消息：不套气泡，单图按原宽高比放大为圆角卡片，多图方形网格。 */
    STANDALONE,

    /** 图文混合：图片贴气泡顶边全幅展示，由气泡形状统一裁剪圆角。 */
    FULL_BLEED_HEADER,
}

/** 单图卡片最大宽度/高度：约占半屏宽，超长图按高度截断并裁切。 */
private val SentImageMaxWidth = 220.dp
private val SentImageMaxHeight = 300.dp

/** 多图网格单格边长：两列排布，方形裁切。 */
private val SentImageGridCell = 106.dp

/**
 * 用户消息图片区：按 [layout] 决定无边框大图或贴边全幅头部，
 * 内部持有点击放大预览的对话框状态。
 */
@Composable
internal fun SentImages(
    images: List<FileAttachment>,
    layout: SentImagesLayout,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    var previewImage by remember { mutableStateOf<FileAttachment?>(null) }
    val single = images.singleOrNull()?.takeIf { !it.isMissing }
    when (layout) {
        SentImagesLayout.STANDALONE -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (single != null) {
                SentImageCard(
                    image = single,
                    onClick = { previewImage = single },
                    modifier = Modifier
                        .widthIn(max = SentImageMaxWidth)
                        .heightIn(max = SentImageMaxHeight),
                )
            } else {
                SentImageGrid(images) { previewImage = it }
            }
        }

        SentImagesLayout.FULL_BLEED_HEADER -> Column(modifier = modifier.fillMaxWidth()) {
            if (single != null) {
                SentImageCard(
                    image = single,
                    onClick = { previewImage = single },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SentImageMaxHeight),
                )
            } else {
                SentImageGrid(images) { previewImage = it }
            }
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            image = image,
            onDismiss = { previewImage = null },
        )
    }
}

/** 多图（或含缺失项）的方形网格缩略行。 */
@Composable
private fun SentImageGrid(
    images: List<FileAttachment>,
    onClick: (FileAttachment) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        images.forEach { image ->
            if (image.isMissing) {
                MissingImageTile()
            } else {
                InlineImage(
                    image = image,
                    size = SentImageGridCell,
                    onClick = { onClick(image) },
                )
            }
        }
    }
}

/**
 * 单张已发送图片的宽高比卡片：加载完成后读取 intrinsicSize 更新比例，
 * 保证不同方向的实拍图都以原始构图展示而不是固定方块。
 */
@Composable
private fun SentImageCard(
    image: FileAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ratio by remember(image.id) { mutableFloatStateOf(1f) }
    SubcomposeAsyncImage(
        model = image.imageModel,
        contentDescription = stringResource(R.string.chat_attachment_sent_image),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .aspectRatio(ratio),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceDim),
            )
        },
        error = { AttachmentPlaceholder() },
        success = {
            val intrinsic = painter.intrinsicSize
            if (intrinsic.width > 0f && intrinsic.height > 0f) {
                LaunchedEffect(intrinsic) { ratio = intrinsic.width / intrinsic.height }
            }
            SubcomposeAsyncImageContent()
        },
    )
}

@Composable
private fun InlineImage(
    image: FileAttachment,
    size: Dp = 88.dp,
    onClick: () -> Unit,
) {
    AttachmentTile(
        modifier = Modifier.clickable(onClick = onClick),
        size = size,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceDim),
        )
        SubcomposeAsyncImage(
            model = image.imageModel,
            contentDescription = stringResource(R.string.chat_attachment_sent_image),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            // 加载期失败（文件损坏、解码异常等）与映射期缺失是两层：文件存在但读不出来
            // 时也要给出明确占位，而不是渲染成空白块。
            error = { AttachmentPlaceholder() },
        )
    }
}

/** 缺失图片附件的降级占位：只提示文件已丢失，不提供任何依赖载荷的操作。 */
@Composable
private fun MissingImageTile() {
    AttachmentTile(size = 88.dp) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceDim),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.stream_ai_compose_ic_image_placeholder),
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.chat_attachment_file_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    if (attachment.isMissing) {
        MissingFileAttachmentRow(attachment)
        return
    }
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
                    val player = mediaPlayer ?: return@clickable
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (player.currentPosition == 0) {
                            val source = attachment.playbackPath(context) ?: return@clickable
                            player.reset()
                            player.setDataSource(source)
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

/** 缺失文档/音频附件的降级行：仅展示文件名与缺失标记，禁用预览、播放等依赖文件的操作。 */
@Composable
private fun MissingFileAttachmentRow(attachment: FileAttachment) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (attachment.category == AttachmentCategory.AUDIO) {
                Icons.Default.AudioFile
            } else {
                Icons.Default.Description
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f, fill = false),
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.chat_attachment_file_missing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A filesystem path MediaPlayer can open. The persisted payload is used directly; only
 * attachments that carry inline bytes need a cache copy.
 */private fun FileAttachment.playbackPath(context: Context): String? {
    payloadReference?.let { return it }
    val bytes = inlineData ?: return null
    val root = AndroidAppDirectoryResolver(context).resolve(chatShareableDirectorySpec, create = true)
    val directory = File(root, "playback").apply { mkdirs() }
    val safeName = displayName.replace(Regex("""[^\w.\-]"""), "_")
    val file = File(directory, "$id-$safeName")
    if (!file.exists()) file.writeBytes(bytes)
    return file.absolutePath
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
