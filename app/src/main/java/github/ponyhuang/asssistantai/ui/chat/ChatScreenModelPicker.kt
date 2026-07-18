package github.ponyhuang.asssistantai.ui.chat

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.data.LLMModelGroup
import github.ponyhuang.asssistantai.data.LLMModelItem
import github.ponyhuang.asssistantai.data.LLMModelProvider
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.ui.settings.llmmodel.LLMModelServiceIcon
import kotlin.math.abs

/**
 * 聊天 TopAppBar 中央"当前模型显示与切换"组件。
 *
 * - [ModelStatusDisplay]：常驻显示当前激活模型名称，带 sparkles 图标 alpha 脉动微动效；
 *                       `modelName == null` 时显示空态文字（无动效、无 caret）。
 * - [ModelPickerDialog]：点击后弹出的模型选择对话框，列出所有已启用服务下的模型；
 *                       点击行即提交（不重置当前会话）。
 * - [ModelTitleAndPicker]：把上述两个组件 + 内部订阅模型服务状态 + 弹窗显示
 *                       状态打包成单棵子树，避免宿主屏幕（[MainScreenImpl]）顶层订阅全局
 *                       服务列表 / 当前选择后整段重组。
 */

/**
 * 一行可选项的视图模型 — 同时携带所属服务与组，供对话框展示二级文案与构造 [LLMModelSelection]。
 */
data class EnabledModelRow(
    val service: LLMModelProvider,
    val group: LLMModelGroup,
    val model: LLMModelItem,
)

/**
 * TopAppBar 中央常驻显示：当前激活模型名称 + 服务品牌图标 + 下拉 caret。
 *
 * - 仅对左侧品牌图标做 1.4s 周期的 alpha 反向循环（0.55 ↔ 1.0），文字本身不动，
 *   避免触发布局抖动与文字偏移。
 * - `modelName == null`（没有任何已启用服务）时跳过动效与 caret，文字改用 error 色。
 *
 * @param modelName 当前应显示的模型名；`null` 时进入"未选择模型"空态。
 * @param onClick 整行点击回调；空态也允许点击（会弹出空列表对话框，提示用户去启用服务）。
 */
