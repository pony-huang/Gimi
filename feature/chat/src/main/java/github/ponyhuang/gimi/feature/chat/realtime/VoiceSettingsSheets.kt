package github.ponyhuang.gimi.feature.chat.realtime

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.components.GimiPickerItemRow
import github.ponyhuang.gimi.ui.components.GimiSheetSearchRow
import github.ponyhuang.gimi.ui.components.GimiSheetTitleRow
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
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
    val service: LLMModelSetting,
    val group: ModelGroup,
    val model: Model,
    val capability: ModelCapability? = null,
    val isSupported: Boolean = true,
) {
    fun selection() = ModelSelection(service.id, group.id, model.id)
}

/**
 * 按能力筛选候选模型并标注服务是否接入该能力。
 *
 * 与默认模型设置页同一条规则：语音类模型按**模型自身声明的能力**逐行判定，
 * 而不是整批按同一个能力判定——专用转写（STT）与音频理解（ASR）是两套协议，
 * 同一个服务可能只接入其中一套。
 */
internal fun List<LLMModelSetting>.voiceModelRows(
    voiceCapabilities: VoiceCapabilityResolver,
    predicate: (Model) -> Boolean,
    requiredCapability: (Model) -> ModelCapability? = { null },
): List<VoiceModelRow> = flatMap { service ->
    service.groups.flatMap { group ->
        group.models.filter(predicate).map { model ->
            val capability = requiredCapability(model)
            VoiceModelRow(
                service = service,
                group = group,
                model = model,
                capability = capability,
                isSupported = capability == null ||
                    voiceCapabilities.isSupported(service.id, capability),
            )
        }
    }
}

/** 当前选中模型在候选行里的展示名；找不到时返回 null（未配置或已失效）。 */
internal fun List<VoiceModelRow>.selectionName(selection: ModelSelection?): String? =
    selection?.let { target -> firstOrNull { it.selection() == target }?.model?.name }

/** 模型选择抽屉的一个条目；`row == null` 表示 Realtime 选择器的「不使用」首项。 */
private data class VoiceModelOption(val row: VoiceModelRow?)

/**
 * 模型选择页：可搜索的候选列表，**抽屉内的一页**而非独立模态抽屉。
 *
 * 宿主把本页放进设置抽屉的 `AnimatedContent`：标题行返回箭头翻回来源设置页，
 * 标题行、搜索行与列表一起参与左右滑动过渡。点选后由宿主翻回设置页，
 * 抽屉保持打开、当前值随数据流原地更新。
 *
 * 候选规则与默认模型设置页一致（分组、能力标注、未接入置灰）；
 * Realtime 选择器通过 [allowNone] 提供一等「不使用」项（用于清除默认配置）。
 */
