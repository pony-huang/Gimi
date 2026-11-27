package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.GimiSearchField
import github.ponyhuang.gimi.ui.components.FunctionIcon
import github.ponyhuang.gimi.ui.preference.PreferenceFormContent
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchRow
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 模型管理页：搜索、类别筛选、候选勾选与手动添加。 */
@Composable
internal fun ModelsPage(state: ModelServiceDetailUiState, onAction: (ModelServiceDetailAction) -> Unit) {
    val capabilities = state.candidates.map { it.capability }.distinct()
    val selectedCategory = state.selectedCategory?.takeIf { it in capabilities }
    val visibleCandidates = state.visibleCandidates
    Column(Modifier.fillMaxWidth()) {
        GimiSearchField(
            state.modelQuery,
            { onAction(ModelServiceDetailAction.ModelQueryChanged(it)) },
            placeholder = stringResource(R.string.modelsettings_search_models),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (capabilities.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onAction(ModelServiceDetailAction.SelectCategory(null)) },
                    label = { Text(stringResource(R.string.modelsettings_category_all)) },
                )
                capabilities.forEach { capability ->
                    FilterChip(
                        selected = selectedCategory == capability,
                        onClick = { onAction(ModelServiceDetailAction.SelectCategory(capability)) },
                        label = { Text(stringResource(capabilityLabelRes(capability))) },
                    )
                }
            }
        }
        if (state.candidates.isNotEmpty()) {
            // 摘要行取代原"全选"独占行：左侧汇总已选与候选规模，右侧承载全选入口。
            SelectionSummaryRow(
                selectedCount = visibleCandidates.count { it.key in state.pendingModelKeys },
                visibleCount = visibleCandidates.size,
                selectAllEnabled = state.canEdit && visibleCandidates.any { it.key !in state.pendingModelKeys },
                onSelectAll = { onAction(ModelServiceDetailAction.SelectAllVisibleModels) },
            )
        }
        if (visibleCandidates.isEmpty() && state.modelQuery.isNotBlank()) {
            EmptyQueryHint(state.modelQuery.trim())
        }
        val grouped = visibleCandidates.groupBy { it.capability }
        grouped.forEach { (capability, rows) ->
            ModelSectionHeader(stringResource(capabilityLabelRes(capability)), rows.size)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEach { row ->
                    ModelChoiceCard(
                        row = row,
                        selected = row.key in state.pendingModelKeys,
                        onToggle = { onAction(ModelServiceDetailAction.ToggleModel(row.key)) },
                    )
                }
            }
        }
        // 手动添加是独立操作入口：虚线卡片在视觉上与候选卡片区分操作与选项。
        Spacer(Modifier.height(10.dp))
        ManualAddCard(onClick = { onAction(ModelServiceDetailAction.ShowAddDialog) })
    }
}

/** 候选列表顶部的选择摘要行：左汇总、右全选；无可见候选时全选自然禁用。 */
@Composable
private fun SelectionSummaryRow(
    selectedCount: Int,
    visibleCount: Int,
    selectAllEnabled: Boolean,
    onSelectAll: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.modelsettings_models_summary, selectedCount, visibleCount),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSelectAll, enabled = selectAllEnabled) {
            Text(stringResource(R.string.modelsettings_select_all))
        }
    }
}

