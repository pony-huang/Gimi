package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 音色选择页：抽屉内的纯选择界面，只挑选系统音色。
 *
 * 这是**抽屉内的一页**，不自带 [GimiSheetBase] 外壳：宿主把本页放进同一个抽屉的
 * `AnimatedContent` 里，点返回箭头翻回来源设置页。标题行、搜索胶囊与列表由
 * [GimiSearchableSheetPage] 骨架统一给出——搜索不再常驻，点标题行尾的搜索图标
 * 原位展开成胶囊输入框，抽屉升高、列表收起、结果平铺；抽屉内**不提供新增音色**
 * 入口，自定义音色只能从设置页添加。
 *
 * 列表态按分类分组展示；搜索态平铺展示命中项，不区分分组。
 *
 * 模块归属说明（AGENTS.md 共享豁免，与 `LLMModelServiceIcon` 同类）：
 * 本组件与 `VoicePickerDialog` 共用同一套音色文案与领域类型，
 * 落在 `:core:designsystem` 避免两套实现分叉；页面版由 `:feature:chat` 消费，
 * 对话框版由 `:feature:modelsettings` 消费。
 *
 * @param title 标题行标题（如「播放音色」）。
 * @param currentVoiceId 当前生效的音色 ID；为空表示使用厂商缺省音色。
 * @param options 当前模型可用的系统音色；列表已按模型过滤，组件不再做跨模型判断。
 * @param isLoading 音色表是否仍在加载；加载中不把空列表当成「没有内置音色」。
 * @param isSearching 是否处于搜索态；由宿主提升持有，宿主的返回键先收搜索。
 * @param onSearchingChange 进入/退出搜索态；退出后本页自动清空搜索词。
 * @param onPick 选中音色；调用后宿主负责翻回上一级页面。
 * @param onBack 收起态返回箭头：回来源设置页。
 * @param onClose 收起态 ✕：关闭整个抽屉。
 * @param initialQuery 搜索框初值；生产调用保持空串，仅供预览构造「搜索中」状态。
 */
@Composable
fun VoicePickerPage(
    title: String,
    currentVoiceId: String,
    options: List<TtsVoice>,
    isLoading: Boolean,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // 退出搜索时统一清词：无论从胶囊 ✕ 还是宿主返回键退出，下次展开都不残留旧词。
    var wasSearching by remember { mutableStateOf(isSearching) }
    LaunchedEffect(isSearching) {
        if (wasSearching && !isSearching) query = ""
        wasSearching = isSearching
    }

    // 过场中的旧页面还会短暂存在，回调必须指向最新一次组合的宿主状态，不能用快照值。
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnBack by rememberUpdatedState(onBack)
    // 提交后由宿主翻回来源设置页：抽屉保持打开，选中值随数据流在设置页原地更新。
    val commit: (String) -> Unit = { voiceId ->
        currentOnPick(voiceId)
        currentOnBack()
    }

    GimiSearchableSheetPage(
        title = title,
        isSearching = isSearching,
        query = query,
        onQueryChange = { query = it },
        onOpenSearch = { onSearchingChange(true) },
        onExitSearch = { onSearchingChange(false) },
        onBack = currentOnBack,
        onClose = onClose,
        modifier = modifier,
        listContent = {
            VoiceListPanel(
                currentVoiceId = currentVoiceId,
                options = options,
                isLoading = isLoading,
                onPick = commit,
            )
        },
        searchContent = {
            VoiceSearchPanel(
                currentVoiceId = currentVoiceId,
                options = options,
                isLoading = isLoading,
                query = query,
                onPick = commit,
            )
        },
    )
}

@Composable
private fun VoiceListPanel(
    currentVoiceId: String,
    options: List<TtsVoice>,
    isLoading: Boolean,
    onPick: (String) -> Unit,
) {
    // 列表在抽屉主体内滚动：Column 里的 LazyColumn 需要显式上限，否则会拿到无限高度。
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.6f).coerceAtMost(520.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = listMaxHeight),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            options.isEmpty() -> PickerEmptyText(
                text = stringResource(R.string.voice_picker_no_voices),
                modifier = Modifier.heightIn(min = 120.dp, max = listMaxHeight),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                VoiceCategory.values().forEach { category ->
                    val voices = options.filter { it.category == category }
                    if (voices.isEmpty()) return@forEach
                    item(key = "header/${category.name}") {
                        VoiceGroupHeader(category)
                    }
                    items(voices, key = { it.id }) { voice ->
                        VoicePickerRow(
                            voice = voice,
                            currentVoiceId = currentVoiceId,
                            onPick = onPick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchPanel(
    currentVoiceId: String,
    options: List<TtsVoice>,
    isLoading: Boolean,
    query: String,
    onPick: (String) -> Unit,
) {
    val normalizedQuery = query.trim()
    val visible = options.filter { voice ->
        voice.searchText().contains(normalizedQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            // 搜索态的空 query 是「还没输入」的引导态，与无命中区分开。
            normalizedQuery.isEmpty() -> PickerEmptyText(
                text = stringResource(R.string.voice_picker_search_hint),
                modifier = Modifier.fillMaxSize(),
            )

            visible.isEmpty() -> PickerEmptyText(
                text = stringResource(R.string.voice_picker_no_match),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                // 搜索结果平铺展示：搜索态是独立模式，分组标题只在列表态保留。
                items(visible, key = { it.id }) { voice ->
                    VoicePickerRow(
                        voice = voice,
                        currentVoiceId = currentVoiceId,
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoicePickerRow(
    voice: TtsVoice,
    currentVoiceId: String,
    onPick: (String) -> Unit,
) {
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

/** 居中的空态文案；抽屉内不做任何新增出口，无命中只给文字。 */
@Composable
private fun PickerEmptyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    TtsVoice("qingshe_v3.1", "青涩青年音色", "中文（普通话）", "男", "青涩 · 社交陪伴", VoiceCategory.Flagship),
    TtsVoice("jingying_v3.1", "精英青年音色", "中文（普通话）", "男", "沉稳 · 社交陪伴", VoiceCategory.Flagship),
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

/** 预览按抽屉页面的真实底色渲染，直接展示标题行 + 列表/搜索态，不套模态外壳。 */
@Composable
private fun VoicePickerPagePreviewHost(
    currentVoiceId: String,
    options: List<TtsVoice>,
    isSearching: Boolean = false,
    initialQuery: String = "",
    title: String = "播放音色",
) {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            VoicePickerPage(
                title = title,
                currentVoiceId = currentVoiceId,
                options = options,
                isLoading = false,
                isSearching = isSearching,
                onSearchingChange = {},
                onPick = {},
                onBack = {},
                onClose = {},
                initialQuery = initialQuery,
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
    )
}

@Preview(name = "音色选择页 · 搜索命中", showBackground = true, widthDp = 390, heightDp = 700)
@Preview(
    name = "音色选择页 · 搜索命中 · 深色",
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
        isSearching = true,
        initialQuery = "青年",
    )
}

@Preview(name = "音色选择页 · 无内置音色", showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun VoicePickerPageEmptyPreview() {
    VoicePickerPagePreviewHost(
        currentVoiceId = "",
        options = emptyList(),
        title = "会话音色",
    )
}
