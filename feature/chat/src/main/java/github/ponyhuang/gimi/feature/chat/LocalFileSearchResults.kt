package github.ponyhuang.gimi.feature.chat

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Material 3 carousel preview for one structured local-file search response. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalFileSearchCarousel(
    responseId: String,
    result: LocalFileSearchResult,
    onOpenFile: (LocalFileReference) -> Unit,
    onShowAll: (responseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result.files.isEmpty()) return
    val previewFiles = remember(result.files) { result.files.take(CarouselPreviewLimit) }
    val state = rememberCarouselState { previewFiles.size }
    var previewImage by remember { mutableStateOf<LocalFileReference?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 176.dp,
            itemSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .testTag("local_file_search_carousel"),
        ) { index ->
            val file = previewFiles[index]
            LocalFileResultCard(
                file = file,
                onClick = {
                    if (file.isImage) previewImage = file else onOpenFile(file)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge),
            )
        }
        TextButton(
            onClick = { onShowAll(responseId) },
            modifier = Modifier
                .align(Alignment.End)
                .testTag("local_file_search_show_all"),
        ) {
            Text(stringResource(R.string.chat_local_file_show_all, result.files.size))
        }
    }

    previewImage?.let { file ->
        LocalFileImagePreview(file = file, onDismiss = { previewImage = null })
    }
}

/** Dedicated vertically scrolling page containing every item from a file-search carousel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileSearchResultsScreen(
    result: LocalFileSearchResult?,
    onBack: () -> Unit,
    onOpenFile: (LocalFileReference) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewImage by remember { mutableStateOf<LocalFileReference?>(null) }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("local_file_search_all_results"),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_local_file_results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_local_file_results_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        val files = result?.files.orEmpty()
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.chat_local_file_results_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = files,
                    key = LocalFileReference::contentUri,
                ) { file ->
                    LocalFileResultCard(
                        file = file,
                        onClick = {
                            if (file.isImage) previewImage = file else onOpenFile(file)
                        },
                        modifier = Modifier.height(188.dp),
                    )
                }
            }
        }
    }

    previewImage?.let { file ->
        LocalFileImagePreview(file = file, onDismiss = { previewImage = null })
    }
}

@Composable
private fun LocalFileResultCard(
    file: LocalFileReference,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("local_file_search_item_${file.displayName}"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (file.isImage) {
            Box(modifier = Modifier.fillMaxSize()) {
                LocalContentUriImage(
                    file = file,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = file.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = if (file.mimeType.equals("application/pdf", true)) {
                        Icons.Default.PictureAsPdf
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = file.displayName,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = Formatter.formatShortFileSize(context, file.sizeBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalContentUriImage(
    file: LocalFileReference,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSize = with(density) { 240.dp.roundToPx() }
    val uri = remember(file.contentUri) { Uri.parse(file.contentUri) }
    var bitmap by remember(uri, targetSize) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, targetSize) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                decodeSampledBitmap(context.contentResolver, uri, targetSize)
            }.getOrNull()
        }
    }
    DisposableEffect(bitmap) {
        val managedBitmap = bitmap
        onDispose { managedBitmap?.recycle() }
    }

    val currentBitmap = bitmap
    if (currentBitmap == null) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.chat_local_file_image, file.displayName),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun LocalFileImagePreview(
    file: LocalFileReference,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uri = remember(file.contentUri) { Uri.parse(file.contentUri) }
    val description = stringResource(R.string.chat_local_file_image_preview, file.displayName)
    ZoomableImagePreviewDialog(
        imageKey = file.contentUri,
        contentDescription = description,
        loadBitmap = { targetSize ->
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledBitmap(context.contentResolver, uri, targetSize)
                }.getOrNull()
            }
        },
        onDismiss = onDismiss,
    )
}

private const val CarouselPreviewLimit = 10
