package github.ponyhuang.gimi.feature.chat.realtime

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.components.GimiPickerItemRow
import github.ponyhuang.gimi.ui.components.GimiSearchableSheetPage
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 语音设置抽屉的设置条目归属；决定二级页内容与写入目标。 */
enum class VoiceSettingsKind { Speech, Tts, Realtime }

/**
 * 语音设置模型选择器中的一行。
 *
 * 与默认模型设置页的 `SelectableModelRow` 同语义，但独立落在 `:feature:chat`：
 * feature 之间禁止依赖，模型目录类型全部来自 `:domain:modelcatalog`。
 *
 * @property capability 该行所需能力；有值时用于副标题与「未接入」标注。
 * @property isSupported 该模型所属服务是否已接入目标能力；未接入时仍显示但不可选。
 */
data class VoiceModelRow(
    val instance: ModelServiceInstance,
    val connection: github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection,
    val model: Model,
    val capability: ModelCapability? = null,
    val isSupported: Boolean = true,
) {
    fun selection() = ModelSelection(instance.id, connection.id, model.id)
}

/**
 * 按能力筛选候选模型并标注服务是否接入该能力。
 *
 * 与默认模型设置页同一条规则：语音类模型按**模型自身声明的能力**逐行判定，
 * 而不是整批按同一个能力判定——专用转写（STT）与音频理解（ASR）是两套协议，
 * 同一个服务可能只接入其中一套。
 */
internal fun List<ModelServiceInstance>.voiceModelRows(
    voiceCapabilities: VoiceCapabilityResolver,
    predicate: (Model) -> Boolean,
    requiredCapability: (Model) -> ModelCapability? = { null },
): List<VoiceModelRow> = flatMap { instance ->
    instance.connections.flatMap { connection ->
        val templateId = instance.templateId
        connection.models.filter(predicate).map { model ->
            val capability = requiredCapability(model)
            VoiceModelRow(
                instance = instance,
                connection = connection,
                model = model,
                capability = capability,
                isSupported = capability == null ||
                    (templateId != null && voiceCapabilities.isSupported(templateId, capability)),
            )
        }
    }
}.filter { resolveConnection(it.selection()) != null }

/**
 * 当前选中模型在候选行里的完整行；展示名与厂商品牌图标都从这一行取。
 * 找不到时返回 null（未配置、凭据失效或模型已从目录移除），调用方回落占位。
 */
internal fun List<VoiceModelRow>.selectionRow(selection: ModelSelection?): VoiceModelRow? =
    selection?.let { target -> firstOrNull { it.isSupported && it.selection() == target } }

/**
 * 模型选择页：可搜索的候选列表，**抽屉内的一页**而非独立模态抽屉。
 *
 * 宿主把本页放进设置抽屉的 `AnimatedContent`：标题行返回箭头翻回来源设置页，
 * 标题行、搜索胶囊与列表由 [GimiSearchableSheetPage] 骨架统一给出——搜索不再
 * 常驻，点标题行尾的搜索图标原位展开成胶囊输入框，抽屉升高、列表收起。
 * 点选后由宿主翻回设置页，抽屉保持打开、当前值随数据流原地更新。
 *
 * 候选规则与默认模型设置页一致（能力标注、未接入置灰）；
 * 只列真实模型，没有候选时展示空态，不提供「不使用 Realtime」占位项。
 */
