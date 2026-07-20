package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService

@Composable
fun FooterSection(
    service: ModelService,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append("查看 ")
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(TAG_DOCS, service.docsUrl)
            append("${service.name} 文档")
            pop()
        }
        append(" 和 ")
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(TAG_MODELS, service.modelsUrl)
            append("模型")
            pop()
        }
        append(" 获取更多详情")
    }

    ClickableText(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = TextStyle(
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        onClick = { offset ->
            val url = text.getStringAnnotations(TAG_DOCS, offset, offset).firstOrNull()?.item
                ?: text.getStringAnnotations(TAG_MODELS, offset, offset).firstOrNull()?.item
            if (url != null) onOpenUrl(url)
        },
    )
}

private const val TAG_DOCS = "docs"
private const val TAG_MODELS = "models"
