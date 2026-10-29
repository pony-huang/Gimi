package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 聊天输入栏中的当前模型显示与切换组件。
 *
 * - [ModelStatusDisplay]：常驻显示当前激活模型名称，带 sparkles 图标 alpha 脉动微动效；
 *                       `modelName == null` 时显示空态文字（无动效、无 caret）。
 * - 点击 pill 后打开 [ChatModelPickerSheet] 列出所有已启用服务下的模型；
 *   点击行即提交（不重置当前会话）。
 * - [ModelTitleAndPicker]：解析宿主传入的模型列表，并持有抽屉的显示状态。
 */

/**
 * 一行模型选项，携带模型及所属服务、分组，用于展示副标题和构造 [ModelSelection]。
 */
data class EnabledModelRow(
    val service: LLMModelSetting,
    val group: ModelGroup,
    val model: Model,
)

/**
 * 输入栏中显示当前激活模型名称、服务品牌图标和下拉箭头。
 *
 * - 仅对左侧品牌图标做 1.4s 周期的 alpha 反向循环（0.55 ↔ 1.0），文字本身不动，
 *   避免触发布局抖动与文字偏移。
 * - `modelName == null`（没有任何已启用服务）时跳过动效与 caret，文字改用 error 色。
 *
 * @param modelName 当前应显示的模型名；`null` 时进入"未选择模型"空态。
 * @param onClick 整行点击回调；加载态传入 `null` 时禁用点击。
 */
@Composable
fun ModelStatusDisplay(
    modelName: String?,
    serviceId: String?,
    emptyText: String,
    isLoading: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isActive = modelName != null
    val infinite = rememberInfiniteTransition(label = "active-model")
    val iconAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "iconAlpha",
    )

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(24.dp),
        // 模型入口比输入栏背景抬一级，与抽屉中的选项保持中性容器层级。
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            if (isActive) {
                LLMModelServiceIcon(
                    serviceId = serviceId.orEmpty(),
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(iconAlpha),
                    contentPadding = 4.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = modelName ?: emptyText,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isActive -> MaterialTheme.colorScheme.onSurface
                    isLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isActive) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.chat_model_picker_change),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 输入栏中的模型入口及底部选择抽屉。
 *
 * 点击标题 → 打开模型选择底部抽屉；选中行：
 * 1. 写入当前模型选择；
 * 2. 关闭抽屉；
 * 3. [ChatViewModel.selectModel] 持久化当前会话的选择并重建后续消息使用的 agent。
 *
 * @param isAgentRunning 当前 Agent turn 是否仍在进行。
 * @param onPickerVisibilityChange 模型选择抽屉显示状态变化回调。
 * @param modifier 修饰符，透传给模型入口的根 `Box`。
 */
@Composable
fun ModelTitleAndPicker(
    services: List<LLMModelSetting>,
    currentSelection: ModelSelection?,
    loadState: CatalogLoadState,
    isAgentRunning: Boolean,
    onConfigureModels: () -> Unit,
    onSelectModel: (ModelSelection) -> Unit,
    onModelSwitchBlocked: () -> Unit,
    onPickerVisibilityChange: (Boolean) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    // 解析后用于 TopAppBar 中央显示的模型。显式选择命中走 resolveSelection，
    // 否则回退到"第一个启用服务 → 第一个非空组 → 第一个模型"。两个都没有则 null（空态）。
    val displayedModel: EnabledModelRow? = remember(services, currentSelection) {
        services.resolveSelection(currentSelection) ?: services.asSequence()
            .filter { it.isConfiguredForChat() }
            .flatMap { service ->
                service.groups.asSequence().flatMap { group ->
                    group.models.asSequence()
                        .filter { it.isChatModel() }
                        .map { model -> EnabledModelRow(service, group, model) }
                }
            }
            .firstOrNull()
    }
    val displayedModelName = displayedModel?.model?.name
    // 若会话没有显式选择或选择已失效，选择器应与标题/runner 的回退结果一致，
    // 使用户看到当前正在使用的模型已被勾选。
    val effectiveSelection: ModelSelection? = remember(services, currentSelection) {
        if (services.resolveSelection(currentSelection) != null) {
            currentSelection
        } else {
            services.firstConfiguredChatSelection()
        }
    }
    // 抽屉候选：所有已启用且已配置服务下的普通聊天模型。
    val enabledModels: List<EnabledModelRow> = remember(services) {
        services.filter { it.isConfiguredForChat() }.flatMap { svc ->
            svc.groups.flatMap { grp ->
                grp.models.sortedBy { it.id }.sortedBy { it.name }
                    .filter { it.isChatModel() }.map { m -> EnabledModelRow(svc, grp, m) }
            }
        }
    }
    var showModelPicker by remember { mutableStateOf(false) }

    // 模型选择器由宿主决定可用宽度；输入框内保持左对齐并在窄屏省略长名称。
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        ModelStatusDisplay(
            modelName = displayedModelName,
            serviceId = displayedModel?.service?.id,
            emptyText = when (loadState) {
                CatalogLoadState.Loading -> stringResource(R.string.chat_model_loading)
                CatalogLoadState.Ready -> stringResource(R.string.chat_model_empty_configure)
                is CatalogLoadState.Failed -> stringResource(R.string.chat_model_load_failed)
            },
            isLoading = loadState == CatalogLoadState.Loading,
            onClick = when {
                loadState == CatalogLoadState.Loading -> null
                displayedModelName == null -> onConfigureModels
                else -> {
                    {
                        if (isAgentRunning) {
                            onModelSwitchBlocked()
                        } else {
                            showModelPicker = true
                            onPickerVisibilityChange(true)
                        }
                    }
                }
            },
        )
    }

    // 模型软切换：只改变后续消息使用的模型，不重置当前会话。
    if (showModelPicker) {
        ChatModelPickerSheet(
            options = enabledModels,
            selection = effectiveSelection,
            onPick = { row ->
                onSelectModel(
                    ModelSelection(
                        serviceId = row.service.id,
                        groupId = row.group.id,
                        modelId = row.model.id,
                    )
                )
                showModelPicker = false
                onPickerVisibilityChange(false)
            },
            onDismiss = {
                showModelPicker = false
                onPickerVisibilityChange(false)
            },
        )
    }
}

