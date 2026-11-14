package github.ponyhuang.gimi.feature.modelsettings.add

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.GimiSearchField
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 添加服务目录：兼容接口（自定义 LLM）与内置服务商模板。
 *
 * 模板能力描述应用集成范围；同一模板可以重复创建多套独立配置。
 */
@Composable
fun AddServiceRoute(
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenCustom: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddServiceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        modifier = modifier,
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_add_service),
        onBack = onBack,
    ) { scaffoldModifier ->
        AddServiceScreen(state, viewModel::onAction, onOpenTemplate, onOpenCustom, scaffoldModifier)
    }
}

@Composable
internal fun AddServiceScreen(
    state: AddServiceUiState,
    onAction: (AddServiceAction) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier, style = PreferenceStyle.Compact) {
        Column(Modifier.fillMaxSize()) {
            GimiSearchField(
                state.query,
                { onAction(AddServiceAction.QueryChanged(it)) },
                placeholder = stringResource(R.string.modelsettings_search_provider),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val normalizedQuery = state.query.trim()
            val customMatches = normalizedQuery.isBlank() ||
                stringResource(R.string.modelsettings_custom_llm_title)
                    .contains(normalizedQuery, ignoreCase = true)
            val templates = state.templates.filter { template ->
                normalizedQuery.isBlank() || template.name.contains(normalizedQuery, ignoreCase = true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (customMatches) {
                    item {
                        PreferenceSectionTitle(stringResource(R.string.modelsettings_section_compatible))
                        PreferenceGroupCard {
                            PreferenceListItem(
                                icon = Icons.Outlined.Code,
                                title = stringResource(R.string.modelsettings_custom_llm_title),
                                subtitle = stringResource(R.string.modelsettings_custom_llm_subtitle),
                                subtitleMaxLines = 2,
                                onClick = onOpenCustom,
                            )
                        }
                    }
                }
                item {
                    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_built_in))
                    // 多实例说明紧跟分组标题；底部不再重复展示。
                    PreferenceSectionNote(stringResource(R.string.modelsettings_add_service_note))
                    if (templates.isEmpty()) {
                        PreferenceSectionNote(stringResource(R.string.modelsettings_no_matches))
                    } else {
                        PreferenceGroupCard {
                            templates.forEachIndexed { index, template ->
                                PreferenceListItem(
                                    title = template.name,
                                    subtitle = template.capabilitySummary(),
                                    leadingIcon = { iconModifier ->
                                        LLMModelServiceIcon(template.id, iconModifier, contentPadding = 2.dp)
                                    },
                                    showDivider = index < templates.lastIndex,
                                    onClick = { onOpenTemplate(template.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 模板集成能力的展示摘要：对话 · 识别 · 合成 · 实时语音（仅已集成项）。 */
@Composable
private fun github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate.capabilitySummary(): String =
    buildList {
        connections.forEach { connection ->
            val label = stringResource(
                when (connection.capability) {
                    github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Conversation ->
                        R.string.modelsettings_capability_chat
                    github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Recognition ->
                        R.string.modelsettings_capability_recognition
                    github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Synthesis ->
                        R.string.modelsettings_capability_synthesis
                    github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Realtime ->
                        R.string.modelsettings_capability_realtime
                },
            )
            add(label)
        }
    }.joinToString(" · ")

@Preview(name = "添加服务 · 浅色", showBackground = true, widthDp = 390, heightDp = 720, locale = "zh")
@Preview(
    name = "添加服务 · 深色", showBackground = true, widthDp = 390, heightDp = 720, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AddServicePreview() {
    GimiTheme {
        AddServiceScreen(
            state = AddServiceUiState(
                templates = listOf(
                    github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate(
                        id = "openai",
                        name = "OpenAI",
                        connections = listOf(
                            github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection(
                                capability = github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Conversation,
                                protocols = listOf(github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions),
                                defaultEndpoints = mapOf(github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions to ""),
                                endpointPolicy = github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy.UserConfigured,
                                endpointSchemes = setOf("http", "https"),
                            ),
                        ),
                    ),
                ),
            ),
            onAction = {},
            onOpenTemplate = {},
            onOpenCustom = {},
        )
    }
}
