package github.ponyhuang.gimi.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.ui.components.FunctionIcon
import github.ponyhuang.gimi.ui.components.GimiBottomSheet
import github.ponyhuang.gimi.ui.components.GimiBottomSheetHeader
import github.ponyhuang.gimi.ui.components.GimiBottomSheetOptionRow
import github.ponyhuang.gimi.ui.components.GimiBottomSheetSwitchRow
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 列表上限与模型选择抽屉一致，内容较少时不强制撑高。 */
private val ToolSheetListMaxHeight = 380.dp
private const val ToolSheetListHeightFraction = 0.55f

private enum class AddToChatPage {
    HOME,
    PERMISSION_MODE,
    REASONING_EFFORT,
    MCP,
    OFFICIAL_TOOL,
}

@Composable
internal fun ChatAddToChatSheet(
    state: ChatAddToChatState,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onChooseFiles: () -> Unit,
    imagesEnabled: Boolean,
    filesEnabled: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onMcpServerEnabledChange: (String, Boolean) -> Unit,
    onFullAccessChange: (Boolean) -> Unit,
    onOfficialToolOpened: (String) -> Unit,
    onOfficialToolFunctionEnabledChange: (String, String, Boolean) -> Unit,
    onOfficialToolFunctionsRetry: (String) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AddToChatPage.HOME) }
    BackHandler(enabled = page != AddToChatPage.HOME) {
        page = AddToChatPage.HOME
    }

    PreferenceStyleScope(PreferenceStyle.Compact) {
        GimiBottomSheet(onDismissRequest = onDismiss) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maximumPageHeight = maxHeight * 0.92f
                val toolListMaxHeight = (maxHeight * ToolSheetListHeightFraction)
                    .coerceAtMost(ToolSheetListMaxHeight)
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        slideIntoContainer(
                            towards = if (direction > 0) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            },
                            animationSpec = tween(220),
                        ) + fadeIn(tween(160)) togetherWith
                            slideOutOfContainer(
                                towards = if (direction > 0) {
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                } else {
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                },
                                animationSpec = tween(220),
                            ) + fadeOut(tween(120))
                    },
                    label = "add-to-chat-page",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maximumPageHeight)
                        .testTag("add-to-chat-sheet-content"),
                ) { currentPage ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier.wrapContentHeight()
                                .widthIn(max = 640.dp),
                        ) {
                            GimiBottomSheetHeader(
                                title = when (currentPage) {
                                    AddToChatPage.HOME -> stringResource(R.string.chat_add_to_chat_title)
                                    AddToChatPage.PERMISSION_MODE ->
                                        stringResource(R.string.chat_permission_mode_title)
                                    AddToChatPage.REASONING_EFFORT ->
                                        stringResource(R.string.chat_reasoning_effort_title)
                                    AddToChatPage.MCP -> stringResource(R.string.chat_session_mcp_title)
                                    AddToChatPage.OFFICIAL_TOOL ->
                                        stringResource(R.string.chat_official_tools_title)
                                },
                                navigationIcon = if (currentPage == AddToChatPage.HOME) {
                                    Icons.Default.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                navigationContentDescription = stringResource(
                                    if (currentPage == AddToChatPage.HOME) {
                                        R.string.chat_add_to_chat_close
                                    } else {
                                        R.string.chat_add_to_chat_back
                                    },
                                ),
                                onNavigationClick = {
                                    if (currentPage == AddToChatPage.HOME) onDismiss()
                                    else page = AddToChatPage.HOME
                                },
                                navigationModifier = Modifier.testTag(
                                    if (currentPage == AddToChatPage.HOME) {
                                        "add-to-chat-close"
                                    } else {
                                        "add-to-chat-back"
                                    },
                                ),
                            )
                            when (currentPage) {
                                AddToChatPage.HOME -> AddToChatHome(
                                    state = state,
                                    onTakePhoto = onTakePhoto,
                                    onChoosePhotos = onChoosePhotos,
                                    onChooseFiles = onChooseFiles,
                                    imagesEnabled = imagesEnabled,
                                    filesEnabled = filesEnabled,
                                    onOpenReasoningEffort = { page = AddToChatPage.REASONING_EFFORT },
                                    onOpenMcp = { page = AddToChatPage.MCP },
                                    onOpenPermissionMode = { page = AddToChatPage.PERMISSION_MODE },
                                    onOpenOfficialTools = {
                                        state.officialTools.forEach { tool ->
                                            onOfficialToolOpened(tool.id)
                                        }
                                        page = AddToChatPage.OFFICIAL_TOOL
                                    },
                                )
                                AddToChatPage.PERMISSION_MODE -> PermissionModePage(
                                    fullAccess = state.fullAccess,
                                    onFullAccessChange = onFullAccessChange,
                                )
                                AddToChatPage.REASONING_EFFORT -> ReasoningEffortPage(
                                    state = state,
                                    onEffortChange = onReasoningEffortChange,
                                )
                                AddToChatPage.MCP -> McpServersPage(
                                    state = state,
                                    onEnabledChange = onMcpServerEnabledChange,
                                    listMaxHeight = toolListMaxHeight,
                                )
                                AddToChatPage.OFFICIAL_TOOL -> OfficialToolsDetailPage(
                                    state = state,
                                    onFunctionEnabledChange = onOfficialToolFunctionEnabledChange,
                                    onRetry = onOfficialToolFunctionsRetry,
                                    listMaxHeight = toolListMaxHeight,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToChatHome(
    state: ChatAddToChatState,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onChooseFiles: () -> Unit,
    imagesEnabled: Boolean,
    filesEnabled: Boolean,
    onOpenReasoningEffort: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenOfficialTools: () -> Unit,
    onOpenPermissionMode: () -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .wrapContentHeight()
            .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState))
            .testTag("add-to-chat-home"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                AttachmentShortcut(
                    icon = Icons.Default.CameraAlt,
                    label = stringResource(R.string.stream_ai_compose_composer_take_photo),
                    onClick = onTakePhoto,
                    enabled = imagesEnabled,
                    modifier = Modifier.weight(1f),
                )
                AttachmentShortcut(
                    icon = Icons.Default.PhotoLibrary,
                    label = stringResource(R.string.chat_add_to_chat_photos),
                    onClick = onChoosePhotos,
                    enabled = imagesEnabled,
                    modifier = Modifier.weight(1f),
                )
                AttachmentShortcut(
                    icon = Icons.Default.Description,
                    label = stringResource(R.string.chat_add_to_chat_files),
                    onClick = onChooseFiles,
                    enabled = filesEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.officialTools.isNotEmpty()) {
            item {
                GroupedCard {
                    OfficialToolsNavigationRow(
                        enabledCount = state.enabledOfficialFunctionTotal(),
                        onClick = onOpenOfficialTools,
                    )
                }
            }
        }
        item {
            GroupedCard(modifier = Modifier.testTag("session-configuration-group")) {
                NavigationRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.chat_permission_mode_title),
                    subtitle = permissionModeLabel(state.fullAccess),
                    onClick = onOpenPermissionMode,
                    testTag = "permission-mode-nav",
                )
                SheetDivider()
                NavigationRow(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.chat_reasoning_effort_title),
                    subtitle = reasoningEffortLabel(
                        state.configuration?.reasoningEffort ?: ReasoningEffort.MEDIUM,
                    ),
                    onClick = onOpenReasoningEffort,
                    testTag = "reasoning-effort-nav",
                )
                SheetDivider()
                NavigationRow(
                    icon = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
                    title = stringResource(R.string.chat_session_mcp_title),
                    subtitle = when {
                        state.mcpServers.isEmpty() ->
                            stringResource(R.string.chat_session_mcp_not_configured)
                        else -> enabledCountText(state.enabledMcpServerCount, isMcp = true)
                    },
                    onClick = onOpenMcp,
                    testTag = "session-mcp-nav",
                )
            }
        }
        state.errorMessage?.let { error ->
            item { PreferenceBanner(text = error, tone = PreferenceBannerTone.Error) }
        }
    }
}

