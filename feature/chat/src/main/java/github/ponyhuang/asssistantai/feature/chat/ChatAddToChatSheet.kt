package github.ponyhuang.asssistantai.feature.chat

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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor

private enum class AddToChatPage {
    HOME,
    LOCAL_TOOLS,
    MCP,
    TOOL_ACCESS,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ChatAddToChatSheet(
    state: ChatAddToChatState,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onLocalToolEnabledChange: (String, Boolean) -> Unit,
    onMcpServerEnabledChange: (String, Boolean) -> Unit,
    onOfficialToolEnabledChange: (String, Boolean) -> Unit,
    onToolAccessModeChange: (ToolAccessMode) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AddToChatPage.HOME) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = page != AddToChatPage.HOME) {
        page = AddToChatPage.HOME
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
                    val pageHeightModifier = if (
                        currentPage == AddToChatPage.LOCAL_TOOLS ||
                        currentPage == AddToChatPage.MCP && state.mcpServers.isNotEmpty()
                    ) {
                        Modifier.height(maximumPageHeight)
                    } else {
                        Modifier.wrapContentHeight()
                    }
                    Column(
                        modifier = pageHeightModifier
                            .widthIn(max = 640.dp),
                    ) {
                        SheetHeader(
                            title = when (currentPage) {
                                AddToChatPage.HOME -> stringResource(R.string.chat_add_to_chat_title)
                                AddToChatPage.LOCAL_TOOLS -> stringResource(R.string.chat_session_tools_title)
                                AddToChatPage.MCP -> stringResource(R.string.chat_session_mcp_title)
                                AddToChatPage.TOOL_ACCESS -> stringResource(R.string.chat_tool_access_title)
                            },
                            isRoot = currentPage == AddToChatPage.HOME,
                            onNavigationClick = {
                                if (currentPage == AddToChatPage.HOME) onDismiss()
                                else page = AddToChatPage.HOME
                            },
                        )
                        when (currentPage) {
                            AddToChatPage.HOME -> AddToChatHome(
                                state = state,
                                onTakePhoto = onTakePhoto,
                                onChoosePhotos = onChoosePhotos,
                                onOpenTools = { page = AddToChatPage.LOCAL_TOOLS },
                                onOpenMcp = { page = AddToChatPage.MCP },
                                onOpenToolAccess = { page = AddToChatPage.TOOL_ACCESS },
                                onOfficialToolEnabledChange = onOfficialToolEnabledChange,
                            )
                            AddToChatPage.LOCAL_TOOLS -> LocalToolsPage(
                                state = state,
                                onEnabledChange = onLocalToolEnabledChange,
                            )
                            AddToChatPage.MCP -> McpServersPage(
                                state = state,
                                onEnabledChange = onMcpServerEnabledChange,
                            )
                            AddToChatPage.TOOL_ACCESS -> ToolAccessPage(
                                selectedMode = state.configuration?.toolAccessMode
                                    ?: ToolAccessMode.AUTO,
                                enabled = !state.isMutationBlocked && state.configuration != null,
                                onSelected = onToolAccessModeChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    isRoot: Boolean,
    onNavigationClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onNavigationClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .testTag(if (isRoot) "add-to-chat-close" else "add-to-chat-back"),
        ) {
            Icon(
                imageVector = if (isRoot) {
                    Icons.Default.Close
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
                contentDescription = stringResource(
                    if (isRoot) R.string.chat_add_to_chat_close else R.string.chat_add_to_chat_back,
                ),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun AddToChatHome(
    state: ChatAddToChatState,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenToolAccess: () -> Unit,
    onOfficialToolEnabledChange: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .wrapContentHeight()
            .nestedScroll(rememberLowerBoundaryNestedScrollConnection())
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
                    modifier = Modifier.weight(1f),
                )
                AttachmentShortcut(
                    icon = Icons.Default.PhotoLibrary,
                    label = stringResource(R.string.chat_add_to_chat_photos),
                    onClick = onChoosePhotos,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.supportedOfficialToolIds.isNotEmpty()) {
            item {
                GroupedCard {
                    state.supportedOfficialToolIds.forEachIndexed { index, toolId ->
                        OfficialToolRow(
                            toolId = toolId,
                            enabled = toolId in state.serviceId?.let { serviceId ->
                                state.configuration?.enabledOfficialToolIds(serviceId)
                            }.orEmpty(),
                            mutationEnabled = !state.isMutationBlocked &&
                                state.configuration != null,
                            onEnabledChange = { onOfficialToolEnabledChange(toolId, it) },
                        )
                        if (index != state.supportedOfficialToolIds.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                        }
                    }
                }
            }
        }
        item {
            GroupedCard {
                NavigationRow(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.chat_session_tools_title),
                    subtitle = enabledCountText(state.enabledLocalToolCount, isMcp = false),
                    onClick = onOpenTools,
                    testTag = "session-tools-nav",
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                NavigationRow(
                    icon = Icons.Default.Work,
                    title = stringResource(R.string.chat_tool_access_title),
                    subtitle = toolAccessModeLabel(
                        state.configuration?.toolAccessMode ?: ToolAccessMode.AUTO,
                    ),
                    onClick = onOpenToolAccess,
                    testTag = "tool-access-nav",
                )
            }
        }
        item {
            GroupedCard {
                NavigationRow(
                    icon = Icons.Default.Link,
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
        if (state.configuration == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
private fun GroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
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
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun OfficialToolRow(
    toolId: String,
    enabled: Boolean,
    mutationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .toggleable(
                value = enabled,
                enabled = mutationEnabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(
            if (toolId == OfficialToolIds.WEB_SEARCH) {
                Icons.Default.Language
            } else {
                Icons.Default.Functions
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(officialToolLabel(toolId), style = MaterialTheme.typography.bodyLarge)
            Text(
                officialToolDescription(toolId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = mutationEnabled,
        )
    }
}

@Composable
private fun LocalToolsPage(
    state: ChatAddToChatState,
    onEnabledChange: (String, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SessionToolFilter.ALL) }
    val visibleTools = remember(state, query, filter) {
        state.visibleLocalTools(query, filter)
    }

    Column(modifier = Modifier.fillMaxSize().testTag("session-tools-page")) {
        PageStatus(state)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.chat_session_tools_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SessionToolFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(toolFilterLabel(option)) },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberLowerBoundaryNestedScrollConnection()),
        ) {
            items(visibleTools, key = ToolDescriptor::id) { tool ->
                LocalToolRow(
                    tool = tool,
                    enabled = tool.id in state.configuration?.enabledLocalToolIds.orEmpty(),
                    mutationEnabled = !state.isMutationBlocked &&
                        state.configuration != null,
                    onEnabledChange = { onEnabledChange(tool.id, it) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun LocalToolRow(
    tool: ToolDescriptor,
    enabled: Boolean,
    mutationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(
                value = enabled,
                enabled = mutationEnabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Icons.Default.Build)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(tool.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                tool.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null, enabled = mutationEnabled)
    }
}

@Composable
private fun McpServersPage(
    state: ChatAddToChatState,
    onEnabledChange: (String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("session-mcp-page")) {
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
                    Icons.Default.Link,
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
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(rememberLowerBoundaryNestedScrollConnection()),
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
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .toggleable(
                value = enabled,
                enabled = mutationEnabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Icons.Default.Link)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(server.name.ifBlank { server.id }, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (server.transport) {
                    McpTransport.SSE -> stringResource(R.string.chat_mcp_transport_sse)
                    McpTransport.STREAMABLE_HTTP ->
                        stringResource(R.string.chat_mcp_transport_streamable_http)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                server.endpointUrl,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null, enabled = mutationEnabled)
    }
}

@Composable
private fun ToolAccessPage(
    selectedMode: ToolAccessMode,
    enabled: Boolean,
    onSelected: (ToolAccessMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .testTag("tool-access-page")
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ToolAccessMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .selectable(
                        selected = selectedMode == mode,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(mode) },
                    )
                    .testTag("tool-access-${mode.name}")
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        toolAccessModeLabel(mode),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selectedMode == mode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        toolAccessModeDescription(mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (selectedMode == mode) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InlineNotice(
            text = stringResource(R.string.chat_tool_access_todo_notice),
            isError = false,
        )
    }
}

@Composable
private fun rememberLowerBoundaryNestedScrollConnection(): NestedScrollConnection =
    remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(
                x = 0f,
                y = consumeAtLowerScrollBoundary(available.y),
            )

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(
                x = 0f,
                y = consumeAtLowerScrollBoundary(available.y),
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
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
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
private fun toolFilterLabel(filter: SessionToolFilter): String = stringResource(
    when (filter) {
        SessionToolFilter.ALL -> R.string.chat_session_tools_filter_all
        SessionToolFilter.ENABLED -> R.string.chat_session_tools_filter_enabled
        SessionToolFilter.DISABLED -> R.string.chat_session_tools_filter_disabled
    },
)

@Composable
private fun toolAccessModeLabel(mode: ToolAccessMode): String = stringResource(
    when (mode) {
        ToolAccessMode.AUTO -> R.string.chat_tool_access_auto
        ToolAccessMode.ON_DEMAND -> R.string.chat_tool_access_on_demand
        ToolAccessMode.ALWAYS_AVAILABLE -> R.string.chat_tool_access_always
    },
)

@Composable
private fun toolAccessModeDescription(mode: ToolAccessMode): String = stringResource(
    when (mode) {
        ToolAccessMode.AUTO -> R.string.chat_tool_access_auto_description
        ToolAccessMode.ON_DEMAND -> R.string.chat_tool_access_on_demand_description
        ToolAccessMode.ALWAYS_AVAILABLE -> R.string.chat_tool_access_always_description
    },
)

@Composable
private fun officialToolLabel(toolId: String): String = when (toolId) {
    OfficialToolIds.WEB_SEARCH -> stringResource(R.string.chat_official_tool_web_search)
    OfficialToolIds.KIMI_FORMULAS -> stringResource(R.string.chat_official_tool_kimi_formulas)
    else -> toolId
}

@Composable
private fun officialToolDescription(toolId: String): String = when (toolId) {
    OfficialToolIds.WEB_SEARCH ->
        stringResource(R.string.chat_official_tool_web_search_description)
    OfficialToolIds.KIMI_FORMULAS ->
        stringResource(R.string.chat_official_tool_kimi_formulas_description)
    else -> stringResource(R.string.chat_official_tool_default_description)
}
