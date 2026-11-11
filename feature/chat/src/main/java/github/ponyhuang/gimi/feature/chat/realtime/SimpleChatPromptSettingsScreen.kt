package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/**
 * 简单聊天预设管理页：官方预设与自建预设分两组展示。
 * 官方预设只读可选中，行尾菜单只有「查看提示词」；自建预设可编辑删除，另加一行新建入口。
 * 预设数量由用户自建，规模小，直接在卡片 Column 内展开即可，不需要独立懒加载。
 */
@Composable
fun SimpleChatPromptSettingsScreen(
    state: SimpleChatPromptUiState,
    onAction: (SimpleChatPromptAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.voice_prompt_settings_section)) }
            item { PreferenceSectionNote(stringResource(R.string.voice_prompt_settings_note)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = stringResource(R.string.voice_prompt_preset_none),
                        subtitle = stringResource(R.string.voice_prompt_preset_none_detail),
                        showDivider = state.builtInPresets.isNotEmpty(),
                        onClick = { onAction(SimpleChatPromptAction.Select(null)) },
                        trailingContent = {
                            // 单选状态由仓库持有，圆钮本身不响应点击，统一走行点击。
                            // 不可点击的圆钮没有 48dp 最小触摸目标，会比下方可点击圆钮的圆心偏右；
                            // 补一个等宽容器让所有圆钮的圆心落在同一条竖线上。
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                RadioButton(selected = state.selectedPresetId == null, onClick = null)
                            }
                        },
                    )
                    state.builtInPresets.forEachIndexed { index, preset ->
                        var menuOpen by remember(preset.id) { mutableStateOf(false) }
                        PreferenceListItem(
                            icon = Icons.Default.AutoAwesome,
                            title = preset.name,
                            subtitle = preset.description.ifBlank { preset.content },
                            showDivider = index != state.builtInPresets.lastIndex,
                            onClick = { onAction(SimpleChatPromptAction.Select(preset.id)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, stringResource(R.string.voice_more_options))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.voice_prompt_preset_view)) },
                                            onClick = {
                                                menuOpen = false
                                                onAction(SimpleChatPromptAction.ViewPrompt(preset.id))
                                            },
                                        )
                                    }
                                    RadioButton(
                                        selected = state.selectedPresetId == preset.id,
                                        onClick = { onAction(SimpleChatPromptAction.Select(preset.id)) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.voice_prompt_preset_mine_section)) }
            item { PreferenceSectionNote(stringResource(R.string.voice_prompt_preset_mine_note)) }
            item {
                PreferenceGroupCard {
                    state.presets.forEach { preset ->
                        var menuOpen by remember(preset.id) { mutableStateOf(false) }
                        PreferenceListItem(
                            icon = Icons.Default.EditNote,
                            title = preset.name,
                            subtitle = preset.content,
                            showDivider = true,
                            onClick = { onAction(SimpleChatPromptAction.Select(preset.id)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, stringResource(R.string.voice_more_options))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.voice_prompt_preset_edit)) },
                                            onClick = {
                                                menuOpen = false
                                                onAction(SimpleChatPromptAction.StartEdit(preset.id))
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.voice_prompt_preset_delete)) },
                                            onClick = {
                                                menuOpen = false
                                                onAction(SimpleChatPromptAction.RequestDelete(preset.id))
                                            },
                                        )
                                    }
                                    RadioButton(
                                        selected = state.selectedPresetId == preset.id,
                                        onClick = { onAction(SimpleChatPromptAction.Select(preset.id)) },
                                    )
                                }
                            },
                        )
                    }
                    PreferenceListItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.voice_prompt_preset_create),
                        showDivider = state.presets.isNotEmpty(),
                        onClick = { onAction(SimpleChatPromptAction.StartCreate) },
                    )
                }
            }
        }
    }
    state.editing?.let { editing ->
        SimpleChatPromptEditDialog(
            initialName = editing.name,
            initialContent = editing.content,
            creating = editing.id.isBlank(),
            onAction = onAction,
        )
    }
    state.confirmDelete?.let { preset ->
        SimpleChatPromptDeleteDialog(presetName = preset.name, onAction = onAction)
    }
    state.viewing?.let { preset ->
        SimpleChatPromptViewDialog(preset = preset, onAction = onAction)
    }
}

/** 新建与编辑共用一个弹层；名称和正文去空白后必须同时非空才能保存。 */
@Composable
private fun SimpleChatPromptEditDialog(
    initialName: String,
    initialContent: String,
    creating: Boolean,
    onAction: (SimpleChatPromptAction) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var content by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = { onAction(SimpleChatPromptAction.DismissEdit) },
        title = { Text(stringResource(if (creating) R.string.voice_prompt_preset_create else R.string.voice_prompt_preset_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.voice_prompt_preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.voice_prompt_preset_content)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && content.isNotBlank(),
                onClick = { onAction(SimpleChatPromptAction.Save(name, content)) },
            ) { Text(stringResource(R.string.voice_prompt_preset_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SimpleChatPromptAction.DismissEdit) }) {
                Text(stringResource(R.string.voice_prompt_preset_cancel))
            }
        },
    )
}

