package github.ponyhuang.gimi.feature.modelsettings.list

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import github.ponyhuang.gimi.feature.modelsettings.detail.ServiceListPreviewFixtures
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Tune
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 列表行的展示投影：图标与说明行都在这里推导，页面保持无状态。 */
internal data class ServiceListRow(
    val instance: ModelServiceInstance,
    /** 说明行；内置厂商由图标表达来源，仅自定义服务再写一段协议说明。 */
    val subtitle: String,
)

@Composable
fun ModelServiceListScreen(
    state: ModelServiceListUiState,
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
                if (state.items.isEmpty()) {
                    EmptyServiceList(onAddService)
                } else {
                    ServiceListContent(state.items, onNavigateToDetail, onOpenDefaults)
                }
            }
        }
    }
}

@Composable
private fun ServiceListContent(
    instances: List<ModelServiceInstance>,
    onNavigateToDetail: (String) -> Unit,
    onOpenDefaults: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PreferenceSectionTitle(stringResource(R.string.modelsettings_list_section_configured)) }
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
    // 与「添加服务商 · 内置服务商」共用同一行骨架：24dp 品牌图标 + 标题/说明两行，
    // 行高、内边距、分隔线内缩与尾部箭头都由 PreferenceListItem 统一给出。
    PreferenceListItem(
        title = row.instance.name,
        subtitle = row.subtitle,
        leadingIcon = { iconModifier ->
            LLMModelServiceIcon(row.instance.templateId.orEmpty(), iconModifier, contentPadding = 2.dp)
        },
        showDivider = showDivider,
        onClick = onClick,
    )
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

/** 说明行：来源（仅自定义服务）+ 能力摘要；停用服务整行加停用前缀。 */
@Composable
internal fun ModelServiceInstance.toListRow(): ServiceListRow = ServiceListRow(
    instance = this,
    subtitle = localizedSubtitle(),
)

@Composable
private fun ModelServiceInstance.localizedSubtitle(): String {
    val summary = localizedSummary()
    val source = localizedSourceLabel()
    val line = if (source == null) summary else "$source · $summary"
    return if (isEnabled) line else stringResource(R.string.modelsettings_summary_paused_prefix, line)
}

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

/** 用途摘要：非空能力连接的已选模型数。 */
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
    return counts.joinToString(" · ").ifEmpty { stringResource(R.string.modelsettings_summary_unconfigured) }
}

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
            onNavigateToDetail = {},
            onAddService = {},
            onOpenDefaults = {},
        )
    }
}
