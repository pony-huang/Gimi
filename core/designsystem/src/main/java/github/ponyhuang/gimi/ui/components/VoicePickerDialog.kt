package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.VoiceCategory
import github.ponyhuang.gimi.domain.voice.model.VoiceGender
import github.ponyhuang.gimi.domain.voice.model.traits
import github.ponyhuang.gimi.domain.voice.model.voiceGender
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 音色选择对话框：系统音色可选，音色 ID 也可直接填写。
 *
 * 两种来源共用一屏——用户不需要先判断自己要的是「标准音色」还是「自定义音色」。
 * 搜索有命中时按分类分组展示列表；无命中时把输入本身变成一个可选项；
 * 厂商没有内置音色表时（例如千问 Realtime）直接进入输入引导，不展示空列表。
 *
 * 外壳是统一的大尺寸选择对话框（与模型选择同款），搜索、筛选与新增入口
 * 都在对话框内部：自定义音色入口在搜索框尾部，不随列表滚动。
 *
 * 模块归属说明（AGENTS.md 共享豁免，与 `LLMModelServiceIcon` 同类）：
 * 本组件由 `:feature:chat`（语音页设置抽屉）与 `:feature:modelsettings`（默认模型页）共用，
 * 而 feature 之间禁止依赖，因此落在 `:core:designsystem`；公开签名因此携带
 * `:domain:voice` 的 `TtsVoice`/`VoiceCategory`/`VoiceGender` 领域类型。
 *
 * @param currentVoiceId 当前生效的音色 ID；为空表示使用厂商缺省音色。
 * @param options 当前模型可用的系统音色；列表已按模型过滤，组件不再做跨模型判断。
 * @param allowCustomInput 是否允许填写内置表之外的音色 ID（声音复刻、基础音色）。
 * @param isLoading 音色表是否仍在加载；加载中不把空列表当成「没有内置音色」。
 * @param defaultVoiceId 厂商缺省音色，仅在输入引导里作为提示展示。
 * @param namingTemplate 基础音色的命名模板说明，仅厂商提供时展示。
 * @param docsUrl 官方音色列表地址，仅厂商提供时展示跳转入口。
 * @param initialQuery 搜索框初值；生产调用保持空串，仅供预览构造「搜索中」状态。
 */
@Composable
fun VoicePickerDialog(
    title: String,
    currentVoiceId: String,
    options: List<TtsVoice>,
    allowCustomInput: Boolean,
    isLoading: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    defaultVoiceId: String = "",
    namingTemplate: String? = null,
    docsUrl: String? = null,
    onOpenUrl: (String) -> Unit = {},
    initialQuery: String = "",
) {
    var customMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // 没有内置音色表时直接落到输入态，避免出现一个永远空着的列表。
    val showInput = customMode || (!isLoading && options.isEmpty() && allowCustomInput)
    val canReturnToList = options.isNotEmpty()

    if (showInput) {
        VoiceInputDialog(
            title = stringResource(R.string.voice_picker_input_title),
            canReturnToList = canReturnToList,
            currentVoiceId = currentVoiceId,
            defaultVoiceId = defaultVoiceId,
            namingTemplate = namingTemplate,
            docsUrl = docsUrl,
            onConfirm = { voiceId ->
                onPick(voiceId)
                onDismiss()
            },
            onOpenUrl = onOpenUrl,
            onBack = { customMode = false },
            onDismiss = onDismiss,
        )
    } else {
        VoiceListDialog(
            title = title,
            currentVoiceId = currentVoiceId,
            options = options,
            isLoading = isLoading,
            allowCustomInput = allowCustomInput,
            query = query,
            onQueryChange = { query = it },
            onPick = { voiceId ->
                onPick(voiceId)
                onDismiss()
            },
            onOpenCustomInput = { customMode = true },
            onDismiss = onDismiss,
        )
    }
}

