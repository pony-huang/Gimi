package github.ponyhuang.gimi.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.ui.components.GimiBottomSheet
import github.ponyhuang.gimi.ui.components.GimiBottomSheetHeader
import github.ponyhuang.gimi.ui.components.GimiBottomSheetOptionRow
import github.ponyhuang.gimi.ui.components.GimiBottomSheetSwitchRow
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

private const val COMPACT_SHEET_ITEM_LIMIT: Int = 3

private enum class AddToChatPage {
    HOME,
    TOOL_ACCESS,
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
    onToolAccessModeChange: (ToolAccessMode) -> Unit,
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

    GimiBottomSheet(onDismissRequest = onDismiss) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maximumPageHeight = maxHeight * 0.92f
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
                    val isFullHeightPage = when (currentPage) {
                        AddToChatPage.MCP -> state.mcpServers.size > COMPACT_SHEET_ITEM_LIMIT
                        AddToChatPage.OFFICIAL_TOOL -> state.officialTools.sumOf { it.functions.size } >
                            COMPACT_SHEET_ITEM_LIMIT
                        else -> false
                    }
                    val pageHeightModifier = if (isFullHeightPage) {
                        Modifier.height(maximumPageHeight)
                    } else {
                        Modifier.wrapContentHeight()
                    }
                    Column(
                        modifier = pageHeightModifier
                            .widthIn(max = 640.dp),
                    ) {
                        GimiBottomSheetHeader(
                            title = when (currentPage) {
                                AddToChatPage.HOME -> stringResource(R.string.chat_add_to_chat_title)
                                AddToChatPage.TOOL_ACCESS ->
                                    stringResource(R.string.chat_tool_access_title)
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
                                onOpenToolAccess = { page = AddToChatPage.TOOL_ACCESS },
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
                            AddToChatPage.TOOL_ACCESS -> ToolAccessPage(
                                state = state,
                                onModeChange = onToolAccessModeChange,
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
                            )
                            AddToChatPage.OFFICIAL_TOOL -> OfficialToolsDetailPage(
                                state = state,
                                onFunctionEnabledChange = onOfficialToolFunctionEnabledChange,
                                onRetry = onOfficialToolFunctionsRetry,
                            )
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
    onOpenToolAccess: () -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
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
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.chat_tool_access_title),
                    subtitle = toolAccessModeLabel(
                        state.configuration?.toolAccessMode ?: ToolAccessMode.ALWAYS_AVAILABLE,
                    ),
                    onClick = onOpenToolAccess,
                    testTag = "tool-access-nav",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                NavigationRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.chat_permission_mode_title),
                    subtitle = permissionModeLabel(state.fullAccess),
                    onClick = onOpenPermissionMode,
                    testTag = "permission-mode-nav",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                NavigationRow(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.chat_reasoning_effort_title),
                    subtitle = reasoningEffortLabel(
                        state.configuration?.reasoningEffort ?: ReasoningEffort.MEDIUM,
                    ),
                    onClick = onOpenReasoningEffort,
                    testTag = "reasoning-effort-nav",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
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
            item { InlineNotice(error, isError = true) }
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
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(24.dp),
        color = preferenceGroupCardColor(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconBubble(icon)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = preferenceGroupCardColor(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
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
        }
    }
}

@Composable
private fun permissionModeLabel(fullAccess: Boolean): String = stringResource(
    if (fullAccess) R.string.chat_permission_mode_full
    else R.string.chat_permission_mode_request,
)

@Composable
private fun ToolAccessPage(
    state: ChatAddToChatState,
    onModeChange: (ToolAccessMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .testTag("tool-access-page"),
    ) {
        PageStatus(state)
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.wrapContentHeight(),
        ) {
            items(ToolAccessMode.entries, key = ToolAccessMode::name) { mode ->
                ToolAccessModeRow(
                    mode = mode,
                    selected = state.configuration?.toolAccessMode == mode,
                    enabled = state.configuration != null && !state.isMutationBlocked,
                    onClick = { onModeChange(mode) },
                )
            }
        }
    }
}

@Composable
private fun ToolAccessModeRow(
    mode: ToolAccessMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GimiBottomSheetOptionRow(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = toolAccessModeLabel(mode),
        description = toolAccessModeDescription(mode),
        modifier = Modifier.testTag(
            when (mode) {
                ToolAccessMode.ON_DEMAND -> "tool-access-on-demand"
                ToolAccessMode.ALWAYS_AVAILABLE -> "tool-access-always"
            },
        ),
    )
}

