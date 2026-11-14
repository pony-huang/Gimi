package github.ponyhuang.gimi.feature.modelsettings.list

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.feature.modelsettings.detail.HorizontalRowDivider
import github.ponyhuang.gimi.feature.modelsettings.detail.ServiceListPreviewFixtures
import github.ponyhuang.gimi.ui.components.GimiSearchField
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ChevronRight
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 列表行的展示投影：图标、来源行与用途摘要都在这里推导，页面保持无状态。 */
internal data class ServiceListRow(
    val instance: ModelServiceInstance,
    /** 来源行；内置厂商由图标表达，仅自定义服务显示协议说明。 */
    val sourceLabel: String?,
    val summary: String,
    val isPaused: Boolean,
)

@Composable
fun ModelServiceListScreen(
    state: ModelServiceListUiState,
    onAction: (ModelServiceListAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onAddService: () -> Unit,
    onOpenDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier, style = PreferenceStyle.Compact) {
        when (state.loadState) {
            CatalogLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is CatalogLoadState.Failed -> Text(
                stringResource(R.string.modelsettings_list_load_failed),
                Modifier.padding(24.dp),
            )
            CatalogLoadState.Ready -> {
                val normalizedQuery = state.query.trim()
                val instances = state.items.filter { instance ->
                    normalizedQuery.isBlank() ||
                        instance.name.contains(normalizedQuery, ignoreCase = true) ||
                        sourceLabel(instance).contains(normalizedQuery, ignoreCase = true)
                }
                Column(Modifier.fillMaxSize()) {
                    GimiSearchField(
                        state.query,
                        { onAction(ModelServiceListAction.QueryChanged(it)) },
                        placeholder = stringResource(R.string.modelsettings_search_service),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (instances.isEmpty() && normalizedQuery.isBlank()) {
                        EmptyServiceList(onAddService)
                    } else {
                        ServiceListContent(instances, normalizedQuery, onNavigateToDetail, onOpenDefaults)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceListContent(
    instances: List<ModelServiceInstance>,
    normalizedQuery: String,
    onNavigateToDetail: (String) -> Unit,
    onOpenDefaults: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PreferenceSectionTitle(stringResource(R.string.modelsettings_list_section_configured)) }
        if (instances.isEmpty()) {
            item { PreferenceSectionNote(stringResource(R.string.modelsettings_no_matches)) }
        } else {
            item {
                PreferenceGroupCard {
                    instances.forEachIndexed { index, instance ->
                        ServiceListRowItem(
                            row = instance.toListRow(),
                            showDivider = index < instances.lastIndex,
                            onClick = { onNavigateToDetail(instance.id) },
                        )
                    }
                }
            }
        }
        item { PreferenceSectionTitle(stringResource(R.string.modelsettings_list_section_preferences)) }
        item {
            PreferenceGroupCard {
                PreferenceListItem(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.modelsettings_defaults_entry_title),
                    subtitle = stringResource(R.string.modelsettings_defaults_entry_subtitle),
                    onClick = onOpenDefaults,
                )
            }
        }
    }
}

@Composable
private fun ServiceListRowItem(row: ServiceListRow, showDivider: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 图标 40dp + 行内边距与间距，分隔线与品牌图标右侧对齐，同「默认模型」等偏好行风格。
                LLMModelServiceIcon(row.instance.templateId.orEmpty(), Modifier.size(40.dp), contentPadding = 7.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.instance.name, style = MaterialTheme.typography.bodyLarge)
                // 内置厂商的来源由品牌图标表达；只有自定义服务才显示协议说明行。
                if (row.sourceLabel != null) {
                    Text(
                        row.sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (row.isPaused) {
                        Icon(
                            Icons.Outlined.PauseCircleOutline,
                            contentDescription = null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        row.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            HorizontalRowDivider(start = 70)
        }
    }
}

@Composable
private fun EmptyServiceList(onAddService: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Outlined.CloudQueue,
            contentDescription = null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.modelsettings_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.modelsettings_empty_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddService) {
            Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.modelsettings_add_service))
        }
    }
}

/** 实例的来源行：模板品牌名，或自定义服务的协议说明。 */
internal fun sourceLabel(instance: ModelServiceInstance): String = when (val templateId = instance.templateId) {
    null -> when (instance.conversationConnection?.protocol) {
        ApiProtocol.Anthropic -> PROTOCOL_LABEL_ANTHROPIC
        else -> PROTOCOL_LABEL_OPENAI
    }
    else -> templateId
}

/** 用途摘要：非空能力连接的已选模型数，停用实例加前缀。 */
@Composable
internal fun ModelServiceInstance.toListRow(): ServiceListRow = ServiceListRow(
    instance = this,
    sourceLabel = localizedSourceLabel(),
    summary = localizedSummary(),
    isPaused = !isEnabled,
)

@Composable
private fun ModelServiceInstance.localizedSourceLabel(): String? = when (templateId) {
    null -> stringResource(
        when (conversationConnection?.protocol) {
            ApiProtocol.Anthropic -> R.string.modelsettings_source_custom_anthropic
            else -> R.string.modelsettings_source_custom_openai
        },
    )
    // 内置厂商图标已表达来源，不再重复模板 ID。
    else -> null
}

@Composable
private fun ModelServiceInstance.localizedSummary(): String {
    val counts = buildList {
        connection(ServiceCapability.Conversation)?.models?.size?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.modelsettings_capability_count_chat, it)) }
        connection(ServiceCapability.Recognition)?.models?.size?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.modelsettings_capability_count_recognition, it)) }
        connection(ServiceCapability.Synthesis)?.models?.size?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.modelsettings_capability_count_synthesis, it)) }
        connection(ServiceCapability.Realtime)?.models?.size?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.modelsettings_capability_count_realtime, it)) }
    }
    val summary = counts.joinToString(" · ").ifEmpty { stringResource(R.string.modelsettings_summary_unconfigured) }
    return if (isEnabled) summary else stringResource(R.string.modelsettings_summary_paused_prefix, summary)
}

