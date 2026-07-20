package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.feature.modelsettings.R

@Composable
fun FooterSection(
    service: ModelService,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val docsLabel = stringResource(R.string.modelsettings_footer_docs_label, service.name)
    val andLabel = stringResource(R.string.modelsettings_footer_and_label)
    val modelsLabel = stringResource(R.string.modelsettings_footer_models_label)
    val introLabel = stringResource(R.string.modelsettings_footer_intro_label)
    val tailLabel = stringResource(R.string.modelsettings_footer_tail_label)

    val text = buildAnnotatedString {
        append(introLabel)
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(TAG_DOCS, service.docsUrl)
            append(docsLabel)
            pop()
        }
        append(andLabel)
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(TAG_MODELS, service.modelsUrl)
            append(modelsLabel)
            pop()
        }
        append(tailLabel)
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