private fun List<LLMModelSetting>.resolveSelection(selection: ModelSelection?): EnabledModelRow? {
    if (selection == null) return null
    val service = firstOrNull {
        it.id == selection.serviceId && it.isConfiguredForChat()
    } ?: return null
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return null
    val model = group.models.firstOrNull {
        it.id == selection.modelId && it.isChatModel()
    } ?: return null
    return EnabledModelRow(service, group, model)
}

private fun List<LLMModelSetting>.firstConfiguredChatSelection(): ModelSelection? {
    return asSequence()
        .filter { it.isConfiguredForChat() }
        .mapNotNull { service ->
            service.groups.asSequence().mapNotNull { group ->
                val model = group.models.firstOrNull { it.isChatModel() } ?: return@mapNotNull null
                ModelSelection(
                    serviceId = service.id,
                    groupId = group.id,
                    modelId = model.id,
                )
            }.firstOrNull()
        }
        .firstOrNull()
}

private fun LLMModelSetting.isConfiguredForChat(): Boolean =
    isEnabled && apiKey.isNotBlank() && groups.any { group -> group.models.any(Model::isChatModel) }

// 远端拉取的模型不会带 isTts 标记（OpenAiCompatibleModelServiceGateway 只取 id），
// 这里额外按 id/name 中的 "tts" 关键字兜底过滤，避免语音合成模型混入聊天候选。
internal fun Model.isChatModel(): Boolean =
    !isStt && !isTts && !id.contains("tts", ignoreCase = true) &&
            !name.contains("tts", ignoreCase = true)

internal fun previewModelServices(): List<LLMModelSetting> = listOf(
    LLMModelSetting(
        id = "openai",
        name = "OpenAI",
        isEnabled = true,
        apiKey = "sk-preview",
        apiBaseUrl = "https://api.openai.com/v1",
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "https://api.anthropic.com",
        groups = listOf(
            ModelGroup(
                id = "gpt",
                name = "GPT",
                models = listOf(
                    Model(id = "gpt-4o", name = "GPT-4o"),
                    Model(id = "gpt-4o-mini", name = "GPT-4o mini"),
                ),
            ),
        ),
    ),
    LLMModelSetting(
        id = "deepseek",
        name = "DeepSeek",
        isEnabled = true,
        apiKey = "preview-only",
        apiBaseUrl = "https://api.deepseek.com",
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "deepseek",
                name = "DeepSeek",
                models = listOf(Model(id = "deepseekv4-flash", name = "deepseekv4-flash")),
            ),
        ),
    ),
)

@Preview(showBackground = true)
@Composable
private fun ModelStatusDisplayActivePreview() {
    AsssistantaiTheme {
        ModelStatusDisplay(
            modelName = "GPT-4o",
            serviceId = "openai",
            emptyText = "未选择模型",
            isLoading = false,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelStatusDisplayEmptyPreview() {
    AsssistantaiTheme {
        ModelStatusDisplay(
            modelName = null,
            serviceId = null,
            emptyText = "未选择模型",
            isLoading = false,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelTitleAndPickerPreview() {
    AsssistantaiTheme {
        ModelTitleAndPicker(
            services = previewModelServices(),
            currentSelection = ModelSelection(
                serviceId = "openai",
                groupId = "gpt",
                modelId = "gpt-4o",
            ),
            loadState = CatalogLoadState.Ready,
            isAgentRunning = false,
            onConfigureModels = {},
            onSelectModel = {},
            onModelSwitchBlocked = {},
        )
    }
}
