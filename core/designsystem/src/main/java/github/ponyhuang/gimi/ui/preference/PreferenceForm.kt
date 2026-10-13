package github.ponyhuang.gimi.ui.preference

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 统一的低强调填充式设置输入框；所有编辑、校验和密钥展示均由调用方控制。 */
@Composable
fun PreferenceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val container = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 卡片中的表单内容使用统一内边距，避免各页自行叠加容器与留白。 */
@Composable
fun PreferenceFormContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** 固定的表单操作区；通知文案由所属 feature 提供，安全区与键盘由容器统一处理。 */
@Composable
fun PreferenceSaveBar(
    label: String,
    onSave: () -> Unit,
    enabled: Boolean,
    notice: String? = null,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    Surface(color = preferenceGroupCardColor(), modifier = modifier.imePadding()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notice != null) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(buttonModifier)) {
                    Text(label)
                }
            }
        }
    }
}

@Preview(name = "Compact form light", widthDp = 390, heightDp = 780)
@Preview(name = "Compact form dark", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compact form narrow", widthDp = 320, heightDp = 780, fontScale = 1.3f)
@Preview(name = "Compact form tablet", widthDp = 840, heightDp = 780)
@Composable
private fun PreferenceFormPreview() {
    AsssistantaiTheme {
        PreferenceScaffold(
            title = "连接配置",
            onBack = {},
            style = PreferenceStyle.Compact,
            bottomBar = { PreferenceSaveBar("保存配置", {}, true, "有未保存的修改") },
        ) { modifier ->
            PreferencePageContainer(modifier) {
                PreferenceSectionTitle("连接信息")
                PreferenceGroupCard {
                    PreferenceFormContent {
                        PreferenceTextField("e2b.app", {}, label = { Text("域名") })
                        PreferenceTextField("", {}, label = { Text("API Key") }, isError = true, supportingText = { Text("请填写 API Key") })
                    }
                }
            }
        }
    }
}