@Composable
fun ModelStatusDisplay(
    modelName: String?,
    serviceId: String?,
    onClick: () -> Unit,
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
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (isActive) {
                LLMModelServiceIcon(
                    serviceId = serviceId.orEmpty(),
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(iconAlpha),
                    contentPadding = 3.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = modelName ?: "未选择模型",
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isActive) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "选择模型",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 模型选择对话框。
 *
 * - 搜索词仅在本次弹窗显示期间存在，实时匹配模型、服务与分组的所有可见标识。
 * - 候选区使用 [LazyColumn]：大量模型时只组合可视行，搜索框始终固定在列表外。
 * - 复用 `confirmButton` 槽位做"取消"按钮（点击行即提交，无需 apply 步骤）。
 */
@Composable
fun ModelPickerDialog(
    rows: List<EnabledModelRow>,
    currentSelection: LLMModelSelection?,
    title: String = "选择当前模型",
    onPick: (EnabledModelRow) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredRows = remember(rows, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            rows
        } else {
            rows.filter { row ->
                row.model.modelId.contains(normalizedQuery, ignoreCase = true) ||
                    row.model.modelName.contains(normalizedQuery, ignoreCase = true) ||
                    row.service.serviceId.contains(normalizedQuery, ignoreCase = true) ||
                    row.service.serviceName.contains(normalizedQuery, ignoreCase = true) ||
                    row.group.groupId.contains(normalizedQuery, ignoreCase = true) ||
                    row.group.groupName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (rows.isEmpty()) {
                Text(
                    "暂无可用模型，请先在 设置 → 模型服务 启用至少一个模型。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("搜索模型、服务或分组") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                TextButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除搜索",
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.size(8.dp))

                    if (filteredRows.isEmpty()) {
                        Text(
                            text = "未找到匹配模型",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            // 5 行紧凑模型项（每行 36dp）+ 两端 8dp 的轮状留白；其余结果滚动查看。
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(max = 196.dp)
                                .weight(1f, fill = false),
                        ) {
                            items(
                                items = filteredRows,
                                key = { row ->
                                    "${row.service.serviceId}/${row.group.groupId}/${row.model.modelId}"
                                },
                                contentType = { "model-picker-row" },
                            ) { row ->
                                val key = "${row.service.serviceId}/${row.group.groupId}/${row.model.modelId}"
                                val itemInfo = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == key }
                                // 与 scrollableArea 示例相同：以可视区域中心为轮轴，按项目中心
                                // 到轮轴的距离缩放。LazyColumn 只提供当前可见项，因此仍可按需组合。
                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                val viewportEnd = listState.layoutInfo.viewportEndOffset
                                val viewportCenter = (viewportStart + viewportEnd) / 2f
                                val itemCenter = itemInfo?.let { it.offset + it.size / 2f }
                                val distanceRatio = itemCenter?.let { center ->
                                    abs(center - viewportCenter) /
                                        (viewportEnd - viewportStart).coerceAtLeast(1)
                                } ?: 0f
                                val scale = (1f - distanceRatio * 0.28f).coerceIn(0.72f, 1f)

                                ModelPickerRow(
                                    row = row,
                                    isSelected = currentSelection?.let { sel ->
                                        sel.serviceId == row.service.serviceId
                                            && sel.groupId == row.group.groupId
                                            && sel.modelId == row.model.modelId
                                    } ?: false,
                                    onClick = { onPick(row) },
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        transformOrigin = TransformOrigin.Center
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 对话框内单行：仅展示模型显示名，保持大量模型列表的紧凑性。
 */
@Composable
private fun ModelPickerRow(
    row: EnabledModelRow,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = row.model.modelName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 把 TopAppBar 中央"当前模型"标题
 *
 * 点击标题 → 打开 [ModelPickerDialog]；选中行：
 * 1. 写入当前模型选择；
 * 2. 关闭弹窗；
 * 3. [ChatViewModel.selectModel] 持久化当前会话的选择并重建后续消息使用的 agent。
 *
 * @param viewModel    [ChatViewModel]；选中新模型后持久化会话配置。
 * @param isStreaming  当前是否正在流式输出。
 * @param modifier     修饰符，会透传给根 `Box`（用于在 TopAppBar 内做居中布局）。
 */
@Composable
fun ModelTitleAndPicker(
    viewModel: ChatViewModel,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val services by viewModel.availableModelServices.collectAsStateWithLifecycle()
    val currentSelection by viewModel.currentLLMModelSelection.collectAsStateWithLifecycle()

    // 解析后用于 TopAppBar 中央显示的模型。显式选择命中走 resolveSelection，
    // 否则回退到"第一个启用服务 → 第一个非空组 → 第一个模型"。两个都没有则 null（空态）。
    val displayedModel: EnabledModelRow? = remember(services, currentSelection) {
        services.resolveSelection(currentSelection) ?: services.asSequence()
            .filter { it.isEnabled }
            .flatMap { service ->
                service.LLMModelGroups.asSequence().flatMap { group ->
                    group.models.asSequence()
                        .filterNot { it.isStt }
                        .map { model -> EnabledModelRow(service, group, model) }
                }
            }
            .firstOrNull()
    }
    val displayedModelName = displayedModel?.model?.modelName
    // 若会话没有显式选择或选择已失效，选择器应与标题/runner 的回退结果一致，
    // 使用户看到当前正在使用的模型已被勾选。
    val effectiveSelection: LLMModelSelection? = remember(services, currentSelection) {
        if (services.resolveSelection(currentSelection) != null) {
            currentSelection
        } else {
            services.firstEnabledSelection()
        }
    }
    // 对话框候选：所有 isEnabled=true 的服务下的所有 ModelItem。
    val enabledModels: List<EnabledModelRow> = remember(services) {
        services.filter { it.isEnabled }.flatMap { svc ->
            svc.LLMModelGroups.flatMap { grp ->
                grp.models.filterNot { it.isStt }.map { m -> EnabledModelRow(svc, grp, m) }
            }
        }
    }
    var showModelPicker by remember { mutableStateOf(false) }

    // ── 标题（与原实现一致：普通 TopAppBar + Box.fillMaxWidth 居中） ─────────
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ModelStatusDisplay(
            modelName = displayedModelName,
            serviceId = displayedModel?.service?.serviceId,
            onClick = {
                if (isStreaming) {
                    Toast.makeText(context, "正在生成回复，完成后再切换模型", Toast.LENGTH_SHORT).show()
                } else if (displayedModelName != null) {
                    showModelPicker = true
                }
            },
        )
    }

    // ── 模型选择对话框（"软切换"：不重置当前会话） ───────────────────────
    if (showModelPicker) {
        ModelPickerDialog(
            rows = enabledModels,
            currentSelection = effectiveSelection,
            onPick = { row ->
                viewModel.selectModel(
                    LLMModelSelection(
                        serviceId = row.service.serviceId,
                        groupId = row.group.groupId,
                        modelId = row.model.modelId,
                    )
                )
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

private fun List<LLMModelProvider>.resolveSelection(selection: LLMModelSelection?): EnabledModelRow? {
    if (selection == null) return null
    val service = firstOrNull { it.serviceId == selection.serviceId && it.isEnabled } ?: return null
    val group = service.LLMModelGroups.firstOrNull { it.groupId == selection.groupId } ?: return null
    val model = group.models.firstOrNull {
        it.modelId == selection.modelId && !it.isStt
    } ?: return null
    return EnabledModelRow(service, group, model)
}

private fun List<LLMModelProvider>.firstEnabledSelection(): LLMModelSelection? {
    return asSequence()
        .filter { it.isEnabled }
        .mapNotNull { service ->
            service.LLMModelGroups.asSequence().mapNotNull { group ->
                val model = group.models.firstOrNull { !it.isStt } ?: return@mapNotNull null
                LLMModelSelection(
                    serviceId = service.serviceId,
                    groupId = group.groupId,
                    modelId = model.modelId,
                )
            }.firstOrNull()
        }
        .firstOrNull()
}
