package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.CloudAttachmentItem
import github.ponyhuang.gimi.domain.conversation.cloud.CloudAttachmentState
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 每个草稿附件独立成卡；列表在输入胶囊外滚动，预览不改变发送草稿。 */
@Composable
internal fun AttachmentList(
    attachments: List<DraftAttachment>,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cloudItems: List<CloudAttachmentItem> = emptyList(),
    cloudActive: Boolean = false,
    cloudLocalFiles: Map<github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference, String?> = emptyMap(),
    onCloudControl: (DraftAttachment, Boolean) -> Unit = { _, _ -> },
) {
    val visibleAttachments = attachments.filterNot { attachment ->
        cloudItems.any { it.draft == attachment && it.state == CloudAttachmentState.Removed }
    }
    if (visibleAttachments.isEmpty()) return
    var preview by remember { mutableStateOf<DraftAttachment?>(null) }
    Column(
        modifier = modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleAttachments.forEach { attachment ->
            key(attachment.cloudReference?.let { it.scope + it.fileId } ?: attachment.reference) {
                val local = if (attachment.cloudReference == null) attachment.reference else cloudLocalFiles[attachment.cloudReference].orEmpty()
                DraftAttachmentCard(attachment.copy(reference = local), enabled, { onRemoveAttachment(attachment) }, { preview = attachment },
                    cloudItems.firstOrNull { it.draft == attachment }?.state, cloudActive,
                    { retry -> onCloudControl(attachment, retry) })
            }
        }
    }
    preview?.takeIf { it in visibleAttachments }?.let { attachment ->
        ZoomableCoilImagePreviewDialog(
            model = File(if (attachment.cloudReference == null) attachment.reference else cloudLocalFiles[attachment.cloudReference].orEmpty()),
            imageKey = attachment.cloudReference?.let { it.scope + it.fileId } ?: attachment.reference,
            contentDescription = stringResource(R.string.chat_attachment_image_preview),
            onDismiss = { preview = null },
        )
    }
}