@Composable
internal fun VoiceModelPickerPage(
    title: String,
    rows: List<VoiceModelRow>,
    currentSelection: ModelSelection?,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onPick: (ModelSelection) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    // 列表在抽屉主体内滚动：Column 里的 LazyColumn 需要显式上限，否则会拿到无限高度。
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.6f).coerceAtMost(520.dp)
    var query by rememberSaveable { mutableStateOf("") }
    // 退出搜索时统一清词：无论从胶囊 ✕ 还是宿主返回键退出，下次展开都不残留旧词。
    var wasSearching by remember { mutableStateOf(isSearching) }
    LaunchedEffect(isSearching) {
        if (wasSearching && !isSearching) query = ""
        wasSearching = isSearching
    }
    // 能力名与「未接入」说明在组合函数里取好：行渲染的副标题不是 @Composable lambda。
    val sttLabel = stringResource(R.string.voice_capability_stt)
    val asrLabel = stringResource(R.string.voice_capability_asr)
    val ttsLabel = stringResource(R.string.voice_capability_tts)
    val capabilityLabels = mapOf(
        ModelCapability.STT to sttLabel,
        ModelCapability.ASR to asrLabel,
        ModelCapability.TTS to ttsLabel,
    )
    val unsupportedNotes = mapOf(
        ModelCapability.STT to stringResource(R.string.voice_capability_not_supported, sttLabel),
        ModelCapability.ASR to stringResource(R.string.voice_capability_not_supported, asrLabel),
        ModelCapability.TTS to stringResource(R.string.voice_capability_not_supported, ttsLabel),
    )
    // 每个实例的语音连接单层展示，副标题不再按连接细分。
    val options = rows.sortedBy { it.capability?.ordinal ?: 0 }
    val commit: (VoiceModelRow) -> Unit = { option ->
        onPick(option.selection())
        onBack()
    }

    GimiSearchableSheetPage(
        title = title,
        isSearching = isSearching,
        query = query,
        onQueryChange = { query = it },
        onOpenSearch = { onSearchingChange(true) },
        onExitSearch = { onSearchingChange(false) },
        onBack = onBack,
        onClose = onClose,
        listContent = {
            when {
                rows.isEmpty() -> VoiceModelEmptyText(
                    text = stringResource(R.string.voice_settings_no_models),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(options, key = {
                        "${it.instance.id}/${it.connection.id}/${it.model.id}"
                    }) { option ->
                        VoiceModelRowItem(
                            option = option,
                            currentSelection = currentSelection,
                            capabilityLabels = capabilityLabels,
                            unsupportedNotes = unsupportedNotes,
                            onPick = { commit(option) },
                        )
                    }
                }
            }
        },
        searchContent = {
            val normalizedQuery = query.trim()
            val visible = options.filter { option ->
                option.searchText().contains(normalizedQuery, ignoreCase = true)
            }
            when {
                rows.isEmpty() -> VoiceModelEmptyText(
                    text = stringResource(R.string.voice_settings_no_models),
                    modifier = Modifier.fillMaxSize(),
                )

                normalizedQuery.isEmpty() -> VoiceModelEmptyText(
                    text = stringResource(R.string.voice_settings_search_hint),
                    modifier = Modifier.fillMaxSize(),
                )

                visible.isEmpty() -> VoiceModelEmptyText(
                    text = stringResource(github.ponyhuang.gimi.core.designsystem.R.string.picker_no_search_results),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    // 搜索结果平铺展示；候选行按能力排序的顺序保持不变。
                    items(visible, key = {
                        "${it.instance.id}/${it.connection.id}/${it.model.id}"
                    }) { option ->
                        VoiceModelRowItem(
                            option = option,
                            currentSelection = currentSelection,
                            capabilityLabels = capabilityLabels,
                            unsupportedNotes = unsupportedNotes,
                            onPick = { commit(option) },
                        )
                    }
                }
            }
        },
    )
}

