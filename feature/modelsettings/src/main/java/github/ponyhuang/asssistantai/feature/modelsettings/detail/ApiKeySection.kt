package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeySection(
    apiKey: String,
    keyHelpUrl: String,
    isVisible: Boolean,
    isTesting: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onTest: () -> Unit,
    onOpenKeyHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API 密钥") },
            singleLine = true,
            visualTransformation = if (isVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isVisible) "隐藏密钥" else "显示密钥",
                        )
                    }
                    TextButton(
                        enabled = apiKey.isNotBlank() && !isTesting,
                        onClick = onTest,
                    ) {
                        Text("检测")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        val primary = MaterialTheme.colorScheme.primary
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
                pushStringAnnotation(tag = TAG_KEY_HELP, annotation = keyHelpUrl)
                append("点击这里获取密钥")
                pop()
            }
        }
        ClickableText(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = TextStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            onClick = { offset ->
                if (text.getStringAnnotations(TAG_KEY_HELP, offset, offset).isNotEmpty()) {
                    onOpenKeyHelp()
                }
            },
        )
    }
}

private const val TAG_KEY_HELP = "key_help"