/** 图片使用可点击缩略图，其他附件使用类型图标；不虚构未接入的上传进度。 */
@Composable
private fun DraftAttachmentCard(attachment: DraftAttachment, enabled: Boolean, onRemove: () -> Unit, onPreview: () -> Unit,
    cloudState: CloudAttachmentState? = null, cloudActive: Boolean = false, onCloudControl: (Boolean) -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag("chat_composer_attachment"),
    ) {
        Column {
            Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val image = attachment.category == AttachmentCategory.IMAGE
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(if (image) Modifier.clickable(onClick = onPreview) else Modifier), contentAlignment = Alignment.Center) {
                    if (image) {
                        UriImage(
                            uri = Uri.fromFile(File(attachment.reference)),
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { AttachmentPlaceholder() }, error = { AttachmentPlaceholder() },
                        )
                    } else Icon(if (attachment.category == AttachmentCategory.AUDIO) Icons.Default.AudioFile else Icons.Default.Description,
                        null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(attachment.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val size = when {
                        attachment.sizeBytes >= 1024 * 1024 -> stringResource(R.string.chat_attachment_size_mb, attachment.sizeBytes / (1024.0 * 1024.0))
                        attachment.sizeBytes >= 1024 -> stringResource(R.string.chat_attachment_size_kb, attachment.sizeBytes / 1024.0)
                        else -> stringResource(R.string.chat_attachment_size_bytes, attachment.sizeBytes)
                    }
                    val type = attachment.displayName.substringAfterLast('.', attachment.mimeType.substringAfterLast('/')).uppercase(java.util.Locale.ROOT)
                    Text(stringResource(R.string.chat_attachment_details, type, size), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (cloudState != null) cloudAttachmentStatus(cloudState, cloudActive) else stringResource(when {
                        attachment.cloudReference != null -> R.string.chat_cloud_attachment
                        enabled -> R.string.chat_attachment_ready_to_send
                        else -> R.string.chat_attachment_preparing
                    }),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cloudState is CloudAttachmentState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (cloudActive && cloudState != null) {
                    if (cloudState.canRetry) IconButton(onClick = { onCloudControl(true) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.chat_cloud_retry_file), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (cloudState is CloudAttachmentState.Ready) Icon(Icons.Default.Check,
                        stringResource(R.string.chat_cloud_upload_ready), Modifier.padding(end = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onCloudControl(false) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.chat_attachment_remove), Modifier.size(18.dp))
                    }
                } else IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp).testTag("chat_composer_attachment_remove")) {
                    Icon(Icons.Default.Close, stringResource(R.string.chat_attachment_remove), Modifier.size(18.dp))
                }
            }
            if (cloudState == CloudAttachmentState.Preparing || cloudState == CloudAttachmentState.Uploading)
                LinearProgressIndicator(Modifier.fillMaxWidth())
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

    previewImage?.let { selected ->
        ImagePreviewDialog(
            image = attachments.firstOrNull { it.id == selected.id && it.cloudReference == selected.cloudReference } ?: selected,
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

/** 单图卡片长边上限：按图片原始宽高比自适应，横图取宽、竖图取高。 */
private val SentImageMaxEdge = 280.dp

/** 加载/失败态的占位边长，避免以固定方形撑出留白。 */
private val SentImagePlaceholder = 160.dp

/** 多图网格单格边长：两列排布，方形裁切。 */
private val SentImageGridCell = 106.dp

/**
 * 用户消息图片区：不套气泡，单图按原宽高比放大为圆角卡片、多图方形网格，
 * 右对齐排在文字气泡上方；内部持有点击放大预览的对话框状态。
 */
@Composable
internal fun SentImages(
    images: List<FileAttachment>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    var previewImage by remember { mutableStateOf<FileAttachment?>(null) }
    val single = images.singleOrNull()?.takeIf { !it.isMissing }
    Row(
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
                    .widthIn(max = SentImageMaxEdge)
                    .heightIn(max = SentImageMaxEdge),
            )
        } else {
            SentImageGrid(images) { previewImage = it }
        }
    }

    previewImage?.let { selected ->
        ImagePreviewDialog(
            image = images.firstOrNull { it.id == selected.id && it.cloudReference == selected.cloudReference } ?: selected,
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
 * 单张已发送图片的自适应卡片：尺寸直接由图片 intrinsicSize 的宽高比驱动
 * （在 success 子组合内套用 aspectRatio），横图取宽、竖图取高，长边不超过
 * 调用方给定的 widthIn/heightIn 上限，容器与图片严丝合缝、不会出现留白。
 */
@Composable
private fun SentImageCard(
    image: FileAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = image.imageModel,
        contentDescription = stringResource(R.string.chat_attachment_sent_image),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .size(SentImagePlaceholder)
                    .background(MaterialTheme.colorScheme.surfaceDim),
            )
        },
        error = {
            Box(modifier = Modifier.size(SentImagePlaceholder)) {
                AttachmentPlaceholder()
            }
        },
        success = {
            val intrinsic = painter.intrinsicSize
            val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
                intrinsic.width / intrinsic.height
            } else {
                1f
            }
            SubcomposeAsyncImageContent(
                modifier = Modifier.aspectRatio(ratio),
            )
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
internal fun UriImage(
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

@Preview(name = "独立附件卡片", showBackground = true, widthDp = 390)
@Preview(name = "独立附件卡片 · 深色", showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DraftAttachmentsPreview() {
    AsssistantaiTheme {
        AttachmentList(
            attachments = listOf(
                DraftAttachment("preview.pdf", "产品需求说明书.pdf", "application/pdf", 2516582, AttachmentCategory.DOCUMENT),
                DraftAttachment("preview.jpg", "山间日落.jpg", "image/jpeg", 1258291, AttachmentCategory.IMAGE),
            ),
            onRemoveAttachment = {},
        )
    }
}


@Preview(name = "逐文件上传状态 · 浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "逐文件上传状态 · 深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloudAttachmentCardsPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(CloudAttachmentPreviewProvider::class) state: CloudAttachmentState,
) {
    val drafts = listOf(DraftAttachment("one.pdf", "产品需求说明书.pdf", "application/pdf", 2516582, AttachmentCategory.DOCUMENT),
        DraftAttachment("two.pdf", "市场研究报告.pdf", "application/pdf", 8493466, AttachmentCategory.DOCUMENT))
    val ready = FileAttachment("application/pdf", "one", 2516582, "产品需求说明书.pdf", "one.pdf")
    AsssistantaiTheme {
        AttachmentList(drafts, {}, cloudItems = listOf(CloudAttachmentItem(drafts[0], CloudAttachmentState.Ready(ready)),
            CloudAttachmentItem(drafts[1], state)), cloudActive = true)
    }
}

/** 逐文件的准备、上传、失败、取消及就绪状态预览，不启动任何业务任务。 */
class CloudAttachmentPreviewProvider : androidx.compose.ui.tooling.preview.PreviewParameterProvider<CloudAttachmentState> {
    override val values = sequenceOf(CloudAttachmentState.Preparing, CloudAttachmentState.Uploading,
        CloudAttachmentState.Failed(github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure.UploadRejected),
        CloudAttachmentState.Cancelled,
        CloudAttachmentState.Ready(FileAttachment("application/pdf", "two", 8493466, "市场研究报告.pdf", "two.pdf")))
}
