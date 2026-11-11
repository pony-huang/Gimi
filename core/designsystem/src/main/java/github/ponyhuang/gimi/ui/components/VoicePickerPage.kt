package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.VoiceCategory
import github.ponyhuang.gimi.domain.voice.model.VoiceGender
import github.ponyhuang.gimi.domain.voice.model.traits
import github.ponyhuang.gimi.domain.voice.model.voiceGender
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 音色选择页：系统音色可选，音色 ID 也可直接填写。
 *
 * 这是**抽屉内的一页**，不自带 [GimiSheetBase] 外壳：宿主把本页放进同一个抽屉的
 * `AnimatedContent` 里，点返回箭头翻回来源设置页，而不是叠出第二个模态抽屉。
 * 标题行、搜索行与列表一起参与页面切换动画。
 *
 * 两种来源共用一屏——用户不需要先判断自己要的是「标准音色」还是「自定义音色」。
 * 搜索有命中时按分类分组展示列表；无命中时把输入本身变成一个可选项；
 * 厂商没有内置音色表时（例如千问 Realtime）直接进入输入引导，不展示空列表。
 *
 * 列表态与「新增音色 ID」输入态同属这一页的内部层级：点搜索框内的加号后，
 * 用与宿主翻页同款的左右滑动切换过去，标题行一起移动、抽屉高度随内容平滑收缩，
 * 而不是把整块内容硬替换掉。返回箭头按层级退回列表，列表态再退回宿主的来源页。
 *
 * 自定义音色入口常驻搜索框内尾部，与「清除搜索」并存，不占用主体的可视高度。
 *
 * 模块归属说明（AGENTS.md 共享豁免，与 `LLMModelServiceIcon` 同类）：
 * 本组件与 `VoicePickerDialog` 共用同一套音色选择 UI 与 `voice_picker_*` 文案/领域类型，
 * 落在 `:core:designsystem` 避免两套实现分叉；页面版由 `:feature:chat` 消费，
 * 对话框版由 `:feature:modelsettings` 消费。
 *
 * @param currentVoiceId 当前生效的音色 ID；为空表示使用厂商缺省音色。
 * @param options 当前模型可用的系统音色；列表已按模型过滤，组件不再做跨模型判断。
 * @param allowCustomInput 是否允许填写内置表之外的音色 ID（声音复刻、基础音色）。
 * @param isLoading 音色表是否仍在加载；加载中不把空列表当成「没有内置音色」。
 * @param onPick 选中音色；调用后宿主负责翻回上一级页面。
 * @param onBack 标题行返回：输入态先回音色列表，列表态回来源设置页。
 * @param defaultVoiceId 厂商缺省音色，仅在输入引导里作为提示展示。
 * @param namingTemplate 基础音色的命名模板说明，仅厂商提供时展示。
 * @param docsUrl 官方音色列表地址，仅厂商提供时展示跳转入口。
 * @param initialQuery 搜索框初值；生产调用保持空串，仅供预览构造「搜索中」状态。
 * @param initialCustomMode 是否直接展示输入态；生产调用保持 false，仅供预览构造「新增音色 ID」状态。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePickerPage(
    title: String,
    currentVoiceId: String,
    options: List<TtsVoice>,
    allowCustomInput: Boolean,
    isLoading: Boolean,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    defaultVoiceId: String = "",
    namingTemplate: String? = null,
    docsUrl: String? = null,
    onOpenUrl: (String) -> Unit = {},
    initialQuery: String = "",
    initialCustomMode: Boolean = false,
) {
    // 列表在抽屉主体内滚动：Column 里的 LazyColumn 需要显式上限，否则会拿到无限高度。
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.6f).coerceAtMost(520.dp)
    var customMode by rememberSaveable { mutableStateOf(initialCustomMode) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // 没有内置音色表时直接落到输入态，避免出现一个永远空着的列表。
    val showInput = customMode || (!isLoading && options.isEmpty() && allowCustomInput)
    val canReturnToList = options.isNotEmpty()

    // 过场中的旧页面还会短暂存在，回调必须指向最新一次组合的宿主状态，不能用快照值。
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
    // 提交后由宿主翻回来源设置页：抽屉保持打开，选中值随数据流在设置页原地更新。
    val commit: (String) -> Unit = { voiceId ->
        currentOnPick(voiceId)
        currentOnBack()
    }
    val inputTitle = stringResource(R.string.voice_picker_input_title)
    val backDescription = stringResource(R.string.voice_picker_back)
    val searchPlaceholder = stringResource(R.string.voice_picker_search)
    val manualEntryDescription = stringResource(R.string.voice_picker_manual_entry)

    // 与宿主抽屉翻页同一套过渡：新增音色 ID 向左滑入，返回向右滑回，标题行一起移动
    // 而不是原地换字。SizeTransform 让长列表与短输入面板之间平滑变高/变矮——抽屉贴着
    // 内容长，没有它就会在切换瞬间突然抽高或塌下去。
    AnimatedContent(
        targetState = showInput,
        transitionSpec = {
            val towards = if (targetState) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            ContentTransform(
                targetContentEnter = slideIntoContainer(towards = towards, animationSpec = tween(220)) + fadeIn(tween(160)),
                initialContentExit = slideOutOfContainer(towards = towards, animationSpec = tween(220)) + fadeOut(tween(120)),
                sizeTransform = SizeTransform(clip = true),
            )
        },
        label = "voice-picker-input",
        modifier = Modifier.fillMaxWidth(),
    ) { inputMode ->
        Column(Modifier.fillMaxWidth()) {
            GimiSheetTitleRow(
                title = if (inputMode) inputTitle else title,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = backDescription,
                onNavigationClick = {
                    // 输入态的返回只退出输入面板；列表态的返回交给宿主翻回设置页。
                    if (inputMode && canReturnToList) customMode = false else currentOnBack()
                },
            )
            if (inputMode) {
                VoiceInputPanel(
                    initialVoiceId = currentVoiceId,
                    defaultVoiceId = defaultVoiceId,
                    namingTemplate = namingTemplate,
                    docsUrl = docsUrl,
                    onConfirm = commit,
                    onOpenUrl = currentOnOpenUrl,
                )
            } else {
                // 自定义音色入口与「清除搜索」并存于搜索框内尾部，与对话框版保持同一布局。
                GimiSheetSearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = searchPlaceholder,
                    trailing = {
                        if (allowCustomInput) {
                            GimiSheetIconAction(
                                icon = Icons.Default.Add,
                                contentDescription = manualEntryDescription,
                                onClick = { customMode = true },
                            )
                        }
                    },
                )
                VoiceListPanel(
                    currentVoiceId = currentVoiceId,
                    options = options,
                    isLoading = isLoading,
                    query = query,
                    listMaxHeight = listMaxHeight,
                    onPick = commit,
                )
            }
        }
    }
}

