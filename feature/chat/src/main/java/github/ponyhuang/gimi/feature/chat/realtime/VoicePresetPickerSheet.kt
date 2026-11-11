package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.components.GimiSheetTitleRow
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 当前选中的预设对象；先官方后自建查找，未选中或 id 悬空时为 null。 */
internal fun VoiceSettingsUiState.selectedPromptPreset(): SimpleChatPromptPreset? =
    (builtInPromptPresets + userPromptPresets).firstOrNull { it.id == selectedPromptPresetId }

/**
 * 抽屉二级页「提示词预设」：不使用预设、官方预设与自建预设同卡单选。
 * 这里只提供选择，自建预设的新增/编辑/删除入口在个性化服务页的预设管理页；
 * 单选圆钮不响应点击，统一走行点击。通话进行中或前台 Agent 忙时只读（[VoiceSettingsUiState.writable]）。
 */
@Composable
internal fun VoicePresetPickerPage(
    settings: VoiceSettingsUiState,
    onSelectPreset: (String?) -> Unit,
) {
    val selected = settings.selectedPromptPreset()
    val writable = settings.writable
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            PreferenceListItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.voice_prompt_preset_none),
                subtitle = stringResource(R.string.voice_prompt_preset_none_detail),
                showDivider = settings.builtInPromptPresets.isNotEmpty() || settings.userPromptPresets.isNotEmpty(),
                onClick = if (writable) ({ onSelectPreset(null) }) else null,
                trailingContent = {
                    RadioButton(selected = selected == null, onClick = null)
                },
            )
            settings.builtInPromptPresets.forEachIndexed { index, preset ->
                PreferenceListItem(
                    icon = Icons.Default.AutoAwesome,
                    title = preset.name,
                    subtitle = preset.description.ifBlank { preset.content },
                    showDivider = index != settings.builtInPromptPresets.lastIndex || settings.userPromptPresets.isNotEmpty(),
                    onClick = if (writable) ({ onSelectPreset(preset.id) }) else null,
                    trailingContent = {
                        RadioButton(selected = selected?.id == preset.id, onClick = null)
                    },
                )
            }
            settings.userPromptPresets.forEachIndexed { index, preset ->
                PreferenceListItem(
                    icon = Icons.Default.EditNote,
                    title = preset.name,
                    subtitle = preset.description.ifBlank { preset.content },
                    showDivider = index != settings.userPromptPresets.lastIndex,
                    onClick = if (writable) ({ onSelectPreset(preset.id) }) else null,
                    trailingContent = {
                        RadioButton(selected = selected?.id == preset.id, onClick = null)
                    },
                )
            }
        }
        // 提示与分组卡片内文字对齐（卡片外边距 16dp + 行内缩进 16dp），与语音设置页的提示同位。
        Text(
            stringResource(R.string.voice_settings_effect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
    }
}

// ── 抽屉预设选择页预览 ───────────────────────────────────────────

/** 预览共用的官方预设假数据，与真实目录同 id 语义但正文截断。 */
private val previewBuiltInPresets = listOf(
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
        content = "# Role and Objective\n\n你是「情感陪伴师」……",
    ),
)

/** 预览共用的自建预设假数据。 */
private val previewUserPresets = listOf(
    SimpleChatPromptPreset(id = "p1", name = "简洁速答", content = "直接给结论，单句回答，不超过两句话。"),
)

/** 抽屉预设选择页预览共用外壳：灰画布 + 紧凑行样式 + 带返回键的标题行。 */
@Composable
private fun VoicePresetPickerPagePreview(content: @Composable () -> Unit) {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column(Modifier.fillMaxWidth()) {
                    GimiSheetTitleRow(
                        title = stringResource(R.string.voice_prompt_settings_section),
                        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                        navigationContentDescription = stringResource(R.string.voice_sheet_back),
                        onNavigationClick = { },
                    )
                    content()
                }
            }
        }
    }
}

@Preview(name = "抽屉 · 提示词预设 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 提示词预设 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoicePresetPickerPageLightPreview() {
    VoicePresetPickerPagePreview {
        VoicePresetPickerPage(
            settings = VoiceSettingsUiState(
                builtInPromptPresets = previewBuiltInPresets,
                userPromptPresets = previewUserPresets,
                selectedPromptPresetId = "builtin.emotional_companion",
                writable = true,
            ),
            onSelectPreset = {},
        )
    }
}

/** 通话中只读：各行禁点，选中标记保持展示。 */
@Preview(name = "抽屉 · 提示词预设 · 只读 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 提示词预设 · 只读 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoicePresetPickerPageReadOnlyPreview() {
    VoicePresetPickerPagePreview {
        VoicePresetPickerPage(
            settings = VoiceSettingsUiState(
                builtInPromptPresets = previewBuiltInPresets,
                userPromptPresets = previewUserPresets,
                selectedPromptPresetId = null,
                writable = false,
            ),
            onSelectPreset = {},
        )
    }
}
