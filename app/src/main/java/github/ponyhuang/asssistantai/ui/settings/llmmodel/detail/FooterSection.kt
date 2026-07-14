package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import github.ponyhuang.asssistantai.data.LLMModelProvider

/**
 * 详情页底部脚注：
 *
 * > 查看 深度求索 文档 和 模型 获取更多详情
 *
 * "深度求索 文档" 与 "模型" 均为可点击富文本，分别跳转到 [LLMModelProvider.docsUrl] 与
 * [LLMModelProvider.modelsUrl]。
 */
@Composable
fun FooterSection(
    service: LLMModelProvider,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    val text = buildAnnotatedString {
        append("查看 ")
        withStyle(style = SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(tag = TAG_DOCS, annotation = service.docsUrl)
            append("${service.serviceName} 文档")
            pop()
        }
        append(" 和 ")
        withStyle(style = SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
            pushStringAnnotation(tag = TAG_MODELS, annotation = service.modelsUrl)
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
            val annDoc = text.getStringAnnotations(tag = TAG_DOCS, start = offset, end = offset).firstOrNull()
            val annModel = text.getStringAnnotations(tag = TAG_MODELS, start = offset, end = offset).firstOrNull()
            val url = annDoc?.item ?: annModel?.item
            if (url.isNullOrBlank()) {
                Toast.makeText(context, "未配置对应链接", Toast.LENGTH_SHORT).show()
                return@ClickableText
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

private const val TAG_DOCS = "docs"
private const val TAG_MODELS = "models"