@Composable
private fun AttachmentShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        color = preferenceGroupCardColor(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShortcutIcon(icon, enabled)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PreferenceGroupCard(modifier = modifier, content = content)
}

/** 分段绘制分组背景，保留 LazyColumn 每行独立组合与滚动的行为。 */
@Composable
private fun GroupedListItem(
    first: Boolean,
    last: Boolean,
    withIcon: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = if (first) 16.dp else 0.dp,
            topEnd = if (first) 16.dp else 0.dp,
            bottomStart = if (last) 16.dp else 0.dp,
            bottomEnd = if (last) 16.dp else 0.dp,
        ),
        color = preferenceGroupCardColor(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            content()
            if (!last) SheetDivider(withIcon)
        }
    }
}

@Composable
private fun SheetDivider(withIcon: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (withIcon) 56.dp else 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun NavigationRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    leadingIcon: (@Composable (Modifier) -> Unit)? = null,
) {
    PreferenceListItem(
        icon = icon,
        leadingIcon = leadingIcon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun PermissionModePage(
    fullAccess: Boolean,
    onFullAccessChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .testTag("permission-mode-page")
            .padding(bottom = 24.dp),
    ) {
        // 权限模式沿用全局审批开关，运行中也允许切换以放行挂起的确认。
        GroupedCard(modifier = Modifier.padding(top = 8.dp)) {
            listOf(false, true).forEach { approveAll ->
                GimiBottomSheetOptionRow(
                    selected = fullAccess == approveAll,
                    enabled = true,
                    onClick = { onFullAccessChange(approveAll) },
                    label = permissionModeLabel(approveAll),
                    description = stringResource(
                        if (approveAll) R.string.chat_permission_mode_full_description
                        else R.string.chat_permission_mode_request_description,
                    ),
                    modifier = Modifier.testTag(
                        if (approveAll) "permission-mode-full" else "permission-mode-request",
                    ),
                )
                if (!approveAll) SheetDivider(withIcon = false)
            }
        }
    }
}

@Composable
private fun permissionModeLabel(fullAccess: Boolean): String = stringResource(
    if (fullAccess) R.string.chat_permission_mode_full
    else R.string.chat_permission_mode_request,
)

@Composable
private fun ReasoningEffortPage(
    state: ChatAddToChatState,
    onEffortChange: (ReasoningEffort) -> Unit,
) {
    Column(Modifier.wrapContentHeight().testTag("reasoning-effort-page")) {
        PageStatus(state)
        Text(
            text = stringResource(R.string.chat_reasoning_effort_openai_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.wrapContentHeight(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(ReasoningEffort.entries, key = { _, effort -> effort.name }) { index, effort ->
                GroupedListItem(first = index == 0, last = index == ReasoningEffort.entries.lastIndex, withIcon = false) {
                    GimiBottomSheetOptionRow(
                        selected = state.configuration?.reasoningEffort == effort,
                        enabled = state.configuration != null && !state.isMutationBlocked,
                        onClick = { onEffortChange(effort) },
                        label = reasoningEffortLabel(effort),
                        description = reasoningEffortDescription(effort),
                        modifier = Modifier.testTag("reasoning-effort-${effort.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialToolsNavigationRow(
    enabledCount: Int,
    onClick: () -> Unit,
) {
    NavigationRow(
        leadingIcon = { FunctionIcon(it) },
        title = stringResource(R.string.chat_official_tools_title),
        subtitle = enabledCountText(enabledCount, isMcp = false),
        onClick = onClick,
        testTag = "official-tools-nav",
    )
}

@Composable
private fun OfficialToolsDetailPage(
    state: ChatAddToChatState,
    onFunctionEnabledChange: (String, String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    listMaxHeight: Dp = ToolSheetListMaxHeight,
) {
    val totalFunctions = state.officialTools.sumOf { it.functions.size }
    Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
            .testTag("official-tools-detail"),
    ) {
        PageStatus(state)
        val anyLoading = state.officialTools.any { it.isLoadingFunctions }
        val anyError = state.officialTools.any { it.loadError != null && it.functions.isEmpty() }

        when {
            state.officialTools.isEmpty() -> {
                EmptyCenteredMessage(stringResource(R.string.chat_official_tool_functions_empty))
            }

            totalFunctions == 0 && anyLoading -> {
                LoadingState()
            }

            totalFunctions == 0 && anyError -> {
                val firstError = state.officialTools.first { it.loadError != null }
                ErrorState(
                    message = firstError.loadError.orEmpty(),
                    onRetry = { onRetry(firstError.id) },
                )
            }

            totalFunctions == 0 -> {
                EmptyCenteredMessage(stringResource(R.string.chat_official_tool_functions_empty))
            }

            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight)
                        .wrapContentHeight()
                        .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)),
                ) {
                    state.officialTools.groupBy { it.sourceServiceId }.values
                        .forEachIndexed { sourceIndex, sourceTools ->
                            val source = sourceTools.first()
                            item(key = "source-${source.sourceServiceId}") {
                                OfficialToolSourceHeader(
                                    title = source.sourceServiceName.ifBlank {
                                        officialToolLabel(source.id)
                                    },
                                    isFirst = sourceIndex == 0,
                                )
                            }
                            // 每个来源形成一组；逐行保持稳定 key，避免整组成为一个大列表项。
                            val entries = sourceTools.flatMap { tool ->
                                if (tool.functions.isEmpty()) listOf(tool to null)
                                else tool.functions.map { function -> tool to function }
                            }
                            itemsIndexed(entries, key = { _, entry ->
                                "${entry.first.id}-${entry.second?.id ?: "status"}"
                            }) { index, (tool, function) ->
                                GroupedListItem(first = index == 0, last = index == entries.lastIndex) {
                                    when {
                                        function != null -> OfficialToolFunctionRow(
                                            toolId = tool.id,
                                            function = function,
                                            enabled = state.isOfficialFunctionEnabled(tool.id, function.id),
                                            mutationEnabled = !state.isMutationBlocked && state.configuration != null,
                                            onEnabledChange = {
                                                onFunctionEnabledChange(tool.id, function.id, it)
                                            },
                                        )
                                        tool.isLoadingFunctions -> LoadingState(compact = true)
                                        tool.loadError != null -> ErrorState(
                                            message = tool.loadError,
                                            onRetry = { onRetry(tool.id) },
                                            compact = true,
                                        )
                                        else -> Text(
                                            stringResource(R.string.chat_official_tool_functions_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = if (compact) 24.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Text(
            stringResource(R.string.chat_official_tool_functions_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = if (compact) 24.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.chat_official_tool_functions_retry))
        }
    }
}

@Composable
private fun EmptyCenteredMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfficialToolFunctionRow(
    toolId: String,
    function: OfficialToolFunction,
    enabled: Boolean,
    mutationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    GimiBottomSheetSwitchRow(
        leadingIcon = { FunctionIcon(it) },
        label = officialFunctionLabel(toolId, function.id),
        description = officialFunctionDescription(toolId, function.id, function.description),
        checked = enabled,
        enabled = mutationEnabled,
        onCheckedChange = onEnabledChange,
        modifier = Modifier.testTag("official-tool-function-$toolId-${function.id}"),
    )
}

@Composable
private fun McpServersPage(
    state: ChatAddToChatState,
    onEnabledChange: (String, Boolean) -> Unit,
    listMaxHeight: Dp = ToolSheetListMaxHeight,
) {
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
            .testTag("session-mcp-page"),
    ) {
        PageStatus(state)
        if (state.mcpServers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.chat_session_mcp_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    stringResource(R.string.chat_session_mcp_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight)
                    .wrapContentHeight()
                    .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)),
            ) {
                itemsIndexed(state.mcpServers, key = { _, server -> server.id }) { index, server ->
                    GroupedListItem(first = index == 0, last = index == state.mcpServers.lastIndex) {
                        McpServerRow(
                            server = server,
                            enabled = server.id in
                                state.configuration?.enabledMcpServerIds.orEmpty(),
                            mutationEnabled = !state.isMutationBlocked &&
                                state.configuration != null,
                            onEnabledChange = { onEnabledChange(server.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerRow(
    server: McpServer,
    enabled: Boolean,
    mutationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    GimiBottomSheetSwitchRow(
        icon = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
        label = server.name.ifBlank { server.id },
        description = when (server.transport) {
            McpTransport.STREAMABLE_HTTP ->
                stringResource(R.string.chat_mcp_transport_streamable_http)
        },
        checked = enabled,
        enabled = mutationEnabled,
        onCheckedChange = onEnabledChange,
        modifier = Modifier.testTag("mcp-server-${server.id}"),
    )
}

@Composable
private fun rememberLowerBoundaryNestedScrollConnection(
    listState: LazyListState,
): NestedScrollConnection =
    remember(listState) {
        object : NestedScrollConnection {
            /*
             * Keep all three callbacks. Drag and fling travel through separate nested-scroll
             * phases:
             *
             * 1. onPreFling stops an upward release at the list boundary before the sheet starts
             *    settling. Handling only post-fling is too late and caused the original jitter.
             * 2. onPostScroll consumes the unhandled upward drag after LazyColumn reaches its end.
             * 3. onPostFling catches any residual velocity that survives list fling consumption.
             *
             * Every callback reads listState.canScrollForward at event time. Do not capture a
             * one-time Boolean during composition: filtering tools or loading official functions
             * can change the list boundary without recreating this connection.
             *
             * Positive deltas intentionally remain unconsumed. They let the list move away from
             * its end and preserve ModalBottomSheet's downward swipe-to-dismiss behavior.
             */
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity(
                x = 0f,
                y = consumeAtLowerScrollBoundary(
                    availableY = available.y,
                    canScrollForward = listState.canScrollForward,
                ),
            )

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(
                x = 0f,
                y = consumeAtLowerScrollBoundary(
                    availableY = available.y,
                    canScrollForward = listState.canScrollForward,
                ),
            )

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(
                x = 0f,
                y = consumeAtLowerScrollBoundary(
                    availableY = available.y,
                    canScrollForward = listState.canScrollForward,
                ),
            )
        }
    }

@Composable
private fun PageStatus(state: ChatAddToChatState) {
    Column {
        Text(
            stringResource(R.string.chat_session_only_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        if (state.isMutationBlocked) {
            PreferenceBanner(
                text = stringResource(R.string.chat_session_tools_running_notice),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        state.errorMessage?.let {
            PreferenceBanner(
                text = it,
                tone = PreferenceBannerTone.Error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ShortcutIcon(icon: ImageVector, enabled: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun enabledCountText(count: Int, isMcp: Boolean): String =
    if (count == 0) {
        stringResource(R.string.chat_session_none_enabled)
    } else if (isMcp) {
        stringResource(R.string.chat_session_mcp_enabled_count, count)
    } else {
        stringResource(R.string.chat_session_tools_enabled_count, count)
    }

@Composable
private fun reasoningEffortLabel(effort: ReasoningEffort): String = stringResource(
    when (effort) {
        ReasoningEffort.MINIMAL -> R.string.chat_reasoning_effort_minimal
        ReasoningEffort.LOW -> R.string.chat_reasoning_effort_low
        ReasoningEffort.MEDIUM -> R.string.chat_reasoning_effort_medium
        ReasoningEffort.HIGH -> R.string.chat_reasoning_effort_high
    },
)

@Composable
private fun reasoningEffortDescription(effort: ReasoningEffort): String = stringResource(
    when (effort) {
        ReasoningEffort.MINIMAL -> R.string.chat_reasoning_effort_minimal_description
        ReasoningEffort.LOW -> R.string.chat_reasoning_effort_low_description
        ReasoningEffort.MEDIUM -> R.string.chat_reasoning_effort_medium_description
        ReasoningEffort.HIGH -> R.string.chat_reasoning_effort_high_description
    },
)

@Composable
private fun officialToolLabel(toolId: String): String = when (toolId) {
    OfficialToolIds.DEEPSEEK_WEB_SEARCH -> stringResource(R.string.chat_official_tool_deepseek_web_search)
    OfficialToolIds.DEEPSEEK_WEB_FETCH -> stringResource(R.string.chat_official_tool_deepseek_web_fetch)
    OfficialToolIds.ANTHROPIC_WEB_SEARCH,
    OfficialToolIds.MINIMAX_WEB_SEARCH,
    OfficialToolIds.GEMINI_WEB_SEARCH,
    -> stringResource(R.string.chat_official_tool_web_search)
    OfficialToolIds.KIMI_WEB_TOOLS -> stringResource(R.string.chat_official_tool_kimi_web_tools)
    OfficialToolIds.GLM_WEB_SEARCH -> stringResource(R.string.chat_official_tool_glm_web_search)
    OfficialToolIds.GEMINI_URL_CONTEXT -> stringResource(R.string.chat_official_tool_url_context)
    OfficialToolIds.GEMINI_GOOGLE_MAPS -> stringResource(R.string.chat_official_tool_google_maps)
    else -> toolId
}

@Composable
private fun officialToolDescription(toolId: String): String = when (toolId) {
    OfficialToolIds.DEEPSEEK_WEB_SEARCH ->
        stringResource(R.string.chat_official_tool_deepseek_web_search_description)
    OfficialToolIds.DEEPSEEK_WEB_FETCH ->
        stringResource(R.string.chat_official_tool_deepseek_web_fetch_description)
    OfficialToolIds.ANTHROPIC_WEB_SEARCH,
    OfficialToolIds.MINIMAX_WEB_SEARCH,
    OfficialToolIds.GEMINI_WEB_SEARCH,
    -> stringResource(R.string.chat_official_tool_web_search_description)
    OfficialToolIds.KIMI_WEB_TOOLS ->
        stringResource(R.string.chat_official_tool_kimi_web_tools_description)
    OfficialToolIds.GLM_WEB_SEARCH ->
        stringResource(R.string.chat_official_tool_glm_web_search_description)
    OfficialToolIds.GEMINI_URL_CONTEXT ->
        stringResource(R.string.chat_official_tool_url_context_description)
    OfficialToolIds.GEMINI_GOOGLE_MAPS ->
        stringResource(R.string.chat_official_tool_google_maps_description)
    else -> stringResource(R.string.chat_official_tool_default_description)
}

/**
 * 函数行展示:目录 ID 与工具相同的单函数工具复用工具文案;GLM 的两个本地函数
 * 和 MiniMax、Kimi 固定函数使用本地化文案。
 */
@Composable
private fun officialFunctionLabel(toolId: String, functionId: String): String =
    when {
        functionId == toolId -> officialToolLabel(toolId)
        functionId == OfficialToolIds.KIMI_SEARCH ->
            stringResource(R.string.chat_official_tool_kimi_search_basic)
        functionId == OfficialToolIds.KIMI_SEARCH_PRO ->
            stringResource(R.string.chat_official_tool_kimi_search_pro)
        functionId == OfficialToolIds.KIMI_FETCH ->
            stringResource(R.string.chat_official_tool_kimi_fetch)
        functionId == OfficialToolIds.GLM_WEB_SEARCH_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_search)
        functionId == OfficialToolIds.GLM_WEB_READER_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_reader)
        functionId == OfficialToolIds.MINIMAX_TEXT_TO_IMAGE ->
            stringResource(R.string.chat_official_tool_text_to_image)
        functionId == OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE ->
            stringResource(R.string.chat_official_tool_image_to_image)
        else -> functionId
    }

@Composable
private fun officialFunctionDescription(toolId: String, functionId: String, fallback: String): String =
    when {
        functionId == toolId -> officialToolDescription(toolId)
        functionId == OfficialToolIds.KIMI_SEARCH ->
            stringResource(R.string.chat_official_tool_kimi_search_basic_description)
        functionId == OfficialToolIds.KIMI_SEARCH_PRO ->
            stringResource(R.string.chat_official_tool_kimi_search_pro_description)
        functionId == OfficialToolIds.KIMI_FETCH ->
            stringResource(R.string.chat_official_tool_kimi_fetch_description)
        functionId == OfficialToolIds.GLM_WEB_SEARCH_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_search_description)
        functionId == OfficialToolIds.GLM_WEB_READER_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_reader_description)
        functionId == OfficialToolIds.MINIMAX_TEXT_TO_IMAGE ->
            stringResource(R.string.chat_official_tool_text_to_image_description)
        functionId == OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE ->
            stringResource(R.string.chat_official_tool_image_to_image_description)
        else -> fallback
    }

private fun previewAddToChatState(
    configuration: ConversationToolConfiguration? = ConversationToolConfiguration(),
    errorMessage: String? = null,
): ChatAddToChatState = ChatAddToChatState(
    configuration = configuration,
    mcpServers = listOf(
        McpServer(id = "server-1", name = "文件检索服务", transport = McpTransport.STREAMABLE_HTTP),
        McpServer(id = "server-2", name = "浏览器服务", transport = McpTransport.STREAMABLE_HTTP),
    ),
    officialTools = listOf(
        OfficialToolDescriptor(
            id = OfficialToolIds.ANTHROPIC_WEB_SEARCH,
            functions = listOf(
                OfficialToolFunction(
                    id = OfficialToolIds.ANTHROPIC_WEB_SEARCH,
                    name = "Web Search",
                    description = "Search the web for up-to-date information.",
                ),
            ),
        ),
    ),
    errorMessage = errorMessage,
)

@Preview(name = "浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatAddToChatHomePreview() {
    ChatSheetPreviewTheme {
        AddToChatHome(
            state = previewAddToChatState(),
            onTakePhoto = {},
            onChoosePhotos = {},
            onChooseFiles = {},
            imagesEnabled = true,
            filesEnabled = true,
            onOpenReasoningEffort = {},
            onOpenMcp = {},
            onOpenOfficialTools = {},
            onOpenPermissionMode = {},
        )
    }
}

@Composable
private fun OfficialToolSourceHeader(title: String, isFirst: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 32.dp,
            end = 32.dp,
            top = if (isFirst) 4.dp else 20.dp,
            bottom = 8.dp,
        ),
    )
}

@Preview(name = "浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatAddToChatHomeWithErrorPreview() {
    ChatSheetPreviewTheme {
        AddToChatHome(
            state = previewAddToChatState(errorMessage = "工具配置保存失败，请重试。"),
            onTakePhoto = {},
            onChoosePhotos = {},
            onChooseFiles = {},
            imagesEnabled = true,
            filesEnabled = false,
            onOpenReasoningEffort = {},
            onOpenMcp = {},
            onOpenOfficialTools = {},
            onOpenPermissionMode = {},
        )
    }
}

@Preview(name = "浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatAddToChatReasoningEffortPagePreview() {
    ChatSheetPreviewTheme {
        ReasoningEffortPage(
            state = previewAddToChatState(),
            onEffortChange = {},
        )
    }
}

@Preview(name = "浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatAddToChatMcpServersPagePreview() {
    ChatSheetPreviewTheme {
        McpServersPage(
            state = previewAddToChatState(
                configuration = ConversationToolConfiguration(enabledMcpServerIds = setOf("server-1")),
            ),
            onEnabledChange = { _, _ -> },
        )
    }
}

private fun previewMinimaxOfficialToolsState(): ChatAddToChatState =
    previewAddToChatState().copy(
        officialTools = listOf(
            OfficialToolDescriptor(
                id = OfficialToolIds.MINIMAX_WEB_SEARCH,
                sourceServiceId = "minimax",
                sourceServiceName = "MiniMax",
                functions = listOf(
                    OfficialToolFunction(
                        id = OfficialToolIds.MINIMAX_WEB_SEARCH,
                        name = "Web search",
                        description = "Search the web for current information",
                    ),
                ),
            ),
            OfficialToolDescriptor(
                id = OfficialToolIds.MINIMAX_IMAGE_GENERATION,
                sourceServiceId = "minimax",
                sourceServiceName = "MiniMax",
                functions = listOf(
                    OfficialToolFunction(
                        id = OfficialToolIds.MINIMAX_TEXT_TO_IMAGE,
                        name = "Text to image",
                        description = "Generate an image from text",
                    ),
                    OfficialToolFunction(
                        id = OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE,
                        name = "Image to image",
                        description = "Generate an image from a reference image",
                    ),
                ),
            ),
        ),
    )

@Preview(name = "官方工具-浅色", showBackground = true)
@Composable
private fun ChatAddToChatOfficialToolsDetailPagePreview() {
    ChatSheetPreviewTheme(darkTheme = false) {
        OfficialToolsDetailPage(
            state = previewMinimaxOfficialToolsState(),
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}

@Preview(name = "官方工具-深色", showBackground = true)
@Composable
private fun ChatAddToChatOfficialToolsDetailPageDarkPreview() {
    ChatSheetPreviewTheme(darkTheme = true) {
        OfficialToolsDetailPage(
            state = previewMinimaxOfficialToolsState(),
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}

@Preview(name = "Kimi 联网工具-浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "Kimi 联网工具-深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KimiOfficialToolsPreview() {
    ChatSheetPreviewTheme {
        OfficialToolsDetailPage(
            state = previewAddToChatState().copy(
                configuration = ConversationToolConfiguration(
                    enabledOfficialFunctionIds = mapOf(
                        OfficialToolIds.KIMI_WEB_TOOLS to setOf(OfficialToolIds.KIMI_SEARCH_PRO, OfficialToolIds.KIMI_FETCH),
                    ),
                ),
                officialTools = listOf(
                    OfficialToolDescriptor(
                        id = OfficialToolIds.KIMI_WEB_TOOLS,
                        sourceServiceId = "kimi",
                        sourceServiceName = "Kimi",
                        functions = listOf(OfficialToolIds.KIMI_SEARCH, OfficialToolIds.KIMI_SEARCH_PRO, OfficialToolIds.KIMI_FETCH).map {
                            OfficialToolFunction(id = it, name = it, description = it)
                        },
                    ),
                ),
            ),
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}

@Composable
private fun ChatSheetPreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        PreferenceStyleScope(PreferenceStyle.Compact) {
            Surface(color = preferenceCanvasColor()) { content() }
        }
    }
}

@Preview(name = "权限 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "权限 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatPermissionModePagePreview() {
    ChatSheetPreviewTheme {
        PermissionModePage(fullAccess = false, onFullAccessChange = {})
    }
}

@Preview(name = "MCP · 长标题与长列表", widthDp = 320, heightDp = 600, locale = "zh")
@Composable
private fun ChatMcpLongListPreview() {
    ChatSheetPreviewTheme {
        McpServersPage(
            state = previewAddToChatState().copy(
                mcpServers = (1..8).map { index ->
                    McpServer(id = "server-$index", name = "团队共享资料与项目知识库服务器 $index", transport = McpTransport.STREAMABLE_HTTP)
                },
            ),
            onEnabledChange = { _, _ -> },
        )
    }
}

@Preview(name = "MCP · 禁用", widthDp = 390, locale = "zh")
@Composable
private fun ChatMcpBlockedPreview() {
    ChatSheetPreviewTheme(darkTheme = true) {
        McpServersPage(
            state = previewAddToChatState().copy(isMutationBlocked = true),
            onEnabledChange = { _, _ -> },
        )
    }
}

@Preview(name = "MCP · 空列表", widthDp = 390, heightDp = 320, locale = "zh")
@Composable
private fun ChatMcpEmptyPreview() {
    ChatSheetPreviewTheme {
        McpServersPage(
            state = previewAddToChatState().copy(mcpServers = emptyList()),
            onEnabledChange = { _, _ -> },
        )
    }
}

@Preview(name = "官方工具 · 加载", widthDp = 390, heightDp = 320, locale = "zh")
@Composable
private fun ChatOfficialToolsLoadingPreview() {
    ChatSheetPreviewTheme {
        OfficialToolsDetailPage(
            state = previewAddToChatState().let { state ->
                state.copy(officialTools = state.officialTools.map { it.copy(functions = emptyList(), isLoadingFunctions = true) })
            },
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}

@Preview(name = "官方工具 · 加载失败", widthDp = 390, heightDp = 320, locale = "zh")
@Composable
private fun ChatOfficialToolsErrorPreview() {
    ChatSheetPreviewTheme(darkTheme = true) {
        OfficialToolsDetailPage(
            state = previewAddToChatState().let { state ->
                state.copy(officialTools = state.officialTools.map { it.copy(functions = emptyList(), loadError = "工具加载失败，请重试") })
            },
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}

@Preview(name = "DeepSeek-浅色", showBackground = true)
@Preview(name = "DeepSeek-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeepSeekOfficialToolsPreview() {
    ChatSheetPreviewTheme {
        OfficialToolsDetailPage(
            state = previewAddToChatState().copy(
                configuration = ConversationToolConfiguration(
                    enabledOfficialFunctionIds = mapOf(
                        OfficialToolIds.DEEPSEEK_WEB_SEARCH to setOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH),
                    ),
                ),
                officialTools = listOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH, OfficialToolIds.DEEPSEEK_WEB_FETCH).map { id ->
                    OfficialToolDescriptor(
                        id = id,
                        sourceServiceId = "deepseek",
                        sourceServiceName = "DeepSeek",
                        functions = listOf(OfficialToolFunction(id = id, name = id, description = id)),
                    )
                },
            ),
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}
