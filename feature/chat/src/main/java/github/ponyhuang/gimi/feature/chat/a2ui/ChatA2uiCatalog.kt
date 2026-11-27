package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** 使用 AndroidX 的标准目录，接入应用已有的媒体渲染和链接处理。 */
internal fun chatA2uiCatalog(openUrl: (String) -> Unit): A2uiCatalog =
    materialA2uiBasicCatalogV1(
        list = ChatA2uiListViewport,
        image = MaterialA2uiBasicCatalogV1Defaults.image { url, description, scale, modifier, onError ->
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = scale,
                modifier = modifier,
                onError = { onError(it.result.throwable) },
            )
        },
        video = MaterialA2uiBasicCatalogV1Defaults.video(ChatA2uiVideoRenderer),
        audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer(ChatA2uiAudioPlayerRenderer),
        functions = object : A2uiBasicCatalogV1.Functions {
            override fun openUrl(url: String, executionContext: A2uiExecutionContext) {
                openUrl(url)
            }
        },
    )

/** 同轴 Lazy 列表会给条目无界约束，嵌套列表需在自己的滚动轴上补足视口上限。 */
private object ChatA2uiListViewport : A2uiBasicCatalogV1.List {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val configuration = LocalConfiguration.current
        val boundedModifier = when (direction) {
            A2uiBasicCatalogV1.List.Direction.Vertical ->
                modifier.heightIn(max = configuration.screenHeightDp.coerceAtLeast(1).dp)
            A2uiBasicCatalogV1.List.Direction.Horizontal ->
                modifier.widthIn(max = configuration.screenWidthDp.coerceAtLeast(1).dp)
        }
        with(MaterialA2uiBasicCatalogV1Defaults.list) {
            TypedContent(children, direction, align, accessibility, boundedModifier)
        }
    }
}
