package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 此偏好只控制聊天工具入口，不代表自动调用；服务暂停时仍可编辑并保留偏好。 */
@Composable
fun OfficialToolsSection(service: LLMModelSetting, supported: List<OfficialToolSpec>, editable: Boolean, onEnabledChange: (Boolean) -> Unit) {
    PreferenceSectionTitle(stringResource(R.string.modelsettings_provider_tools))
    PreferenceGroupCard {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.heightIn(min = 48.dp).toggleable(value = service.isOfficialToolsEnabled, enabled = editable,
                role = Role.Switch, onValueChange = onEnabledChange), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.modelsettings_official_tools_enabled),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Switch(checked = service.isOfficialToolsEnabled, onCheckedChange = null, enabled = editable)
            }
            Text(stringResource(R.string.modelsettings_official_tools_enabled_summary, service.name),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
    PreferenceSectionNote(stringResource(R.string.modelsettings_tools_limits))
    PreferenceSectionTitle(stringResource(R.string.modelsettings_supported_capabilities))
    if (supported.isNotEmpty()) PreferenceGroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            supported.forEach { tool ->
                Text(stringResource(when (tool.toolId) {
                    OfficialToolIds.DEEPSEEK_WEB_SEARCH, OfficialToolIds.ANTHROPIC_WEB_SEARCH,
                    OfficialToolIds.MINIMAX_WEB_SEARCH, OfficialToolIds.GEMINI_WEB_SEARCH -> R.string.modelsettings_tool_search
                    OfficialToolIds.DEEPSEEK_WEB_FETCH -> R.string.modelsettings_tool_fetch
                    OfficialToolIds.MINIMAX_IMAGE_GENERATION -> R.string.modelsettings_tool_image
                    OfficialToolIds.GEMINI_URL_CONTEXT -> R.string.modelsettings_tool_url
                    OfficialToolIds.GEMINI_GOOGLE_MAPS -> R.string.modelsettings_tool_maps
                    OfficialToolIds.GLM_WEB_SEARCH -> R.string.modelsettings_tool_glm
                    else -> R.string.modelsettings_tool_formulas
                }), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (tool.canBeUsedAcrossModels) R.string.modelsettings_tool_cross_model
                    else R.string.modelsettings_tool_native), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    PreferenceSectionNote(stringResource(R.string.modelsettings_tools_save_hint))
}

@Preview(name = "扩展能力", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "扩展能力 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExtensionsPreview() {
    val service = modelServicePreviewFixture()
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service, page = ServiceEditorPage.Extensions,
        supportedTools = OfficialToolSupport().all.filter { it.serviceId == service.id && service.apiProtocol in it.protocols }), {}, { _, _ -> }) }
}