/** 模型选择页的空态/引导文案：搜索态撑满主体，列表态保持 120dp 起步高度。 */
@Composable
private fun VoiceModelEmptyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceModelRowItem(
    option: VoiceModelRow,
    currentSelection: ModelSelection?,
    capabilityLabels: Map<ModelCapability, String>,
    unsupportedNotes: Map<ModelCapability, String>,
    onPick: () -> Unit,
) {
    GimiPickerItemRow(
        title = option.model.name,
        subtitle = voiceModelSubtitle(
            row = option,
            capabilityLabels = capabilityLabels,
            unsupportedNotes = unsupportedNotes,
        ),
        selected = option.selection() == currentSelection,
        enabled = option.isSupported,
        onClick = onPick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        // 与聊天模型选择页同一套品牌图标：模板 ID 决定厂商，
        // 自建服务没有模板 ID，回落组件内置占位图。
        leading = {
            LLMModelServiceIcon(
                serviceId = option.instance.templateId.orEmpty(),
                modifier = Modifier.size(28.dp),
                contentPadding = 3.dp,
            )
        },
    )
}

/** 候选行的搜索文本：模型名、模型 ID 与服务名。 */
private fun VoiceModelRow.searchText(): String = "${model.name} ${model.id} ${instance.name}"

/** 候选行的副标题：能力标注、服务（或服务 · 组）名与「未接入」说明。 */
private fun voiceModelSubtitle(
    row: VoiceModelRow,
    capabilityLabels: Map<ModelCapability, String>,
    unsupportedNotes: Map<ModelCapability, String>,
): String {
    return buildList {
        row.capability?.let { capabilityLabels[it] }?.let(::add)
        add(row.instance.name)
        if (!row.isSupported) row.capability?.let { unsupportedNotes[it] }?.let(::add)
    }.joinToString(" · ")
}

// ── 预览 ────────────────────────────────────────────────────

private fun previewModelRow(
    instanceId: String,
    instanceName: String,
    modelId: String,
    modelName: String,
    capability: ModelCapability,
    isSupported: Boolean = true,
): VoiceModelRow {
    val instance = ModelServiceInstance(
        id = instanceId,
        name = instanceName,
        // 预览用真实模板 ID，品牌图标才不会整屏回落占位图。
        templateId = instanceId.removePrefix("instance-"),
        isEnabled = true,
        isOfficialToolsEnabled = true,
        sharedApiKey = "preview-key",
        connections = listOf(
            github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection(
                id = "$instanceId-connection",
                capability = github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Recognition,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://preview.example.com",
            ),
        ),
    )
    return VoiceModelRow(
        instance = instance,
        connection = instance.connections.first(),
        model = Model(id = modelId, name = modelName),
        capability = capability,
        isSupported = isSupported,
    )
}

@Composable
private fun VoiceModelPickerPagePreviewHost() {
    val rows = listOf(
        previewModelRow("instance-minimax", "MiniMax", "speech-02", "Speech-02", ModelCapability.STT),
        previewModelRow("instance-qwen", "千问", "qwen3-asr-flash", "Qwen3 ASR Flash", ModelCapability.ASR),
        previewModelRow("instance-mimo", "MiMo", "mimo-tts", "MiMo TTS", ModelCapability.TTS, isSupported = false),
    )
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                VoiceModelPickerPage(
                    title = "选择识别模型",
                    rows = rows,
                    currentSelection = ModelSelection("instance-minimax", "instance-minimax-connection", "speech-02"),
                    isSearching = false,
                    onSearchingChange = {},
                    onPick = {},
                    onBack = {},
                    onClose = {},
                )
            }
        }
    }
}

@Preview(name = "模型选择页 · 浅色", showBackground = true, widthDp = 390, heightDp = 700)
@Preview(
    name = "模型选择页 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoiceModelPickerPagePreview() {
    VoiceModelPickerPagePreviewHost()
}

/** 没有候选模型就是没有：空态不提供任何占位选项。 */
@Preview(name = "模型选择页 · 无候选", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoiceModelPickerPageEmptyPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                VoiceModelPickerPage(
                    title = "选择 Realtime 模型",
                    rows = emptyList(),
                    currentSelection = null,
                    isSearching = false,
                    onSearchingChange = {},
                    onPick = {},
                    onBack = {},
                    onClose = {},
                )
            }
        }
    }
}