/** 候选分组标题：能力名 + 当前可见数量，缩进与选择卡片的内边距对齐。 */
@Composable
private fun ModelSectionHeader(label: String, count: Int) {
    Row(
        Modifier.padding(start = 28.dp, end = 32.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** 候选模型的选择卡片：能力图标圆底 + 名称/ID/多模态徽标 + 尾部勾选标记。 */
@Composable
private fun ModelChoiceCard(
    row: ModelCandidateRow,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val multimodal = row.model.multimodal
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else preferenceGroupCardColor(),
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 能力图标圆底：行首视觉锚点，与设置页图标徽章同一容器语言。
        Box(
            Modifier.size(38.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                capabilityIcon(row.capability),
                contentDescription = null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.model.name.ifBlank { row.model.id },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.model.id != row.model.name) {
                Text(
                    row.model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (multimodal.isMultimodal) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (multimodal.supportsImages) {
                        ModelCapabilityBadge(stringResource(R.string.modelsettings_model_badge_vision))
                    }
                    if (multimodal.supportsDocuments) {
                        ModelCapabilityBadge(stringResource(R.string.modelsettings_model_badge_documents))
                    }
                    if (multimodal.supportsAudio) {
                        ModelCapabilityBadge(stringResource(R.string.modelsettings_model_badge_audio))
                    }
                }
            }
        }
        SelectionMark(selected)
    }
}

/** 多模态输入能力的小徽标：辅助标签用 tertiary 容器语言。 */
@Composable
private fun ModelCapabilityBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 尾部多选标记：选中为墨色圆底白勾，未选中为描边空圆。 */
@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** 搜索无结果的空态：给出换词与换类别的引导。 */
@Composable
private fun EmptyQueryHint(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.modelsettings_empty_query_title, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            stringResource(R.string.modelsettings_empty_query_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 手动添加入口：虚线描边卡片，与候选卡片区分操作与选项。 */
@Composable
private fun ManualAddCard(onClick: () -> Unit) {
    // drawBehind 在绘制期执行，不能读 composition-local；颜色与形状在组合期捕获。
    val shape = MaterialTheme.shapes.large
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .drawBehind {
                // Compose 的 border 修饰符不支持虚线，这里手绘圆角虚线描边。
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> {
                        drawPath(Path().apply { addRoundRect(outline.roundRect) }, outlineColor, style = stroke)
                    }
                    is Outline.Rectangle -> drawRect(outlineColor, style = stroke)
                    else -> Unit
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            stringResource(R.string.modelsettings_add_model_manually),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** 接口设置页：每条连接的协议、端点、凭据来源与连接测试。 */
@Composable
internal fun ConnectionsPage(state: ModelServiceDetailUiState, onAction: (ModelServiceDetailAction) -> Unit) {
    state.connections.forEachIndexed { index, editor ->
        PreferenceSectionTitle(stringResource(capabilityLabelRes(editor.connection.capability)))
        PreferenceGroupCard {
            PreferenceFormContent {
                if (editor.isConversation) {
                    if (editor.protocols.size > 1) {
                        // 在组合期把协议标签解析好；onSelect 回调不是组合上下文。
                        val protocolOptions = editor.protocols.map { it to protocolLabel(it) }
                        ExposedProtocolDropdown(
                            label = stringResource(R.string.modelsettings_field_protocol),
                            value = protocolLabel(editor.connection.protocol),
                            options = protocolOptions.map { it.second },
                            onSelect = { label ->
                                protocolOptions.firstOrNull { it.second == label }?.let { (protocol, _) ->
                                    onAction(ModelServiceDetailAction.ConnectionProtocolChanged(editor.connection.id, protocol))
                                }
                            },
                        )
                    } else {
                        // 模板只开放一种协议（如 DeepSeek / MiniMax 固定 Messages）：只展示
                        // 当前值，不提供任何切换入口，避免给出可以更换协议的暗示。
                        LockedProtocolField(
                            label = stringResource(R.string.modelsettings_field_protocol),
                            value = protocolLabel(editor.connection.protocol),
                        )
                    }
                }
                if (editor.endpointPolicy == EndpointPolicy.UserConfigured) {
                    PreferenceTextField(
                        value = editor.connection.endpoint,
                        onValueChange = {
                            onAction(ModelServiceDetailAction.ConnectionEndpointChanged(editor.connection.id, it))
                        },
                        label = { Text(stringResource(R.string.modelsettings_field_endpoint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                } else {
                    Text(
                        stringResource(R.string.modelsettings_vendor_managed_endpoint),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val sharedLabel = stringResource(R.string.modelsettings_credential_shared)
                val independentLabel = stringResource(R.string.modelsettings_credential_independent)
                ExposedProtocolDropdown(
                    label = stringResource(R.string.modelsettings_field_credential_source),
                    value = if (editor.connection.credential == CredentialSource.Shared) sharedLabel else independentLabel,
                    options = listOf(sharedLabel, independentLabel),
                    onSelect = { label ->
                        onAction(
                            ModelServiceDetailAction.ConnectionCredentialSharedChanged(
                                editor.connection.id,
                                shared = label == sharedLabel,
                            ),
                        )
                    },
                )
                if (editor.connection.credential is CredentialSource.Independent) {
                    PreferenceTextField(
                        value = (editor.connection.credential as CredentialSource.Independent).apiKey,
                        onValueChange = {
                            onAction(ModelServiceDetailAction.ConnectionIndependentKeyChanged(editor.connection.id, it))
                        },
                        label = { Text(stringResource(R.string.modelsettings_field_independent_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (state.isConnectionKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { onAction(ModelServiceDetailAction.ToggleConnectionKeyVisibility) }) {
                                Text(
                                    stringResource(
                                        if (state.isConnectionKeyVisible) {
                                            R.string.modelsettings_hide
                                        } else {
                                            R.string.modelsettings_show
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
                if (editor.supportsWorkspace) {
                    PreferenceTextField(
                        value = editor.connection.workspaceId.orEmpty(),
                        onValueChange = {
                            onAction(ModelServiceDetailAction.ConnectionWorkspaceChanged(editor.connection.id, it))
                        },
                        label = { Text(stringResource(R.string.modelsettings_field_workspace)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                ConnectionTestRow(
                    test = editor.test,
                    isApplicable = editor.test.isApplicable(state.connectionTestVersion),
                    onTest = { onAction(ModelServiceDetailAction.TestConnection(editor.connection.id)) },
                )
            }
        }
        if (index < state.connections.lastIndex) {
            Spacer(Modifier.padding(top = 8.dp))
        }
    }
    PreferenceSectionNote(stringResource(R.string.modelsettings_connections_save_hint))
}

@Composable
private fun ConnectionTestRow(
    test: ConnectionTestState,
    isApplicable: Boolean,
    onTest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            test.isRunning -> CircularProgressIndicator(Modifier.size(18.dp))
            isApplicable -> Icon(
                if (test.result == ServiceConnectionResult.Succeeded) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.ErrorOutline
                },
                contentDescription = null,
                Modifier.size(18.dp),
                tint = if (test.result == ServiceConnectionResult.Succeeded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            else -> Unit
        }
        val label = when {
            test.isRunning -> stringResource(R.string.modelsettings_testing)
            !isApplicable -> stringResource(R.string.modelsettings_test_untested)
            else -> when (test.result) {
                ServiceConnectionResult.Succeeded -> stringResource(R.string.modelsettings_test_succeeded)
                ServiceConnectionResult.AuthFailed -> stringResource(R.string.modelsettings_test_auth_failed)
                ServiceConnectionResult.NetworkFailed -> stringResource(R.string.modelsettings_test_network_failed)
                ServiceConnectionResult.Unsupported -> stringResource(R.string.modelsettings_test_unsupported)
                null -> stringResource(R.string.modelsettings_test_untested)
            }
        }
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (isApplicable && test.result != ServiceConnectionResult.Succeeded &&
                test.result != null
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (test.isRunning) {
            TextButton(onClick = onTest, enabled = false) { Text(stringResource(R.string.modelsettings_testing)) }
        } else {
            TextButton(onClick = onTest) {
                Text(
                    stringResource(
                        if (isApplicable) R.string.modelsettings_test_retry else R.string.modelsettings_test_run,
                    ),
                )
            }
        }
    }
}

/** 厂商工具页：工具偏好与能力矩阵；协议限制逐行标注。 */
@Composable
internal fun OfficialToolsPage(state: ModelServiceDetailUiState, onAction: (ModelServiceDetailAction) -> Unit) {
    val draft = state.draft ?: return
    PreferenceSectionTitle(state.template?.name.orEmpty())
    PreferenceGroupCard {
        PreferenceSwitchRow(
            leadingIcon = { FunctionIcon(it) },
            title = stringResource(R.string.modelsettings_provide_official_tools),
            description = stringResource(
                if (draft.isOfficialToolsEnabled) {
                    R.string.modelsettings_official_tools_enabled
                } else {
                    R.string.modelsettings_official_tools_disabled
                },
            ),
            checked = draft.isOfficialToolsEnabled,
            onCheckedChange = { onAction(ModelServiceDetailAction.OfficialToolsEnabledChanged(it)) },
        )
    }
    // 费用说明紧贴能力清单；底部不再重复展示说明与保存提示。
    PreferenceSectionTitle(stringResource(R.string.modelsettings_tool_capabilities))
    PreferenceSectionNote(stringResource(R.string.modelsettings_official_tools_cost_note))
    PreferenceGroupCard {
        state.supportedTools.forEachIndexed { index, row ->
            PreferenceListItem(
                leadingIcon = { FunctionIcon(it) },
                title = toolRowTitle(row),
                subtitle = toolRowSubtitle(row),
                subtitleMaxLines = 2,
                showDivider = index < state.supportedTools.lastIndex,
            )
        }
    }
}

/** 工具行标题：无厂商前缀的短名称；未收录的工具回落到目录 displayName。 */
@Composable
private fun toolRowTitle(row: OfficialToolRow): String =
    officialToolCopyById[row.spec.toolId]?.let { stringResource(it.nameRes) } ?: row.spec.displayName

/** 工具行副标题：工具的具体作用；当前协议不支持时追加限制说明。 */
@Composable
private fun toolRowSubtitle(row: OfficialToolRow): String {
    val description = officialToolCopyById[row.spec.toolId]
        ?.let { stringResource(it.descriptionRes) }
        ?: row.spec.binding.let { binding ->
            if (binding is OfficialToolBinding.LocalFunctions) {
                stringResource(R.string.modelsettings_tool_local_functions)
            } else {
                stringResource(R.string.modelsettings_tool_vendor_side)
            }
        }
    return if (row.isSupportedByProtocol) {
        description
    } else {
        description + " · " + stringResource(R.string.modelsettings_tool_protocol_limited)
    }
}

/** 保存前的空用途建议确认页。 */
@Composable
internal fun SuggestionsPage(state: ModelServiceDetailUiState, onAction: (ModelServiceDetailAction) -> Unit) {
    val suggestions = state.suggestions.orEmpty()
    PreferenceSectionTitle(stringResource(R.string.modelsettings_suggestions_title))
    PreferenceSectionNote(stringResource(R.string.modelsettings_suggestions_note))
    PreferenceGroupCard {
        suggestions.forEachIndexed { index, suggestion ->
            PreferenceListItem(
                icon = capabilityIcon(suggestionRoleCapability(suggestion.role)),
                title = stringResource(suggestion.roleLabelRes),
                subtitle = suggestion.modelLabel,
                showDivider = index < suggestions.lastIndex,
                trailingContent = {
                    Checkbox(
                        checked = suggestion.role in state.acceptedSuggestionRoles,
                        onCheckedChange = { accepted ->
                            onAction(
                                ModelServiceDetailAction.ToggleSuggestion(suggestion.role, accepted),
                            )
                        },
                    )
                },
            )
        }
    }
    PreferenceSectionTitle(stringResource(R.string.modelsettings_suggestions_existing_title))
    PreferenceSectionNote(stringResource(R.string.modelsettings_suggestions_existing_note))
}

@Composable
private fun suggestionRoleCapability(role: DefaultModelRole): ServiceCapability = when (role) {
    DefaultModelRole.Assistant, DefaultModelRole.Fast -> ServiceCapability.Conversation
    DefaultModelRole.SpeechRecognition -> ServiceCapability.Recognition
    DefaultModelRole.SpeechSynthesis -> ServiceCapability.Synthesis
    DefaultModelRole.Realtime -> ServiceCapability.Realtime
}

/** 协议的展示名。 */
@Composable
fun protocolLabel(protocol: ApiProtocol): String = when (protocol) {
    ApiProtocol.OpenaiChatcompletions -> stringResource(R.string.modelsettings_protocol_openai)
    ApiProtocol.Anthropic -> stringResource(R.string.modelsettings_protocol_anthropic)
    ApiProtocol.Gemini -> stringResource(R.string.modelsettings_protocol_gemini)
}

/** 模板固定协议时的只读展示：与下拉同形态但没有展开入口，明确不可更换。 */
@Composable
private fun LockedProtocolField(label: String, value: String) {
    PreferenceTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.modelsettings_protocol_locked_hint)) },
        readOnly = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExposedProtocolDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        PreferenceTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---- Previews ----

@Preview(name = "接口设置 · 浅色", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh")
@Preview(
    name = "接口设置 · 深色", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ConnectionsPagePreview() {
    GimiTheme {
        ConnectionsPage(
            state = ModelServiceDetailUiState(
                isLoading = false,
                draft = github.ponyhuang.gimi.feature.modelsettings.detail.PreviewDrafts.builtIn(),
                connections = PreviewDrafts.connectionEditors(),
                connectionTestVersion = 1,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "接口设置 · 固定 Messages · 浅色", showBackground = true, widthDp = 390, heightDp = 800, locale = "zh")
@Preview(
    name = "接口设置 · 固定 Messages · 深色", showBackground = true, widthDp = 390, heightDp = 800,
    locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LockedMessagesConnectionsPagePreview() {
    GimiTheme {
        ConnectionsPage(
            state = ModelServiceDetailUiState(
                isLoading = false,
                connections = PreviewDrafts.lockedMessagesConnectionEditors(),
                connectionTestVersion = 1,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "厂商工具 · 协议受限", showBackground = true, widthDp = 390, locale = "zh")
@Composable
private fun OfficialToolsPagePreview() {
    GimiTheme {
        OfficialToolsPage(
            state = ModelServiceDetailUiState(
                isLoading = false,
                template = PreviewDrafts.minimaxTemplate(),
                draft = PreviewDrafts.minimaxDraft(),
                supportedTools = PreviewDrafts.toolRows(),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "保存建议 · 浅色", showBackground = true, widthDp = 390, locale = "zh")
@Composable
private fun SuggestionsPagePreview() {
    GimiTheme {
        SuggestionsPage(
            state = ModelServiceDetailUiState(
                isLoading = false,
                suggestions = PreviewDrafts.suggestions(),
                acceptedSuggestionRoles = setOf(DefaultModelRole.SpeechRecognition),
            ),
            onAction = {},
        )
    }
}
