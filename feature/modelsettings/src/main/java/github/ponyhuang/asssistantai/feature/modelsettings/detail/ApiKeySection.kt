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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.feature.modelsettings.R

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
            label = { Text(stringResource(R.string.modelsettings_api_key_label)) },
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
                            contentDescription = stringResource(
                                if (isVisible) R.string.modelsettings_api_key_hide
                                else R.string.modelsettings_api_key_show,
                            ),
                        )
                    }
                    TextButton(
                        enabled = apiKey.isNotBlank() && !isTesting,
                        onClick = onTest,
                    ) {
                        Text(stringResource(R.string.modelsettings_api_key_test))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        val primary = MaterialTheme.colorScheme.primary
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
                pushStringAnnotation(tag = TAG_KEY_HELP, annotation = keyHelpUrl)
                append(stringResource(R.string.modelsettings_api_key_get_key))
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
