package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.FunctionIcon
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 此偏好只控制聊天工具入口，不代表自动调用；服务暂停时仍可编辑并保留偏好。 */
@Composable
fun OfficialToolsSection(service: LLMModelSetting, supported: List<OfficialToolSpec>, editable: Boolean, onEnabledChange: (Boolean) -> Unit) {
    if (supported.isEmpty()) return
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_tools))
    PreferenceGroupCard {
        PreferenceListItem(leadingIcon = { FunctionIcon(it) },
            title = stringResource(R.string.modelsettings_provider_tools),
            subtitle = stringResource(R.string.modelsettings_provider_tools_summary),
            modifier = Modifier.toggleable(service.isOfficialToolsEnabled, enabled = editable, role = Role.Switch,
                onValueChange = onEnabledChange),
            trailingContent = { GimiSwitch(service.isOfficialToolsEnabled, onCheckedChange = null, enabled = editable) })
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            supported.forEach { tool ->
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.alpha(if (service.isOfficialToolsEnabled) 1f else 0.5f)) {
                    Text(stringResource(when (tool.toolId) {
                        OfficialToolIds.DEEPSEEK_WEB_SEARCH, OfficialToolIds.ANTHROPIC_WEB_SEARCH,
                        OfficialToolIds.MINIMAX_WEB_SEARCH, OfficialToolIds.GEMINI_WEB_SEARCH -> R.string.modelsettings_tool_search
                        OfficialToolIds.DEEPSEEK_WEB_FETCH -> R.string.modelsettings_tool_fetch
                        OfficialToolIds.MINIMAX_IMAGE_GENERATION -> R.string.modelsettings_tool_image
                        OfficialToolIds.GEMINI_URL_CONTEXT -> R.string.modelsettings_tool_url
                        OfficialToolIds.GEMINI_GOOGLE_MAPS -> R.string.modelsettings_tool_maps
                        OfficialToolIds.GLM_WEB_SEARCH -> R.string.modelsettings_tool_glm
                        else -> R.string.modelsettings_tool_kimi_web_tools
                    }), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Preview(name = "页内工具", widthDp = 390, heightDp = 1250, locale = "zh")
@Preview(name = "页内工具 · 深色", widthDp = 390, heightDp = 1250, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InlineExtensionsPreview(@PreviewParameter(InlineExtensionsPreviewProvider::class) protocol: ApiProtocol) {
    val service = modelServicePreviewFixture().copy(apiProtocol = protocol)
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service,
        supportedTools = OfficialToolSupport().all.filter { it.serviceId == service.id && protocol in it.protocols }), {}, { _, _ -> }) }
}

/** 覆盖不同协议下官方工具可用性差异的情况。 */
class InlineExtensionsPreviewProvider : PreviewParameterProvider<ApiProtocol> {
    override val values = sequenceOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic, ApiProtocol.Gemini)
}