@Composable
internal fun VoiceModelPickerPage(
    title: String,
    rows: List<VoiceModelRow>,
    currentSelection: ModelSelection?,
    onPick: (ModelSelection?) -> Unit,
    onBack: () -> Unit,
    /** Realtime 选择器固定首项「不使用」；其余选择器不传。 */
    allowNone: Boolean = false,
    onPickNone: (() -> Unit)? = null,
) {
    // 列表在抽屉主体内滚动：Column 里的 LazyColumn 需要显式上限，否则会拿到无限高度。
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.6f).coerceAtMost(520.dp)
    var query by rememberSaveable { mutableStateOf("") }
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
    val noneTitle = stringResource(R.string.voice_settings_pick_realtime_none)

    Column(Modifier.fillMaxWidth()) {
        GimiSheetTitleRow(
            title = title,
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = stringResource(R.string.voice_sheet_back),
            onNavigationClick = onBack,
        )
        GimiSheetSearchRow(
            query = query,
            onQueryChange = { query = it },
            searchPlaceholder = stringResource(R.string.voice_settings_search_model),
        )
        // 写入后由宿主翻回来源设置页；抽屉本身不关闭，也不再叠出第二个遮罩。
        val commit: (VoiceModelOption) -> Unit = { option ->
            val row = option.row
            if (row == null) onPickNone?.invoke() else onPick(row.selection())
            onBack()
        }
        // 「不使用」始终可被搜到（关键词含 realtime），否则一搜就被过滤掉。
        val options = buildList {
            if (allowNone) add(VoiceModelOption(null))
            rows.sortedBy { it.capability?.ordinal ?: 0 }.forEach { add(VoiceModelOption(it)) }
        }
        val normalizedQuery = query.trim()
        val visible = options.filter { option ->
            normalizedQuery.isEmpty() || option.searchText(noneTitle)
                .contains(normalizedQuery, ignoreCase = true)
        }
        val groupCount: (String) -> Int = { serviceId ->
            options.asSequence()
                .map { it.row }
                .filterIsInstance<VoiceModelRow>()
                .filter { it.service.id == serviceId }
                .map { it.group.id }
                .distinct()
                .count()
        }
        when {
            rows.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.voice_settings_no_models),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visible.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(github.ponyhuang.gimi.core.designsystem.R.string.picker_no_search_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(visible, key = { option ->
                    option.row?.let { "${it.service.id}/${it.group.id}/${it.model.id}" } ?: "none"
                }) { option ->
                    GimiPickerItemRow(
                        title = option.row?.model?.name ?: noneTitle,
                        subtitle = voiceModelSubtitle(
                            row = option.row,
                            capabilityLabels = capabilityLabels,
                            unsupportedNotes = unsupportedNotes,
                            groupCount = groupCount,
                        ),
                        selected = when (val row = option.row) {
                            null -> currentSelection == null
                            else -> row.selection() == currentSelection
                        },
                        enabled = option.row?.isSupported != false,
                        onClick = { commit(option) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** 候选行的搜索文本：「不使用」项也带上 realtime 关键词。 */
private fun VoiceModelOption.searchText(noneTitle: String): String = when (val row = row) {
    null -> "$noneTitle realtime"
    else -> "${row.model.name} ${row.model.id} ${row.service.name} ${row.group.name}"
}

/**
 * 候选行的副标题：能力标注、服务（或服务 · 组）名与「未接入」说明；
 * 「不使用」项返回空串，由 [GimiPickerItemRow] 省略副标题行。
 */
private fun voiceModelSubtitle(
    row: VoiceModelRow?,
    capabilityLabels: Map<ModelCapability, String>,
    unsupportedNotes: Map<ModelCapability, String>,
    groupCount: (String) -> Int,
): String {
    if (row == null) return ""
    return buildList {
        row.capability?.let { capabilityLabels[it] }?.let(::add)
        // 服务内只有一个候选组时组名对每行都相同，只显示服务名（与设置页弹窗同规则）。
        val servicePart = if (groupCount(row.service.id) > 1) {
            "${row.service.name} · ${row.group.name}"
        } else {
            row.service.name
        }
        add(servicePart)
        if (!row.isSupported) row.capability?.let { unsupportedNotes[it] }?.let(::add)
    }.joinToString(" · ")
}

// ── 预览 ────────────────────────────────────────────────────

private fun previewModelRow(
    serviceId: String,
    serviceName: String,
    modelId: String,
    modelName: String,
    capability: ModelCapability,
    isSupported: Boolean = true,
): VoiceModelRow = VoiceModelRow(
    service = LLMModelSetting(
        id = serviceId,
        name = serviceName,
        isEnabled = true,
        apiKey = "preview-key",
        apiBaseUrl = "https://preview.example.com",
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "",
        groups = emptyList(),
    ),
    group = ModelGroup(id = "audio", name = "Audio", models = emptyList()),
    model = Model(id = modelId, name = modelName),
    capability = capability,
    isSupported = isSupported,
)

@Composable
private fun VoiceModelPickerPagePreviewHost(allowNone: Boolean) {
    val rows = listOf(
        previewModelRow("minimax", "MiniMax", "speech-02", "Speech-02", ModelCapability.STT),
        previewModelRow("qwen", "千问", "qwen3-asr-flash", "Qwen3 ASR Flash", ModelCapability.ASR),
        previewModelRow("mimo", "MiMo", "mimo-tts", "MiMo TTS", ModelCapability.TTS, isSupported = false),
    )
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                VoiceModelPickerPage(
                    title = "选择识别模型",
                    rows = rows,
                    currentSelection = ModelSelection("minimax", "audio", "speech-02"),
                    onPick = {},
                    onBack = {},
                    allowNone = allowNone,
                    onPickNone = {},
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
    VoiceModelPickerPagePreviewHost(allowNone = false)
}

@Preview(name = "模型选择页 · Realtime 含「不使用」", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoiceModelPickerPageWithNonePreview() {
    VoiceModelPickerPagePreviewHost(allowNone = true)
}
