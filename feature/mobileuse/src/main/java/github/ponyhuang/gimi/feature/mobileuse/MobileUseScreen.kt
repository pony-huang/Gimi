package github.ponyhuang.gimi.feature.mobileuse

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchItem
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无状态 Shizuku 设置页；开关、授权状态与展开态都来自 UiState，本层不读取服务或发起副屏操作。 */
@Composable
fun MobileUseScreen(
    state: MobileUseUiState,
    onEnabledChange: (Boolean) -> Unit,
    onAuthorize: () -> Unit,
    onRefresh: () -> Unit,
    onOpenShizuku: () -> Unit,
    onTextInputAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.mobile_use_switch_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = Icons.Default.Phonelink,
                        title = stringResource(R.string.mobile_use_switch_title),
                        subtitle = stringResource(R.string.mobile_use_intro),
                        checked = state.enabled,
                        switchEnabled = !state.updating,
                        onCheckedChange = onEnabledChange,
                    )
                }
            }
            item {
                // 开启后自动展开：授权、输入与操作入口只在功能打开时出现，关闭时整段收回。
                MobileUseExpansion(visible = state.enabled) {
                    Column {
                        PreferenceSectionTitle(stringResource(R.string.mobile_use_section_status))
                        PreferenceGroupCard {
                            state.availability.statusTitleRes()?.let { titleRes ->
                                PreferenceListItem(
                                    icon = Icons.Default.Phonelink,
                                    title = stringResource(titleRes),
                                    subtitle = state.availability.statusDetailRes()?.let { stringResource(it) },
                                    showDivider = true,
                                )
                            }
                            PreferenceListItem(
                                icon = Icons.Default.Keyboard,
                                title = stringResource(
                                    if (state.textInputAvailable) {
                                        R.string.mobile_use_text_input_ready_title
                                    } else {
                                        R.string.mobile_use_text_input_required_title
                                    },
                                ),
                                subtitle = if (state.textInputAvailable) {
                                    null
                                } else {
                                    stringResource(R.string.mobile_use_text_input_required)
                                },
                            )
                        }
                        PreferenceSectionTitle(stringResource(R.string.mobile_use_section_actions))
                        PreferenceGroupCard {
                            val actions = listOfNotNull(
                                if (state.availability == MobileUseAvailability.PERMISSION_REQUIRED) {
                                    MobileUseActionRow(
                                        icon = Icons.Default.Check,
                                        title = stringResource(R.string.mobile_use_authorize),
                                        onClick = onAuthorize,
                                    )
                                } else {
                                    null
                                },
                                if (state.textInputAvailable) {
                                    null
                                } else {
                                    MobileUseActionRow(
                                        icon = Icons.Default.Settings,
                                        title = stringResource(R.string.mobile_use_text_input_settings),
                                        onClick = onTextInputAction,
                                    )
                                },
                                MobileUseActionRow(
                                    icon = Icons.Default.Refresh,
                                    title = stringResource(R.string.mobile_use_refresh),
                                    onClick = onRefresh,
                                ),
                                MobileUseActionRow(
                                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                                    title = stringResource(R.string.mobile_use_open_shizuku),
                                    onClick = onOpenShizuku,
                                ),
                            )
                            actions.forEachIndexed { index, action ->
                                PreferenceListItem(
                                    icon = action.icon,
                                    title = action.title,
                                    onClick = action.onClick,
                                    showDivider = index < actions.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 操作区中的一行入口；只承载展示所需图标、文案与点击意图。 */
private data class MobileUseActionRow(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit,
)

/** 与沙盒配置页一致的展开动画：从顶部展开，高度与透明度同步过渡。 */
@Composable
private fun MobileUseExpansion(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
    ) { content() }
}

/** 关闭态不渲染状态区，因此这里对 DISABLED 返回 null。 */
private fun MobileUseAvailability.statusTitleRes(): Int? = when (this) {
    MobileUseAvailability.DISABLED -> null
    MobileUseAvailability.SHIZUKU_MISSING -> R.string.mobile_use_missing_title
    MobileUseAvailability.SHIZUKU_STOPPED -> R.string.mobile_use_stopped_title
    MobileUseAvailability.PERMISSION_REQUIRED -> R.string.mobile_use_permission_title
    MobileUseAvailability.PERMISSION_DENIED -> R.string.mobile_use_denied_title
    MobileUseAvailability.ROOT_UNSUPPORTED -> R.string.mobile_use_root_title
    MobileUseAvailability.READY -> R.string.mobile_use_ready_title
    MobileUseAvailability.BUSY -> R.string.mobile_use_busy_title
    MobileUseAvailability.UNSUPPORTED -> R.string.mobile_use_unsupported_title
}

private fun MobileUseAvailability.statusDetailRes(): Int? = when (this) {
    MobileUseAvailability.DISABLED -> null
    MobileUseAvailability.SHIZUKU_MISSING -> R.string.mobile_use_missing_detail
    MobileUseAvailability.SHIZUKU_STOPPED -> R.string.mobile_use_stopped_detail
    MobileUseAvailability.PERMISSION_REQUIRED -> R.string.mobile_use_permission_detail
    MobileUseAvailability.PERMISSION_DENIED -> R.string.mobile_use_denied_detail
    MobileUseAvailability.ROOT_UNSUPPORTED -> R.string.mobile_use_root_detail
    MobileUseAvailability.READY -> null
    MobileUseAvailability.BUSY -> R.string.mobile_use_busy_detail
    MobileUseAvailability.UNSUPPORTED -> R.string.mobile_use_unsupported_detail
}

@Preview(name = "开关关闭", widthDp = 390, heightDp = 900)
@Preview(name = "开关关闭 · 深色", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MobileUseOffPreview() = MobileUsePreview(MobileUseUiState())

@Preview(name = "开启待授权", widthDp = 390, heightDp = 900)
@Composable
private fun MobileUsePermissionPreview() = MobileUsePreview(
    MobileUseUiState(MobileUseAvailability.PERMISSION_REQUIRED, textInputAvailable = false, enabled = true),
)

@Preview(name = "开启已就绪 · 深色", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MobileUseReadyDarkPreview() = MobileUsePreview(
    MobileUseUiState(MobileUseAvailability.READY, textInputAvailable = true, enabled = true),
)

/** 预览外壳：只补齐页面骨架与返回栏，其余状态由 fixture 提供。 */
@Composable
private fun MobileUsePreview(state: MobileUseUiState) {
    AsssistantaiTheme {
        PreferenceScaffold(
            style = PreferenceStyle.Compact,
            title = stringResource(R.string.mobile_use_title),
            onBack = {},
        ) { modifier ->
            MobileUseScreen(
                state = state,
                onEnabledChange = {},
                onAuthorize = {},
                onRefresh = {},
                onOpenShizuku = {},
                onTextInputAction = {},
                modifier = modifier,
            )
        }
    }
}
