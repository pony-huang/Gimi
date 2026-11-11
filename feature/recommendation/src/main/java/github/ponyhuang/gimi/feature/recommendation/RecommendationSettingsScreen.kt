package github.ponyhuang.gimi.feature.recommendation

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Rule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchItem
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 「个性化选项」页：按用户实际使用频率排列分组。
 *
 * 顺序为「推荐 → 语音 → 对话界面 → 工具 → 设备控制 → 主题」：
 * 推荐更新是应用的主动能力，失败需要立刻被看到，因此置顶并独立成页；
 * 语音是一等交互方式、简单聊天预设会被反复调整，排在第二；
 * 对话界面与工具影响每轮对话的呈现与上下文开销，属于常调但非日常项；
 * 设备控制（Shizuku）用于一次性接入；主题模式统一放在页面底部。
 */
@Composable
fun RecommendationSettingsScreen(
    state: RecommendationSettingsUiState,
    onAction: (RecommendationSettingsAction) -> Unit,
    onOpenSimpleChatPromptSettings: () -> Unit = {},
    onOpenMobileUse: () -> Unit = {},
    onOpenRecommendationUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val a2uiLogo = ImageVector.vectorResource(R.drawable.ic_a2ui_logo)
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // 推荐更新独立成组：开关、更新间隔与手动刷新都收进专属页面，失败提示也随之上移。
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_update_nav_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.recommendation_settings_section),
                        subtitle = stringResource(R.string.recommendation_update_nav_subtitle),
                        onClick = onOpenRecommendationUpdate,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_voice_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.recommendation_simple_chat_title),
                        subtitle = stringResource(R.string.recommendation_simple_chat_subtitle),
                        onClick = onOpenSimpleChatPromptSettings,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_conversation_section)) }
            item { PreferenceSectionNote(stringResource(R.string.recommendation_a2ui_note)) }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = a2uiLogo,
                        title = stringResource(R.string.recommendation_a2ui_title),
                        subtitle = stringResource(R.string.recommendation_a2ui_subtitle),
                        checked = state.a2uiEnabled,
                        onCheckedChange = { checked ->
                            onAction(RecommendationSettingsAction.SetA2uiEnabled(checked))
                        },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_tools_section)) }
            item { PreferenceSectionNote(stringResource(R.string.recommendation_tool_loading_note)) }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = Icons.Default.Rule,
                        title = stringResource(R.string.recommendation_tool_loading_title),
                        subtitle = stringResource(
                            if (state.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
                                R.string.recommendation_tool_loading_always
                            } else {
                                R.string.recommendation_tool_loading_on_demand
                            },
                        ),
                        checked = state.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE,
                        onCheckedChange = { enabled ->
                            onAction(
                                RecommendationSettingsAction.SetToolAccessMode(
                                    if (enabled) ToolAccessMode.ALWAYS_AVAILABLE else ToolAccessMode.ON_DEMAND,
                                ),
                            )
                        },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_device_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Phonelink,
                        title = stringResource(R.string.recommendation_mobile_use_title),
                        subtitle = stringResource(R.string.recommendation_mobile_use_subtitle),
                        onClick = onOpenMobileUse,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_theme_section)) }
            item {
                PreferenceGroupCard {
                    ThemeModeSegmentedGroup(
                        themeMode = state.themeMode,
                        onThemeModeChange = { onAction(RecommendationSettingsAction.SetThemeMode(it)) },
                    )
                }
            }
        }
    }
}

/** 带页面外壳的固定状态预览，便于检查分组间距与系统安全区域。 */
@Composable
private fun RecommendationSettingsPagePreview(
    dark: Boolean,
    toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    a2uiEnabled: Boolean = true,
) {
    GimiTheme(darkTheme = dark) {
        PreferenceScaffold(
            title = stringResource(R.string.recommendation_settings_title),
            onBack = {},
        ) { modifier ->
            RecommendationSettingsScreen(
                state = RecommendationSettingsUiState(
                    enabled = true,
                    toolAccessMode = toolAccessMode,
                    intervalHours = 2,
                    a2uiEnabled = a2uiEnabled,
                ),
                onAction = {},
                onOpenRecommendationUpdate = {},
                modifier = modifier,
            )
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsPageLightPreview() {
    RecommendationSettingsPagePreview(false)
}

@Preview(name = "Dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsPageDarkPreview() {
    RecommendationSettingsPagePreview(true)
}

@Preview(name = "按需加载工具", device = Devices.PHONE, showBackground = true)
@Composable
private fun RecommendationSettingsPageOnDemandLightPreview() {
    RecommendationSettingsPagePreview(false, toolAccessMode = ToolAccessMode.ON_DEMAND)
}

@Preview(name = "关闭 A2UI 卡片", device = Devices.PHONE, showBackground = true)
@Composable
private fun RecommendationSettingsPageA2uiDisabledLightPreview() {
    RecommendationSettingsPagePreview(false, a2uiEnabled = false)
}