@Composable
private fun ReasoningEffortPage(
    state: ChatAddToChatState,
    onEffortChange: (ReasoningEffort) -> Unit,
) {
    Column(Modifier.wrapContentHeight().testTag("reasoning-effort-page")) {
        PageStatus(state)
        LazyColumn(
            modifier = Modifier.wrapContentHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(ReasoningEffort.entries, key = ReasoningEffort::name) { effort ->
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

@Composable
private fun OfficialToolsNavigationRow(
    enabledCount: Int,
    onClick: () -> Unit,
) {
    NavigationRow(
        icon = Icons.Default.Functions,
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
) {
    val totalFunctions = state.officialTools.sumOf { it.functions.size }
    val isScrollable = totalFunctions > COMPACT_SHEET_ITEM_LIMIT
    Column(
        modifier = (if (isScrollable) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
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
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier
                        .then(if (isScrollable) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
                        .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)),
                ) {
                    state.officialTools.forEachIndexed { index, tool ->
                        if (state.officialTools.size > 1) {
                            item(key = "header-${tool.id}") {
                                OfficialToolGroupHeader(
                                    title = officialToolLabel(tool.id),
                                    subtitle = officialToolDescription(tool.id),
                                    isFirst = index == 0,
                                )
                            }
                        }
                        if (tool.functions.isEmpty() && tool.isLoadingFunctions) {
                            item(key = "loading-${tool.id}") {
                                LoadingState(compact = true)
                            }
                        } else if (tool.functions.isEmpty() && tool.loadError != null) {
                            item(key = "error-${tool.id}") {
                                ErrorState(
                                    message = tool.loadError,
                                    onRetry = { onRetry(tool.id) },
                                    compact = true,
                                )
                            }
                        } else {
                            items(tool.functions, key = { "${tool.id}-${it.id}" }) { function ->
                                OfficialToolFunctionRow(
                                    toolId = tool.id,
                                    function = function,
                                    enabled = state.isOfficialFunctionEnabled(tool.id, function.id),
                                    mutationEnabled = !state.isMutationBlocked &&
                                        state.configuration != null,
                                    onEnabledChange = {
                                        onFunctionEnabledChange(tool.id, function.id, it)
                                    },
                                )
                                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
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
private fun OfficialToolGroupHeader(
    title: String,
    subtitle: String,
    isFirst: Boolean,
) {
    Column(modifier = Modifier.padding(top = if (isFirst) 4.dp else 18.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
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
            .fillMaxSize()
            .padding(horizontal = 36.dp),
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
        icon = Icons.Default.Functions,
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
) {
    val listState = rememberLazyListState()
    val isScrollable = state.mcpServers.size > COMPACT_SHEET_ITEM_LIMIT
    Column(
        modifier = (if (isScrollable) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
            .testTag("session-mcp-page"),
    ) {
        PageStatus(state)
        if (state.mcpServers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp),
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
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .then(if (isScrollable) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
                    .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)),
            ) {
                items(state.mcpServers, key = McpServer::id) { server ->
                    McpServerRow(
                        server = server,
                        enabled = server.id in
                            state.configuration?.enabledMcpServerIds.orEmpty(),
                        mutationEnabled = !state.isMutationBlocked &&
                            state.configuration != null,
                        onEnabledChange = { onEnabledChange(server.id, it) },
                    )
                    HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
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
            McpTransport.SSE -> stringResource(R.string.chat_mcp_transport_sse)
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (state.isMutationBlocked) {
            InlineNotice(
                text = stringResource(R.string.chat_session_tools_running_notice),
                isError = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        state.errorMessage?.let {
            InlineNotice(
                text = it,
                isError = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun InlineNotice(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun IconBubble(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
private fun toolAccessModeLabel(mode: ToolAccessMode): String = stringResource(
    when (mode) {
        ToolAccessMode.ON_DEMAND -> R.string.chat_tool_access_on_demand
        ToolAccessMode.ALWAYS_AVAILABLE -> R.string.chat_tool_access_always
    },
)

@Composable
private fun toolAccessModeDescription(mode: ToolAccessMode): String = stringResource(
    when (mode) {
        ToolAccessMode.ON_DEMAND -> R.string.chat_tool_access_on_demand_description
        ToolAccessMode.ALWAYS_AVAILABLE -> R.string.chat_tool_access_always_description
    },
)

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
    OfficialToolIds.OPENAI_WEB_SEARCH,
    OfficialToolIds.ANTHROPIC_WEB_SEARCH,
    OfficialToolIds.MINIMAX_WEB_SEARCH,
    OfficialToolIds.MIMO_WEB_SEARCH,
    OfficialToolIds.GEMINI_WEB_SEARCH,
    -> stringResource(R.string.chat_official_tool_web_search)
    OfficialToolIds.KIMI_FORMULAS -> stringResource(R.string.chat_official_tool_kimi_formulas)
    OfficialToolIds.GLM_WEB_SEARCH -> stringResource(R.string.chat_official_tool_glm_web_search)
    OfficialToolIds.GEMINI_URL_CONTEXT -> stringResource(R.string.chat_official_tool_url_context)
    OfficialToolIds.GEMINI_GOOGLE_MAPS -> stringResource(R.string.chat_official_tool_google_maps)
    else -> toolId
}

@Composable
private fun officialToolDescription(toolId: String): String = when (toolId) {
    OfficialToolIds.OPENAI_WEB_SEARCH,
    OfficialToolIds.ANTHROPIC_WEB_SEARCH,
    OfficialToolIds.MINIMAX_WEB_SEARCH,
    OfficialToolIds.MIMO_WEB_SEARCH,
    OfficialToolIds.GEMINI_WEB_SEARCH,
    -> stringResource(R.string.chat_official_tool_web_search_description)
    OfficialToolIds.KIMI_FORMULAS ->
        stringResource(R.string.chat_official_tool_kimi_formulas_description)
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
 * 按厂商函数名映射;Kimi 公式等动态函数直接展示厂商返回的名称/描述。
 */
@Composable
private fun officialFunctionLabel(toolId: String, functionId: String): String =
    when {
        functionId == toolId -> officialToolLabel(toolId)
        functionId == OfficialToolIds.GLM_WEB_SEARCH_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_search)
        functionId == OfficialToolIds.GLM_WEB_READER_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_reader)
        else -> functionId
    }

@Composable
private fun officialFunctionDescription(toolId: String, functionId: String, fallback: String): String =
    when {
        functionId == toolId -> officialToolDescription(toolId)
        functionId == OfficialToolIds.GLM_WEB_SEARCH_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_search_description)
        functionId == OfficialToolIds.GLM_WEB_READER_FUNCTION ->
            stringResource(R.string.chat_official_tool_web_reader_description)
        else -> fallback
    }

private fun previewAddToChatState(
    configuration: ConversationToolConfiguration? = ConversationToolConfiguration(),
    errorMessage: String? = null,
): ChatAddToChatState = ChatAddToChatState(
    configuration = configuration,
    mcpServers = listOf(
        McpServer(id = "server-1", name = "文件检索服务", transport = McpTransport.STREAMABLE_HTTP),
        McpServer(id = "server-2", name = "浏览器服务", transport = McpTransport.SSE),
    ),
    officialTools = listOf(
        OfficialToolDescriptor(
            id = OfficialToolIds.OPENAI_WEB_SEARCH,
            functions = listOf(
                OfficialToolFunction(
                    id = OfficialToolIds.OPENAI_WEB_SEARCH,
                    name = "Web Search",
                    description = "Search the web for up-to-date information.",
                ),
            ),
        ),
    ),
    errorMessage = errorMessage,
)

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatHomePreview() {
    AsssistantaiTheme {
        AddToChatHome(
            state = previewAddToChatState(),
            onTakePhoto = {},
            onChoosePhotos = {},
            onChooseFiles = {},
            imagesEnabled = true,
            filesEnabled = true,
            onOpenToolAccess = {},
            onOpenReasoningEffort = {},
            onOpenMcp = {},
            onOpenOfficialTools = {},
            onOpenPermissionMode = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatHomeWithErrorPreview() {
    AsssistantaiTheme {
        AddToChatHome(
            state = previewAddToChatState(errorMessage = "工具配置保存失败，请重试。"),
            onTakePhoto = {},
            onChoosePhotos = {},
            onChooseFiles = {},
            imagesEnabled = true,
            filesEnabled = false,
            onOpenToolAccess = {},
            onOpenReasoningEffort = {},
            onOpenMcp = {},
            onOpenOfficialTools = {},
            onOpenPermissionMode = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatToolAccessPagePreview() {
    AsssistantaiTheme {
        ToolAccessPage(
            state = previewAddToChatState(),
            onModeChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatReasoningEffortPagePreview() {
    AsssistantaiTheme {
        ReasoningEffortPage(
            state = previewAddToChatState(),
            onEffortChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatMcpServersPagePreview() {
    AsssistantaiTheme {
        McpServersPage(
            state = previewAddToChatState(
                configuration = ConversationToolConfiguration(enabledMcpServerIds = setOf("server-1")),
            ),
            onEnabledChange = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatAddToChatOfficialToolsDetailPagePreview() {
    AsssistantaiTheme {
        OfficialToolsDetailPage(
            state = previewAddToChatState(),
            onFunctionEnabledChange = { _, _, _ -> },
            onRetry = {},
        )
    }
}
