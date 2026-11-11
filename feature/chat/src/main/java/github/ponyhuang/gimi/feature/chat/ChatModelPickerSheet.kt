package github.ponyhuang.gimi.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.components.GimiPickerItemRow
import github.ponyhuang.gimi.ui.components.GimiSheetBase
import github.ponyhuang.gimi.ui.components.GimiSheetIconAction
import github.ponyhuang.gimi.ui.components.GimiSheetTitleRow
import github.ponyhuang.gimi.ui.components.SheetHeight
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme
import kotlin.math.roundToInt

/**
 * 聊天模型选择抽屉，两级导航：一级为品牌厂商列表，二级为该厂商的模型列表；
 * 二级页顶部附思考强度滑杆（仅当前模型所在厂商显示）。
 * 模型、选择与会话配置均由宿主持有，点击行后立即提交，抽屉保持打开以继续调整配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatModelPickerSheet(
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    onPick: (EnabledModelRow) -> Unit,
    onDismiss: () -> Unit,
    toolConfiguration: ConversationToolConfiguration? = null,
    reasoningSupported: Boolean = false,
    reasoningEnabled: Boolean = true,
    onReasoningEffortChange: (ReasoningEffort) -> Unit = { _ -> },
    onThinkingEnabledChange: (Boolean) -> Unit = { },
) {
    // 页级状态放在抽屉层：一级页标题是"选择模型"，二级页是厂商名，标题行要跟着页级切；
    // 标题与主体共用同一份状态与同一份切页动效，切页时两者才会同步位移。
    var selectedServiceId by rememberSaveable { mutableStateOf<String?>(null) }

    PreferenceStyleScope(PreferenceStyle.Compact) {
        GimiSheetBase(
            onDismissRequest = onDismiss,
            initialHeight = SheetHeight.Fraction(0.6f),
            maxHeight = SheetHeight.Fraction(0.9f),
            modifier = Modifier.testTag("chat_model_picker_sheet"),
            header = {
                AnimatedContent(
                    targetState = selectedServiceId,
                    transitionSpec = { modelPickerPageTransition() },
                    label = "model-picker-title",
                    modifier = Modifier.fillMaxWidth(),
                ) { serviceId ->
                    ChatModelPickerTitle(
                        serviceId = serviceId,
                        serviceName = serviceId?.let { id ->
                            options.firstOrNull { it.service.id == id }?.service?.name
                        },
                        onBack = { selectedServiceId = null },
                        onDismiss = onDismiss,
                    )
                }
            },
        ) {
            ChatModelPickerBody(
                options = options,
                selection = selection,
                onPick = onPick,
                toolConfiguration = toolConfiguration,
                reasoningSupported = reasoningSupported,
                reasoningEnabled = reasoningEnabled,
                onReasoningEffortChange = onReasoningEffortChange,
                onThinkingEnabledChange = onThinkingEnabledChange,
                selectedServiceId = selectedServiceId,
                onSelectService = { selectedServiceId = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 抽屉标题行：一级页是"选择模型"配右上角关闭，二级页是厂商名配左侧返回。
 * 标题居中由 [GimiSheetTitleRow] 保证；返回在左、关闭在右。
 */
@Composable
private fun ChatModelPickerTitle(
    serviceId: String?,
    serviceName: String?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    GimiSheetTitleRow(
        title = if (serviceId == null) {
            stringResource(R.string.chat_model_picker_title)
        } else {
            serviceName.orEmpty()
        },
        navigationIcon = if (serviceId == null) null else Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = if (serviceId == null) {
            null
        } else {
            stringResource(R.string.chat_model_picker_back)
        },
        onNavigationClick = if (serviceId == null) null else ({ onBack() }),
        trailing = {
            if (serviceId == null) {
                GimiSheetIconAction(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.chat_model_picker_close),
                    onClick = onDismiss,
                )
            }
        },
    )
}

/** 一级页的一行厂商：所属服务 + 该服务下可用的聊天模型数。 */
private data class ProviderOption(
    val service: LLMModelSetting,
    val modelCount: Int,
)

