package github.ponyhuang.gimi.feature.skills

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.feature.skills.R
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.GimiTheme

@Composable
fun SkillsSettingsScreen(
    state: SkillsSettingsUiState,
    onAction: (SkillsSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.skills_installed_section)) }
            // 安装入口收在标题栏的"+"菜单里；导入期间在此显示进度。
            if (state.isImporting) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.skills_importing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
            if (state.skills.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.skills_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            } else {
                item {
                    // 技能数量有限，整组渲染进同一张卡片，行间使用内缩分隔线。
                    PreferenceGroupCard {
                        state.skills.forEachIndexed { index, skill ->
                            val enableDescription = stringResource(R.string.skills_enable, skill.name)
                            PreferenceListItem(
                                icon = Icons.Default.Extension,
                                title = skill.name,
                                subtitle = skill.description,
                                showDivider = index < state.skills.lastIndex,
                                trailingContent = {
                                    // 尾部顺序与插件/MCP 列表一致：溢出菜单在前，开关贴行尾。
                                    SkillRowMenu(
                                        name = skill.name,
                                        onRemove = { onAction(SkillsSettingsAction.RequestRemove(skill)) },
                                    )
                                    // 菜单与开关都是 48dp 触控盒，留一点间距避免两者紧贴糊在一起。
                                    Spacer(Modifier.width(4.dp))
                                    GimiSwitch(
                                        checked = skill.enabled,
                                        onCheckedChange = { enabled ->
                                            onAction(SkillsSettingsAction.ToggleSkill(skill, enabled))
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = enableDescription
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isUrlDialogVisible) {
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.DismissUrlDialog) },
            title = { Text(stringResource(R.string.skills_url_dialog_title)) },
            text = {
                PreferenceTextField(
                    value = state.urlDraft,
                    onValueChange = { onAction(SkillsSettingsAction.UrlChanged(it)) },
                    label = { Text(stringResource(R.string.skills_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.urlDraft.isNotBlank() && !state.isImporting,
                    onClick = { onAction(SkillsSettingsAction.SubmitUrl) },
                ) {
                    Text(stringResource(R.string.skills_install))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.DismissUrlDialog) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }

    state.pendingReplacement?.let { pending ->
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.CancelReplacement) },
            title = { Text(stringResource(R.string.skills_replace_title)) },
            text = {
                Text(stringResource(R.string.skills_replace_message, pending.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.ConfirmReplacement) },
                ) {
                    Text(stringResource(R.string.skills_replace_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.CancelReplacement) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }

    state.pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.CancelRemoval) },
            title = { Text(stringResource(R.string.skills_delete_title)) },
            text = {
                Text(stringResource(R.string.skills_delete_message, pending.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.ConfirmRemoval) },
                ) {
                    Text(stringResource(R.string.skills_delete_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.CancelRemoval) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }
}

/** 技能行溢出菜单：对齐插件/MCP 列表，删除等低频动作收进菜单并以错误色提示。 */
@Composable
private fun SkillRowMenu(
    name: String,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.skills_row_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.skills_remove, name),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkillsSettingsScreenEmptyPreview() {
    GimiTheme {
        SkillsSettingsScreen(
            state = SkillsSettingsUiState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkillsSettingsScreenWithSkillsPreview() {
    GimiTheme {
        SkillsSettingsScreen(
            state = SkillsSettingsUiState(
                skills = listOf(
                    InstalledSkill(
                        name = "网页摘要",
                        description = "抓取指定网页内容并生成中文要点摘要，支持长文分段。",
                    ),
                    InstalledSkill(
                        name = "代码评审",
                        description = "按团队规范评审 diff，输出问题清单与修改建议。检查架构边界、状态流转、异常处理与测试覆盖，并给出可以直接执行的修改意见。",
                        enabled = false,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