/** 音色列表态：统一选择对话框外壳 + 搜索 + 性别筛选 + 分类分组。 */
@Composable
private fun VoiceListDialog(
    title: String,
    currentVoiceId: String,
    options: List<TtsVoice>,
    isLoading: Boolean,
    allowCustomInput: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onOpenCustomInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    var genderFilter by rememberSaveable { mutableStateOf(VoiceGender.Unspecified) }

    // optionTitle/groupTitle 是普通 lambda，不能在内部调 @Composable 文案函数；
    // 在组合期先把性别与分类文案取好，供行渲染拼接。
    val genderLabels = VoiceGender.entries.associateWith { genderLabel(it) }
    val categoryLabels = VoiceCategory.entries.associateWith { categoryLabel(it) }

    val genders = options.map { it.voiceGender }
        .filter { it != VoiceGender.Unspecified }
        .distinct()
        .sortedBy { it.ordinal }
    val normalizedQuery = query.trim()
    val visible = options
        // 稳定排序保证同分类相邻，供分组标题合并。
        .sortedBy { it.category.ordinal }
        .filter { voice ->
            (genderFilter == VoiceGender.Unspecified || voice.voiceGender == genderFilter) &&
                (normalizedQuery.isEmpty() || voice.searchText().contains(normalizedQuery, ignoreCase = true))
        }

    PickerSingleChoiceDialog(
        options = visible,
        selected = { it.id == currentVoiceId },
        key = { it.id },
        title = title,
        optionTitle = { it.name },
        optionSubtitle = { voiceSubtitle(it, genderLabels) },
        emptyText = stringResource(R.string.voice_picker_no_voices),
        onPick = { onPick(it.id) },
        onDismiss = onDismiss,
        searchField = {
            GimiSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.voice_picker_search),
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, stringResource(R.string.picker_clear_search))
                            }
                        }
                        if (allowCustomInput) {
                            // 自定义音色入口常驻搜索框尾部：框内尾部留给「清除搜索」，两者并存。
                            IconButton(onClick = onOpenCustomInput) {
                                Icon(Icons.Default.Add, stringResource(R.string.voice_picker_manual_entry))
                            }
                        }
                    }
                },
            )
        },
        filterBar = {
            Column {
                CurrentVoiceLabel(currentVoiceId, options)
                if (genders.size > 1) {
                    // 筛选条可以横向滚动：性别种类越多按钮越密，窄屏或大字号下最后一个筛选项
                    // 会被挤出可视区，表现为「某个类型切不了」。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GenderChip(
                            label = stringResource(R.string.voice_filter_all),
                            selected = genderFilter == VoiceGender.Unspecified,
                            onClick = { genderFilter = VoiceGender.Unspecified },
                        )
                        genders.forEach { gender ->
                            GenderChip(
                                label = genderLabel(gender),
                                selected = genderFilter == gender,
                                onClick = { genderFilter = gender },
                            )
                        }
                    }
                }
            }
        },
        groupTitle = { categoryLabels[it.category] },
        emptyContent = { VoiceEmptyState(isLoading, normalizedQuery, allowCustomInput, onPick) },
    )
}

/** 搜索/加载的空态：加载中显示进度；无命中且允许自定义时把输入变成可选项。 */
@Composable
private fun VoiceEmptyState(
    isLoading: Boolean,
    query: String,
    allowCustomInput: Boolean,
    onPick: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()

            query.isEmpty() -> Text(
                // 没有命中任何筛选：说「没有匹配」会把问题指向搜索框，而不是配置本身。
                text = stringResource(R.string.voice_picker_no_voices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.voice_picker_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (allowCustomInput) {
                    Button(
                        onClick = { onPick(query) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.voice_picker_use_query, query))
                    }
                }
            }
        }
    }
}

