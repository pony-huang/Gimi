package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.data.LLMModelProvider
import kotlinx.coroutines.launch

/**
 * API 密钥输入区。
 *
 * - 默认密码隐藏，眼睛按钮可切换。
 * - 末尾"检测"按钮调用 ViewModel 的 `testApiKey()`，Toast 结果。
 * - 底部 HelperText 左侧"点击这里获取密钥"为可点击富文本。
 */
@Composable
fun ApiKeySection(
    service: LLMModelProvider,
    onApiKeyChange: (String) -> Unit,
    onTest: suspend () -> ApiKeyTestResult,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    // service.apiKey 变更（外部 Store 改动）时同步到本地 input。
    var input by remember(service.serviceId) { mutableStateOf(service.apiKey) }
    LaunchedEffect(service.apiKey) {
        // Store 远端更新过来时同步；用户输入触发的也走这里，但因为同值所以无副作用。
        if (input != service.apiKey) input = service.apiKey
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                onApiKeyChange(it)
            },
            label = { Text("API 密钥") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (visible) "隐藏密钥" else "显示密钥",
                        )
                    }
                    TextButton(
                        enabled = input.isNotBlank() && !testing,
                        onClick = {
                            testing = true
                            scope.launch {
                                val result = onTest()
                                val msg = when (result) {
                                    ApiKeyTestResult.Success -> "检测成功"
                                    ApiKeyTestResult.Failure -> "检测失败"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                testing = false
                            }
                        },
                    ) {
                        Text("检测")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val primary = MaterialTheme.colorScheme.primary
            val text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
                    pushStringAnnotation(tag = TAG_KEY_HELP, annotation = service.keyHelpUrl)
                    append("点击这里获取密钥")
                    pop()
                }
            }
            ClickableText(
                text = text,
                style = TextStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                onClick = { offset ->
                    text.getStringAnnotations(tag = TAG_KEY_HELP, start = offset, end = offset)
                        .firstOrNull()
                        ?.let { ann ->
                            val url = ann.item
                            if (url.isBlank()) {
                                Toast.makeText(context, "未配置密钥获取链接", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                },
            )
        }
    }
}

private const val TAG_KEY_HELP = "key_help"