@Composable
private fun ChatModelPickerBody(
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    onPick: (EnabledModelRow) -> Unit,
    toolConfiguration: ConversationToolConfiguration?,
    reasoningSupported: Boolean,
    reasoningEnabled: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    selectedServiceId: String?,
    onSelectService: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers = options
        .groupBy { it.service.id }
        .map { (_, rows) -> ProviderOption(rows.first().service, rows.size) }
    BackHandler(enabled = selectedServiceId != null) { onSelectService(null) }

    // 高度上限来自抽屉自身拿到的窗口约束；若改用 LocalConfiguration.screenHeightDp，
    // 键盘显隐或窗口配置变化会让上限被重算，抽屉高度随之在滚动过程中反复变化而抽动。
    // 主体用 heightIn(max) 给出上限，页面内容用可滚动的 Column 贴着内容长，
    // 超过上限时列表自己滚动。短距离位移保留层级方向，尺寸使用同一缓动，
    // 避免列表长度变化时抽屉突然跳高。
    AnimatedContent(
        targetState = selectedServiceId,
        transitionSpec = { modelPickerPageTransition() },
        label = "model-picker-page",
        modifier = modifier.fillMaxWidth(),
    ) { serviceId ->
        when (serviceId) {
            null -> ProviderListContent(
                providers = providers,
                selection = selection,
                onOpenProvider = onSelectService,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> ProviderModelListContent(
                serviceId = serviceId,
                options = options,
                selection = selection,
                showReasoning = reasoningSupported && toolConfiguration != null &&
                    serviceId == selection?.serviceId,
                reasoningSupported = reasoningSupported,
                toolConfiguration = toolConfiguration,
                reasoningEnabled = reasoningEnabled,
                onReasoningEffortChange = onReasoningEffortChange,
                onThinkingEnabledChange = onThinkingEnabledChange,
                onPick = onPick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 抽屉切页动效：标题行与主体共用同一份，保证切页时两者同向同步位移。
 * 一级页往二级页走时内容自右滑入，二级页回一级页时自左滑入。
 */
private fun AnimatedContentTransitionScope<String?>.modelPickerPageTransition(): ContentTransform {
    val direction = if (targetState == null) -1 else 1
    return (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) {
        direction * it / 16
    } + fadeIn(tween(280))) togetherWith
        (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) {
            -direction * it / 16
        } + fadeOut(tween(220))) using
        SizeTransform { _, _ -> tween(340, easing = FastOutSlowInEasing) }
}

/** 一级页：品牌厂商列表。 */
@Composable
private fun ProviderListContent(
    providers: List<ProviderOption>,
    selection: ModelSelection?,
    onOpenProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 主体用 heightIn(max) 给了有界高度，LazyColumn 在这种约束下会直接把主体撑满，
    // 抽屉就贴不住内容了。厂商数量很少，改用可滚动的 Column：短列表贴内容长，
    // 长列表到上限后在内部滚动。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        if (providers.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_model_empty_enable_first),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = GimiListDefaults.HorizontalPadding,
                    vertical = 16.dp,
                ),
            )
        }
        providers.forEach { provider ->
            val containsCurrent = selection?.serviceId == provider.service.id
            GimiPickerItemRow(
                title = provider.service.name,
                subtitle = stringResource(
                    R.string.chat_model_picker_provider_model_count,
                    provider.modelCount,
                ),
                selected = false,
                onClick = { onOpenProvider(provider.service.id) },
                modifier = Modifier.padding(
                    horizontal = GimiListDefaults.HorizontalPadding,
                    vertical = 4.dp,
                ),
                leading = {
                    LLMModelServiceIcon(
                        serviceId = provider.service.id,
                        modifier = Modifier.size(28.dp),
                        contentPadding = 3.dp,
                    )
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (containsCurrent) {
                            Text(
                                text = stringResource(R.string.chat_model_picker_current),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                role = Role.Button,
            )
        }
    }
}

/** 二级页：该厂商的模型列表（+ 思考强度）。 */
@Composable
private fun ProviderModelListContent(
    serviceId: String,
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    showReasoning: Boolean,
    reasoningSupported: Boolean,
    toolConfiguration: ConversationToolConfiguration?,
    reasoningEnabled: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onPick: (EnabledModelRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val models = options.filter { it.service.id == serviceId }
    // 同一级页：可滚动的 Column 贴内容长，超过主体上限时自己滚动。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        if (showReasoning) {
            ReasoningEffortSliderSection(
                configuration = toolConfiguration,
                supported = reasoningSupported,
                enabled = reasoningEnabled,
                onEffortChange = onReasoningEffortChange,
                onThinkingEnabledChange = onThinkingEnabledChange,
            )
        }
        models.forEach { row ->
            val selected = selection?.let {
                it.serviceId == row.service.id && it.groupId == row.group.id && it.modelId == row.model.id
            } ?: false
            GimiPickerItemRow(
                title = row.model.name,
                subtitle = if (
                    row.service.groups.count { group -> group.models.any(Model::isChatModel) } > 1
                ) {
                    row.group.name
                } else {
                    ""
                },
                selected = selected,
                onClick = { onPick(row) },
                modifier = Modifier.padding(
                    horizontal = GimiListDefaults.HorizontalPadding,
                    vertical = 4.dp,
                ),
            )
        }
    }
}

/**
 * 思考强度区块：五档滑杆（关闭 + 极简/低/中/高）。
 * 协议不支持时显示现有 OpenAI Chat Completions 限制说明文案。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReasoningEffortSliderSection(
    configuration: ConversationToolConfiguration?,
    supported: Boolean,
    enabled: Boolean,
    onEffortChange: (ReasoningEffort) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
) {
    val currentLabel = if (configuration?.thinkingEnabled == true) {
        reasoningEffortLabel(configuration.reasoningEffort)
    } else {
        stringResource(R.string.chat_reasoning_effort_off)
    }
    Column(
        Modifier.padding(
            horizontal = GimiListDefaults.HorizontalPadding,
            vertical = 8.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_reasoning_effort_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Crossfade(
                targetState = currentLabel,
                animationSpec = tween(240),
                label = "reasoning-effort-label",
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (configuration?.thinkingEnabled == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (!supported) {
            Text(
                text = stringResource(R.string.chat_reasoning_effort_openai_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            val sliderValue = if (configuration?.thinkingEnabled == true) {
                configuration.reasoningEffort.ordinal + 1f
            } else {
                0f
            }
            val interactionSource = remember { MutableInteractionSource() }
            val isDragging by interactionSource.collectIsDraggedAsState()
            var gestureValue by remember { mutableStateOf<Float?>(null) }
            // 连续位置只属于手势，业务配置仍为五档；松手后从当前位置平滑吸附到档位。
            val animatedSliderValue by animateFloatAsState(
                targetValue = gestureValue ?: sliderValue,
                animationSpec = if (isDragging) snap() else spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "reasoning-effort-value",
            )
            // 关闭为 0 档，极简/低/中/高依次为 1..4；滑到关闭即整体关闭思考模式。
            Slider(
                value = animatedSliderValue,
                onValueChange = { value ->
                    if (!enabled) return@Slider
                    gestureValue = value
                    val index = value.roundToInt().coerceIn(0, 4)
                    if (index == sliderValue.toInt()) return@Slider
                    if (index == 0) {
                        onThinkingEnabledChange(false)
                    } else {
                        onThinkingEnabledChange(true)
                        onEffortChange(ReasoningEffort.entries[index - 1])
                    }
                },
                onValueChangeFinished = { gestureValue = null },
                valueRange = 0f..4f,
                enabled = enabled,
                interactionSource = interactionSource,
                track = { state ->
                    val activeTick = MaterialTheme.colorScheme.onPrimary
                    val inactiveTick = MaterialTheme.colorScheme.primary
                    SliderDefaults.Track(
                        sliderState = state,
                        enabled = enabled,
                        drawStopIndicator = null,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            // 仅绘制五个档位标记，保持 Slider 本身连续，避免 steps 强制吸附。
                            val inset = size.height / 2f
                            val span = (size.width - 2f * inset).coerceAtLeast(0f)
                            val fraction = (state.value / 4f).coerceIn(0f, 1f)
                            for (index in 0..4) {
                                val tickFraction = index / 4f
                                val visualFraction = if (layoutDirection == LayoutDirection.Rtl) {
                                    1f - tickFraction
                                } else {
                                    tickFraction
                                }
                                val thumbDistance = kotlin.math.abs(tickFraction - fraction) * span
                                if (thumbDistance < 8.dp.toPx()) continue
                                drawCircle(
                                    color = (if (tickFraction <= fraction) activeTick else inactiveTick)
                                        .copy(alpha = if (enabled) 1f else 0.38f),
                                    radius = 2.dp.toPx(),
                                    center = Offset(inset + span * visualFraction, size.height / 2f),
                                )
                            }
                        },
                    )
                },
                modifier = Modifier.testTag("chat_reasoning_effort_slider"),
            )
        }
    }
}

// ── 预览 ────────────────────────────────────────────────────

/** 预览按抽屉的真实底色渲染：标题行固定在上方，主体贴着内容长。 */
@Preview(name = "一级厂商 · 浅色", showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerLevel1LightPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = null,
                        serviceName = null,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = previewModelRows(),
                        selection = ModelSelection("openai", "gpt", "gpt-4o"),
                        onPick = {},
                        toolConfiguration = ConversationToolConfiguration(),
                        reasoningSupported = true,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = null,
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(name = "二级模型+思考强度 · 浅色", showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerLevel2LightPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = "openai",
                        serviceName = previewModelRows().first { it.service.id == "openai" }.service.name,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = previewModelRows(),
                        selection = ModelSelection("openai", "gpt", "gpt-4o"),
                        onPick = {},
                        toolConfiguration = ConversationToolConfiguration(),
                        reasoningSupported = true,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = "openai",
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(name = "二级 · 深色", showBackground = true, widthDp = 393, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatModelPickerLevel2DarkPreview() {
    GimiTheme(darkTheme = true) {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = "openai",
                        serviceName = previewModelRows().first { it.service.id == "openai" }.service.name,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = previewModelRows(),
                        selection = ModelSelection("openai", "gpt", "gpt-4o"),
                        onPick = {},
                        toolConfiguration = ConversationToolConfiguration(thinkingEnabled = false),
                        reasoningSupported = true,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = "openai",
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(name = "二级 · 协议不支持", showBackground = true, widthDp = 393, locale = "zh")
@Composable
private fun ChatModelPickerReasoningUnsupportedPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = "openai",
                        serviceName = previewModelRows().first { it.service.id == "openai" }.service.name,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = previewModelRows(),
                        selection = ModelSelection("openai", "gpt", "gpt-4o"),
                        onPick = {},
                        toolConfiguration = ConversationToolConfiguration(),
                        reasoningSupported = false,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = "openai",
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(name = "一级 · 空", showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerEmptyPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = null,
                        serviceName = null,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = emptyList(),
                        selection = null,
                        onPick = {},
                        toolConfiguration = null,
                        reasoningSupported = false,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = null,
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 候选项远超高度上限时，抽屉应停在固定高度并只滚动列表。 */
@Preview(name = "模型较多 · 浅色", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatModelPickerManyModelsLightPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column {
                    ChatModelPickerTitle(
                        serviceId = "openai",
                        serviceName = previewModelRows().first { it.service.id == "openai" }.service.name,
                        onBack = {},
                        onDismiss = {},
                    )
                    ChatModelPickerBody(
                        options = previewManyModelRows(),
                        selection = null,
                        onPick = {},
                        toolConfiguration = null,
                        reasoningSupported = false,
                        reasoningEnabled = true,
                        onReasoningEffortChange = {},
                        onThinkingEnabledChange = {},
                        selectedServiceId = "openai",
                        onSelectService = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun previewModelRows(): List<EnabledModelRow> = previewModelServices().flatMap { service ->
    service.groups.flatMap { group -> group.models.map { EnabledModelRow(service, group, it) } }
}

private fun previewManyModelRows(): List<EnabledModelRow> {
    val service = previewModelServices().first()
    return (1..24).map { index ->
        val group = ModelGroup(
            id = "gpt",
            name = "GPT",
            models = listOf(Model(id = "gpt-4o-variant-$index", name = "GPT-4o variant $index")),
        )
        EnabledModelRow(service, group, group.models.first())
    }
}
