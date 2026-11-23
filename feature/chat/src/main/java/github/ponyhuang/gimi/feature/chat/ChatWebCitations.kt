package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import github.ponyhuang.gimi.domain.conversation.model.WebSource
import github.ponyhuang.gimi.domain.conversation.model.webCitationHost
import github.ponyhuang.gimi.ui.components.GimiSheet
import github.ponyhuang.gimi.ui.components.GimiSheetIconAction
import github.ponyhuang.gimi.ui.components.LocalGimiSheetBodyMaxHeight

/** 仅为 Markdown 渲染副本插入链接，复制、朗读和模型历史继续用原始内容。 */
internal fun String.withWebCitationLinks(
    citations: List<WebCitation>,
    partStart: Int,
    numbers: Map<String, Int>,
): String = buildString {
    var cursor = 0
    for (citation in citations) {
        val offset = citation.endOffset - partStart
        if (offset <= 0 || offset !in cursor..this@withWebCitationLinks.length) continue
        if (this@withWebCitationLinks.getOrNull(offset)?.isLowSurrogate() == true) continue
        val number = numbers[citation.source.url] ?: continue
        append(this@withWebCitationLinks, cursor, offset)
        append(""" [**\[""")
        append(number)
        append("""\]**](""")
        append(citation.source.url.replace("(", """\(""").replace(")", """\)"""))
        append(")")
        cursor = offset
    }
    append(this@withWebCitationLinks, cursor, this@withWebCitationLinks.length)
}

@Composable
internal fun WebCitationStack(sources: List<WebSource>, onClick: () -> Unit) {
    val sites = sources.distinctBy { webCitationHost(it.url) }
    val openLabel = stringResource(R.string.chat_web_citations_open)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = openLabel }
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sites.take(3).forEach { WebsiteIcon(it, outlined = true) }
        if (sites.size > 3) {
            Text(
                text = stringResource(R.string.chat_web_citations_overflow, sites.size - 3),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
internal fun WebCitationSheet(
    sources: List<WebSource>,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    GimiSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_web_citations_title),
        trailing = {
            GimiSheetIconAction(Icons.Default.Close, stringResource(R.string.chat_web_citations_close), onDismiss)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = LocalGimiSheetBodyMaxHeight.current)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sources.forEach { source ->
                val host = webCitationHost(source.url) ?: return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(role = Role.Button) { onOpenSource(source.url) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WebsiteIcon(source)
                    Text(
                        text = source.title ?: host,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsiteIcon(source: WebSource, outlined: Boolean = false) {
    val icon = source.iconUrl
    var modifier = Modifier.size(if (outlined) 25.dp else 28.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (outlined) modifier = modifier.border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = Modifier.size(16.dp)
        if (icon == null) GenericWebsiteIcon(image)
        else SubcomposeAsyncImage(
            model = icon,
            contentDescription = null,
            modifier = image,
            loading = { GenericWebsiteIcon(image) },
            error = { GenericWebsiteIcon(image) },
        )
    }
}

@Composable
private fun GenericWebsiteIcon(modifier: Modifier) {
    Icon(Icons.Default.Language, null, modifier, MaterialTheme.colorScheme.onSurfaceVariant)
}