/** 当前生效音色的提示行：缺省、系统音色或自定义 ID 三种形态。 */
@Composable
private fun CurrentVoiceLabel(currentVoiceId: String, options: List<TtsVoice>) {
    val current = options.firstOrNull { it.id == currentVoiceId }
    Text(
        text = when {
            currentVoiceId.isBlank() -> stringResource(R.string.voice_picker_current_none)
            current != null -> stringResource(R.string.voice_picker_current, current.name)
            else -> stringResource(R.string.voice_picker_current_custom, currentVoiceId)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * 音色 ID 输入态对话框。
 *
 * 只在厂商允许自定义音色或没有内置音色表时出现；留空表示回到该模型的缺省音色，
 * 因此确定按钮始终可用，不需要额外的「清除」入口。
 */
@Composable
private fun VoiceInputDialog(
    title: String,
    canReturnToList: Boolean,
    currentVoiceId: String,
    defaultVoiceId: String,
    namingTemplate: String?,
    docsUrl: String?,
    onConfirm: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(currentVoiceId) }
    val defaultHint: (@Composable () -> Unit)? = if (defaultVoiceId.isBlank()) {
        null
    } else {
        { Text(stringResource(R.string.voice_picker_default_hint, defaultVoiceId)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            // 标题居中；返回按钮贴左，占位留在标题左侧，标题不会与它重叠。
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = if (canReturnToList) 48.dp else 0.dp),
                )
                if (canReturnToList) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.voice_picker_back))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.voice_picker_input_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                PreferenceTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.voice_picker_input_label)) },
                    supportingText = defaultHint,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (namingTemplate != null) {
                    Text(
                        text = stringResource(
                            R.string.voice_picker_naming_template,
                            namingTemplate,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (docsUrl != null) {
                    TextButton(
                        onClick = { onOpenUrl(docsUrl) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.voice_picker_open_docs))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(input.trim()) }) {
                Text(stringResource(R.string.voice_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
        },
    )
}

/** 选择器只展示名称、性别与特质，其余字段全部并入搜索文本。 */
private fun TtsVoice.searchText(): String =
    listOfNotNull(name, id, localName, language, gender, description).joinToString(" ")

/** 副标题只保留能帮用户区分声音的两项信息：性别与声线特质（小语种则以语种代替）。 */
private fun voiceSubtitle(voice: TtsVoice, genderLabels: Map<VoiceGender, String>): String {
    val traitText = voice.traits.joinToString("、").ifBlank { voice.language.orEmpty() }
    return listOfNotNull(
        genderLabels[voice.voiceGender].orEmpty().ifBlank { null },
        traitText.ifBlank { null },
    ).joinToString(" · ")
}

@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun genderLabel(gender: VoiceGender): String = when (gender) {
    VoiceGender.Female -> stringResource(R.string.voice_gender_female)
    VoiceGender.Male -> stringResource(R.string.voice_gender_male)
    VoiceGender.Child -> stringResource(R.string.voice_gender_child)
    VoiceGender.Unspecified -> ""
}

@Composable
private fun categoryLabel(category: VoiceCategory): String? = when (category) {
    VoiceCategory.Flagship -> stringResource(R.string.voice_group_flagship)
    VoiceCategory.Multilingual -> stringResource(R.string.voice_group_multilingual)
    VoiceCategory.QualityChinese -> stringResource(R.string.voice_group_quality_chinese)
    VoiceCategory.QualityEnglish -> stringResource(R.string.voice_group_quality_english)
    VoiceCategory.Other -> stringResource(R.string.voice_group_other)
    VoiceCategory.Minority -> stringResource(R.string.voice_group_minority)
    VoiceCategory.Unspecified -> null
}

// ── 预览 ────────────────────────────────────────────────────

private fun voicePickerPreviewVoices(): List<TtsVoice> = listOf(
    TtsVoice("longanlingxin_v3.1", "龙安灵心", "中文、英文", "女", "知心温暖 · 社交陪伴", VoiceCategory.Flagship),
    TtsVoice("xunanchuan_v3.1", "许南川", "中文、英文", "男", "磁性、真诚 · 社交陪伴", VoiceCategory.Flagship),
    TtsVoice("longanhuan_v3.1", "龙安欢", "中文、英文", "女", "开朗、热情 · 社交陪伴", VoiceCategory.Flagship),
    TtsVoice("yuxiaoyun_v3.1", "于小云", "中文（普通话）", "女", "元气、亲切、自然 · 广告营销", VoiceCategory.QualityChinese),
    TtsVoice("qiaoxiaojiao_v3.1", "乔小娇", "中文（普通话）", "女", "俏丽、可爱 · 有声书", VoiceCategory.QualityChinese),
    TtsVoice("Emily_v3.1", "Emily", "英文", "女", "英式女声", VoiceCategory.QualityEnglish),
    TtsVoice("longke_v3.1", "龙可", "中文（普通话）", "儿童", "懵懂 · 童声", VoiceCategory.Other),
    TtsVoice(
        "qwen-audio-3.1-tts-flash-zora_bg",
        "Zora",
        "保加利亚语",
        "男",
        null,
        VoiceCategory.Minority,
        localName = "Зора",
    ),
)

@Preview(name = "音色对话框 · 浅色", showBackground = true, widthDp = 720, heightDp = 900)
@Preview(
    name = "音色对话框 · 深色",
    showBackground = true,
    widthDp = 720,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerDialogPreview() {
    GimiTheme {
        VoicePickerDialog(
            title = "播放音色",
            currentVoiceId = "longanlingxin_v3.1",
            options = voicePickerPreviewVoices(),
            allowCustomInput = true,
            isLoading = false,
            onPick = {},
            onDismiss = {},
            namingTemplate = "qwen-audio-3.0-tts-{plus|flash}-{后缀}",
            docsUrl = "https://example.com",
        )
    }
}

@Preview(name = "当前值为自定义", showBackground = true, widthDp = 720, heightDp = 900)
@Composable
private fun VoicePickerDialogCustomValuePreview() {
    GimiTheme {
        VoicePickerDialog(
            title = "播放音色",
            currentVoiceId = "qwen-audio-3.0-tts-flash-abc123",
            options = voicePickerPreviewVoices(),
            allowCustomInput = true,
            isLoading = false,
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "不允许自定义输入 · 无加号", showBackground = true, widthDp = 720, heightDp = 900)
@Composable
private fun VoicePickerDialogNoCustomInputPreview() {
    GimiTheme {
        VoicePickerDialog(
            title = "播放音色",
            currentVoiceId = "longanlingxin_v3.1",
            options = voicePickerPreviewVoices(),
            allowCustomInput = false,
            isLoading = false,
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "搜索中 · 清除与加号并存", showBackground = true, widthDp = 720, heightDp = 900)
@Preview(
    name = "搜索中 · 深色",
    showBackground = true,
    widthDp = 720,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerDialogSearchPreview() {
    GimiTheme {
        VoicePickerDialog(
            title = "播放音色",
            currentVoiceId = "longanlingxin_v3.1",
            options = voicePickerPreviewVoices(),
            allowCustomInput = true,
            isLoading = false,
            onPick = {},
            onDismiss = {},
            initialQuery = "龙",
        )
    }
}

@Preview(name = "无内置音色 · 输入引导", showBackground = true, widthDp = 720, heightDp = 900)
@Preview(
    name = "无内置音色 · 深色",
    showBackground = true,
    widthDp = 720,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerDialogInputPreview() {
    GimiTheme {
        VoicePickerDialog(
            title = "会话音色",
            currentVoiceId = "",
            options = emptyList(),
            allowCustomInput = true,
            isLoading = false,
            onPick = {},
            onDismiss = {},
            defaultVoiceId = "longanqian_v3.1",
            namingTemplate = "qwen-audio-3.0-tts-{plus|flash}-{后缀}",
            docsUrl = "https://example.com",
        )
    }
}