@Composable
private fun VoiceListPanel(
    currentVoiceId: String,
    options: List<TtsVoice>,
    isLoading: Boolean,
    query: String,
    listMaxHeight: Dp,
    onPick: (String) -> Unit,
) {
    var genderFilter by rememberSaveable { mutableStateOf(VoiceGender.Unspecified) }

    val genders = options.map { it.voiceGender }
        .filter { it != VoiceGender.Unspecified }
        .distinct()
        .sortedBy { it.ordinal }
    val normalizedQuery = query.trim()
    val visible = options.filter { voice ->
        (genderFilter == VoiceGender.Unspecified || voice.voiceGender == genderFilter) &&
            (normalizedQuery.isEmpty() || voice.searchText().contains(normalizedQuery, ignoreCase = true))
    }
    val current = options.firstOrNull { it.id == currentVoiceId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                currentVoiceId.isBlank() -> stringResource(
                    R.string.voice_picker_current_none,
                )
                current != null -> stringResource(
                    R.string.voice_picker_current,
                    current.name,
                )
                else -> stringResource(
                    R.string.voice_picker_current_custom,
                    currentVoiceId,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 与选项卡片内的文字对齐（卡片外边距 16dp + 行内边距 16dp）。
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        )
        if (genders.size > 1) {
            // 筛选条可以横向滚动：性别种类越多按钮越密，窄屏或大字号下最后一个筛选项
            // 会被挤出可视区，表现为「某个类型切不了」。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            visible.isEmpty() -> NoMatchState(
                query = normalizedQuery,
                allowCustomInput = false,
                onPick = onPick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                VoiceCategory.values().forEach { category ->
                    val voices = visible.filter { it.category == category }
                    if (voices.isEmpty()) return@forEach
                    item(key = "header/${category.name}") {
                        VoiceGroupHeader(category)
                    }
                    items(voices, key = { it.id }) { voice ->
                        // 副标题只保留能帮用户区分声音的两项信息：性别与声线特质（小语种则以语种代替）。
                        val traitText = voice.traits.joinToString("、").ifBlank { voice.language.orEmpty() }
                        val subtitle = listOfNotNull(
                            genderLabel(voice.voiceGender).ifBlank { null },
                            traitText.ifBlank { null },
                        ).joinToString(" · ")
                        GimiPickerItemRow(
                            title = voice.name,
                            subtitle = subtitle,
                            secondaryTitle = voice.localName,
                            selected = voice.id == currentVoiceId,
                            onClick = { onPick(voice.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索无命中时的出口：直接把用户输入当作自定义音色。 */
@Composable
private fun NoMatchState(
    query: String,
    allowCustomInput: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (query.isBlank()) {
                // 厂商没给这个模型准备音色表：说「没有匹配」会把问题指向搜索框，而不是配置本身。
                stringResource(R.string.voice_picker_no_voices)
            } else {
                stringResource(R.string.voice_picker_no_match)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allowCustomInput && query.isNotEmpty()) {
            Button(
                onClick = { onPick(query) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.voice_picker_use_query, query))
            }
        }
    }
}

@Composable
private fun VoiceGroupHeader(category: VoiceCategory) {
    val label = categoryLabel(category) ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 分组标题与选项卡片内的文字对齐（卡片外边距 16dp + 行内边距 16dp）。
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 4.dp),
    )
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

/**
 * 音色 ID 输入态。
 *
 * 只在厂商允许自定义音色时出现；留空表示回到该模型的缺省音色，
 * 因此确定按钮始终可用，不需要额外的「清除」入口。
 */
@Composable
private fun VoiceInputPanel(
    initialVoiceId: String,
    defaultVoiceId: String,
    namingTemplate: String?,
    docsUrl: String?,
    onConfirm: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(initialVoiceId) }
    val defaultHint: (@Composable () -> Unit)? = if (defaultVoiceId.isBlank()) {
        null
    } else {
        { Text(stringResource(R.string.voice_picker_default_hint, defaultVoiceId)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
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
                modifier = Modifier.padding(top = 4.dp, start = 0.dp),
            ) {
                Text(stringResource(R.string.voice_picker_open_docs))
            }
        }
        Button(
            onClick = { onConfirm(input.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.voice_picker_confirm))
        }
    }
}

/** 选择器只展示名称、性别与特质，其余字段全部并入搜索文本。 */
private fun TtsVoice.searchText(): String =
    listOfNotNull(name, id, localName, language, gender, description).joinToString(" ")

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

/** 预览按抽屉页面的真实底色渲染，直接展示标题行 + 搜索行 + 列表，不套模态外壳。 */
@Composable
private fun VoicePickerPagePreviewHost(
    currentVoiceId: String,
    options: List<TtsVoice>,
    allowCustomInput: Boolean,
    initialQuery: String = "",
    title: String = "播放音色",
    defaultVoiceId: String = "",
    namingTemplate: String? = null,
    docsUrl: String? = null,
    initialCustomMode: Boolean = false,
) {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            VoicePickerPage(
                title = title,
                currentVoiceId = currentVoiceId,
                options = options,
                allowCustomInput = allowCustomInput,
                isLoading = false,
                onPick = {},
                onBack = {},
                defaultVoiceId = defaultVoiceId,
                namingTemplate = namingTemplate,
                docsUrl = docsUrl,
                initialQuery = initialQuery,
                initialCustomMode = initialCustomMode,
            )
        }
    }
}

@Preview(name = "音色选择页 · 浅色", showBackground = true, widthDp = 390, heightDp = 700)
@Preview(
    name = "音色选择页 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerPagePreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "longanlingxin_v3.1",
        options = voicePickerPreviewVoices(),
        allowCustomInput = true,
        namingTemplate = "qwen-audio-3.0-tts-{plus|flash}-{后缀}",
        docsUrl = "https://example.com",
    )
}

@Preview(name = "音色选择页 · 当前值为自定义", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoicePickerPageCustomValuePreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "qwen-audio-3.0-tts-flash-abc123",
        options = voicePickerPreviewVoices(),
        allowCustomInput = true,
    )
}

