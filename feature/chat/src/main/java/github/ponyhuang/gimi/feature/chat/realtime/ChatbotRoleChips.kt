package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.GimiTheme

internal val RoleChipsRestingHeight = 48.dp

@Composable
internal fun ChatbotRoleChipsRoute(
    conversationId: String,
    edgePadding: Dp = 18.dp,
    viewModel: ChatbotPromptViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId) { viewModel.bind(conversationId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val call by viewModel.callState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(conversationId) { mutableStateOf(false) }
    LaunchedEffect(call.configurationLocked) { if (call.configurationLocked) expanded = false }
    ChatbotRoleChips(
        state = state,
        expanded = expanded && !call.configurationLocked,
        locked = call.configurationLocked,
        edgePadding = edgePadding,
        onToggleExpanded = { expanded = !expanded },
        onSelect = { viewModel.onAction(ChatbotPromptAction.Select(it)) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatbotRoleChips(
    state: ChatbotPromptUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (String?) -> Unit,
    edgePadding: Dp = 18.dp,
    locked: Boolean = false,
) {
    val chips: @Composable () -> Unit = {
        if (locked) {
            val current = (state.builtInPresets + state.presets).firstOrNull { it.id == state.selectedPresetId }
            RoleChip(
                label = current?.name ?: stringResource(R.string.voice_prompt_preset_none),
                selected = true,
                enabled = false,
                onClick = {},
            )
        } else {
            FilterChip(
                selected = expanded,
                onClick = onToggleExpanded,
                colors = roleChipColors(),
                label = {
                    val labelRes = if (expanded) R.string.chatbot_role_chips_show_less else R.string.chatbot_role_chips_show_all
                    Text(stringResource(labelRes))
                },
                leadingIcon = {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
            )
            RoleChip(
                label = stringResource(R.string.voice_prompt_preset_none),
                selected = state.selectedPresetId == null,
                enabled = state.canSelect,
                onClick = { onSelect(null) },
            )
            for (preset in state.builtInPresets + state.presets) {
                RoleChip(
                    label = preset.name,
                    selected = state.selectedPresetId == preset.id,
                    enabled = state.canSelect,
                    onClick = { onSelect(preset.id) },
                )
            }
        }
    }
    val horizontalScroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize(alignment = Alignment.BottomStart),
    ) {
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(horizontal = edgePadding)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { chips() }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScroll).padding(horizontal = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { chips() }
        }
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        colors = roleChipColors(),
        modifier = Modifier.widthIn(max = 220.dp),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun roleChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Preview(widthDp = 350)
@Composable
private fun ChatbotRoleChipsCollapsedPreview() {
    GimiTheme { RoleChipsPreview(expanded = false) }
}

@Preview(widthDp = 350, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatbotRoleChipsExpandedPreview() {
    GimiTheme { RoleChipsPreview(expanded = true) }
}

@Preview(widthDp = 350)
@Composable
private fun ChatbotRoleChipsLockedPreview() {
    GimiTheme { RoleChipsPreview(expanded = false, locked = true) }
}

@Composable
private fun RoleChipsPreview(expanded: Boolean, locked: Boolean = false) {
    ChatbotRoleChips(
        state = ChatbotPromptUiState(
            presets = listOf("Travel guide", "English tutor", "Writing coach", "Interview coach")
                .map { ChatbotRolePreset(id = it, name = it, content = it) },
            selectedPresetId = "Travel guide",
            canSelect = true,
        ),
        expanded = expanded,
        locked = locked,
        onToggleExpanded = {},
        onSelect = {},
    )
}