@Composable
private fun SimpleChatPromptDeleteDialog(
    presetName: String,
    onAction: (SimpleChatPromptAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(SimpleChatPromptAction.DismissDelete) },
        title = { Text(stringResource(R.string.voice_prompt_preset_delete_title)) },
        text = { Text(stringResource(R.string.voice_prompt_preset_delete_text, presetName)) },
        confirmButton = {
            TextButton(onClick = { onAction(SimpleChatPromptAction.ConfirmDelete) }) {
                Text(stringResource(R.string.voice_prompt_preset_delete),
                    color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SimpleChatPromptAction.DismissDelete) }) {
                Text(stringResource(R.string.voice_prompt_preset_cancel))
            }
        },
    )
}

/** 官方预设正文只读展示：完整提示词较长，弹层内可滚动查看。 */
@Composable
private fun SimpleChatPromptViewDialog(
    preset: SimpleChatPromptPreset,
    onAction: (SimpleChatPromptAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(SimpleChatPromptAction.DismissViewPrompt) },
        title = { Text(preset.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = preset.content,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(SimpleChatPromptAction.DismissViewPrompt) }) {
                Text(stringResource(R.string.voice_prompt_preset_view_close))
            }
        },
    )
}

private val PreviewPresets = listOf(
    SimpleChatPromptPreset("p1", "简洁速答", "直接给结论，单句回答，不超过两句话。"),
    SimpleChatPromptPreset("p2", "口语陪练", "全程使用简单日常的英语与我对话，我答错时温和纠正。"),
)

private val PreviewBuiltInPresets = listOf(
    SimpleChatPromptPreset(
        id = "builtin.tieba_roast",
        name = "贴吧嘴臭老哥",
        description = "模仿贴吧老哥的嘴臭风格，犀利吐槽、情绪宣泄，适合闲聊发泄。",
        content = "# Role and Objective\n\n你是「贴吧嘴臭老哥」……",
    ),
    SimpleChatPromptPreset(
        id = "builtin.emotional_companion",
        name = "情感陪伴师",
        description = "温柔细腻的情绪支持与陪伴，会共情、能恋爱式互动，适合低落时寻求慰藉。",
        content = "# Role and Objective\n\n你是「情感陪伴師」……",
    ),
)

@Composable
private fun SimpleChatPromptPagePreview(dark: Boolean, empty: Boolean = false) {
    github.ponyhuang.gimi.ui.theme.GimiTheme(darkTheme = dark) {
        SimpleChatPromptSettingsScreen(
            state = SimpleChatPromptUiState(
                presets = if (empty) emptyList() else PreviewPresets,
                builtInPresets = if (empty) emptyList() else PreviewBuiltInPresets,
                selectedPresetId = if (empty) null else "p2",
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Presets light", showBackground = true, widthDp = 400, heightDp = 720, locale = "zh-rCN")
@Composable
private fun SimpleChatPromptPageLightPreview() {
    SimpleChatPromptPagePreview(dark = false)
}

@Preview(name = "Presets dark", showBackground = true, widthDp = 400, heightDp = 720, locale = "zh-rCN",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SimpleChatPromptPageDarkPreview() {
    SimpleChatPromptPagePreview(dark = true)
}

@Preview(name = "Empty light", showBackground = true, widthDp = 400, heightDp = 480, locale = "zh-rCN")
@Composable
private fun SimpleChatPromptPageEmptyLightPreview() {
    SimpleChatPromptPagePreview(dark = false, empty = true)
}

@Preview(name = "Empty dark", showBackground = true, widthDp = 400, heightDp = 480, locale = "zh-rCN",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SimpleChatPromptPageEmptyDarkPreview() {
    SimpleChatPromptPagePreview(dark = true, empty = true)
}

@Preview(name = "Edit dialog", showBackground = true, widthDp = 400, locale = "zh-rCN")
@Composable
private fun SimpleChatPromptEditDialogPreview() {
    github.ponyhuang.gimi.ui.theme.GimiTheme {
        SimpleChatPromptSettingsScreen(
            state = SimpleChatPromptUiState(
                presets = PreviewPresets,
                builtInPresets = PreviewBuiltInPresets,
                selectedPresetId = "p1",
                editing = PreviewPresets[0],
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Delete dialog", showBackground = true, widthDp = 400, locale = "zh-rCN")
@Composable
private fun SimpleChatPromptDeleteDialogPreview() {
    github.ponyhuang.gimi.ui.theme.GimiTheme {
        SimpleChatPromptSettingsScreen(
            state = SimpleChatPromptUiState(
                presets = PreviewPresets,
                builtInPresets = PreviewBuiltInPresets,
                selectedPresetId = "p1",
                confirmDelete = PreviewPresets[1],
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Prompt dialog", showBackground = true, widthDp = 400, locale = "zh-rCN")
@Composable
private fun SimpleChatPromptViewDialogPreview() {
    github.ponyhuang.gimi.ui.theme.GimiTheme {
        SimpleChatPromptSettingsScreen(
            state = SimpleChatPromptUiState(
                presets = PreviewPresets,
                builtInPresets = PreviewBuiltInPresets,
                selectedPresetId = "builtin.tieba_roast",
                viewing = PreviewBuiltInPresets[0],
            ),
            onAction = {},
        )
    }
}
