package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 所有 Chat Completions 服务共享上传偏好与格式说明，编辑器保存后才影响发送。 */
@Composable
internal fun FileUploadSection(service: LLMModelSetting, editable: Boolean, onAction: (LLMModelSettingDetailAction) -> Unit) {
    if (service.apiProtocol != ApiProtocol.OpenaiChatcompletions) return
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_files))
    PreferenceGroupCard {
        PreferenceListItem(icon = Icons.Default.UploadFile,
            title = stringResource(R.string.modelsettings_file_upload),
            subtitle = stringResource(R.string.modelsettings_file_upload_summary),
            modifier = Modifier.toggleable(service.isFileUploadEnabled, enabled = editable, role = Role.Switch,
                onValueChange = { onAction(LLMModelSettingDetailAction.FileUploadEnabledChanged(it)) }),
            trailingContent = { Switch(service.isFileUploadEnabled, onCheckedChange = null, enabled = editable) })
        if (service.isFileUploadEnabled) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (service.isFileUploadEnabled) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.modelsettings_file_retention), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 7, 30).forEach { days ->
                    FilterChip(selected = days == service.fileUploadRetentionDays, enabled = editable,
                        onClick = { onAction(LLMModelSettingDetailAction.FileUploadRetentionChanged(days)) },
                        label = { Text(stringResource(R.string.modelsettings_file_retention_days, days)) },
                        modifier = Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.modelsettings_file_retention_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    PreferenceSectionNote(stringResource(R.string.modelsettings_file_upload_pending))
}

@Preview(name = "文件上传 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "文件上传 · 深色", widthDp = 390, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FileUploadPreview(@PreviewParameter(FileUploadPreviewProvider::class) enabled: Boolean) {
    AsssistantaiTheme {
        Column { FileUploadSection(modelServicePreviewFixture().copy(id = "openai", name = "OpenAI",
            isFileUploadEnabled = enabled), true) {} }
    }
}

/** 文件上传打开和关闭时的布局预览。 */
class FileUploadPreviewProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}
