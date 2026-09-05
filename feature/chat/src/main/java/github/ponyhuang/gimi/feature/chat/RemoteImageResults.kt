package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageReference
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageResult

/** Direct carousel rendering for remote images discovered in a structured tool response. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteImageCarousel(
    result: RemoteImageResult,
    modifier: Modifier = Modifier,
) {
    if (result.images.isEmpty()) return
    val images = remember(result.images) { result.images.take(RemoteImagePreviewLimit) }
    val state = rememberCarouselState { images.size }
    var preview by remember { mutableStateOf<IndexedValue<RemoteImageReference>?>(null) }

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 176.dp,
        itemSpacing = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            .testTag("remote_image_carousel"),
    ) { index ->
        val image = images[index]
        val description = stringResource(R.string.chat_remote_image, index + 1)
        Surface(
            onClick = { preview = IndexedValue(index, image) },
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .testTag("remote_image_item_$index"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = image.url,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (images.size > 1 && index == 0) {
                    Surface(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_remote_image_swipe_hint),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }

    preview?.let { selected ->
        ZoomableCoilImagePreviewDialog(
            model = selected.value.url,
            imageKey = selected.value.url,
            contentDescription = stringResource(R.string.chat_remote_image, selected.index + 1),
            onDismiss = { preview = null },
        )
    }
}

private const val RemoteImagePreviewLimit = 10

@Preview(showBackground = true)
@Composable
private fun RemoteImageCarouselPreview() {
    AsssistantaiTheme {
        RemoteImageCarousel(
            result = RemoteImageResult(
                images = listOf(
                    RemoteImageReference(url = "https://example.com/image-1.png"),
                    RemoteImageReference(url = "https://example.com/image-2.png"),
                    RemoteImageReference(url = "https://example.com/image-3.png"),
                ),
            ),
        )
    }
}