private const val PROTOCOL_LABEL_ANTHROPIC = "Messages"
private const val PROTOCOL_LABEL_OPENAI = "Completions"

// ---- Previews ----

private val capabilityIcons = mapOf(
    ServiceCapability.Conversation to Icons.AutoMirrored.Outlined.Chat,
    ServiceCapability.Recognition to Icons.Outlined.MicNone,
    ServiceCapability.Synthesis to Icons.AutoMirrored.Outlined.VolumeUp,
    ServiceCapability.Realtime to Icons.Outlined.GraphicEq,
)

@Suppress("unused")
private val flashIcon = Icons.Outlined.FlashOn

@Preview(name = "服务列表 · 浅色", showBackground = true, widthDp = 390, heightDp = 720, locale = "zh")
@Preview(
    name = "服务列表 · 深色", showBackground = true, widthDp = 390, heightDp = 720, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServiceListPreview() {
    GimiTheme {
        ModelServiceListScreen(
            state = ModelServiceListUiState(loadState = CatalogLoadState.Ready, items = ServiceListPreviewFixtures()),
            onAction = {},
            onNavigateToDetail = {},
            onAddService = {},
            onOpenDefaults = {},
        )
    }
}

@Preview(name = "空列表 · 浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(
    name = "空列表 · 深色", showBackground = true, widthDp = 390, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun EmptyServiceListPreview() {
    GimiTheme {
        ModelServiceListScreen(
            state = ModelServiceListUiState(loadState = CatalogLoadState.Ready),
            onAction = {},
            onNavigateToDetail = {},
            onAddService = {},
            onOpenDefaults = {},
        )
    }
}

@Preview(name = "窄屏大字", showBackground = true, widthDp = 320, fontScale = 1.3f, locale = "zh")
@Composable
private fun NarrowServiceListPreview() {
    GimiTheme {
        ModelServiceListScreen(
            state = ModelServiceListUiState(loadState = CatalogLoadState.Ready, items = ServiceListPreviewFixtures()),
            onAction = {},
            onNavigateToDetail = {},
            onAddService = {},
            onOpenDefaults = {},
        )
    }
}