@Preview(name = "音色选择页 · 不允许自定义输入（无加号）", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoicePickerPageNoCustomInputPreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "longanlingxin_v3.1",
        options = voicePickerPreviewVoices(),
        allowCustomInput = false,
    )
}

/** 列表 → 输入态滑动过渡的终点：有内置音色时返回箭头只退回列表，不关闭抽屉。 */
@Preview(name = "音色选择页 · 新增音色 ID 输入态", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoicePickerPageCustomInputPreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "qwen-audio-3.0-tts-flash-abc123",
        options = voicePickerPreviewVoices(),
        allowCustomInput = true,
        initialCustomMode = true,
        namingTemplate = "qwen-audio-3.0-tts-{plus|flash}-{后缀}",
        docsUrl = "https://example.com",
    )
}

@Preview(name = "音色选择页 · 搜索中（清除与加号并存）", showBackground = true, widthDp = 390, heightDp = 700)
@Preview(
    name = "音色选择页 · 搜索中 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerPageSearchPreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "longanlingxin_v3.1",
        options = voicePickerPreviewVoices(),
        allowCustomInput = true,
        initialQuery = "龙",
    )
}

@Preview(name = "音色选择页 · 无内置音色（输入引导）", showBackground = true, widthDp = 390, heightDp = 700)
@Preview(
    name = "音色选择页 · 输入引导 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoicePickerPageInputPreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "",
        options = emptyList(),
        allowCustomInput = true,
        title = "会话音色",
        defaultVoiceId = "longanqian_v3.1",
        namingTemplate = "qwen-audio-3.0-tts-{plus|flash}-{后缀}",
        docsUrl = "https://example.com",
    )
}
