package github.ponyhuang.gimi.feature.plugin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun PluginSettingsRoute(
    onBack: () -> Unit,
    onNavigateToConfig: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 进入页面自动重新发现插件，装完新插件 APK 后无需重启即可出现。
    LaunchedEffect(Unit) { viewModel.onAction(PluginSettingsAction.Refresh) }
    // 卸载走系统卸载页；无论结果如何返回后都重新发现插件，保证列表与设备状态一致。
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, R.string.plugin_uninstalled_notice, Toast.LENGTH_SHORT).show()
        }
        viewModel.onAction(PluginSettingsAction.Refresh)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginSettingsEffect.ShowPluginAdded -> Toast.makeText(
                    context,
                    context.getString(R.string.plugin_added_notice, effect.pluginIds.joinToString()),
                    Toast.LENGTH_SHORT,
                ).show()
                is PluginSettingsEffect.RequestSystemUninstall -> uninstallLauncher.launch(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${effect.packageName}")),
                )
            }
        }
    }
    // 读取每个插件 APK 的应用图标；插件自带图标，宿主无需硬编码品牌资源。
    val icons = remember(state.plugins) {
        state.plugins.associate { plugin ->
            plugin.id to loadPluginIcon(context, plugin.packageName)?.toBitmap()?.asImageBitmap()
        }
    }
    PreferenceScaffold(
        title = stringResource(R.string.plugin_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        PluginSettingsScreen(
            state = state,
            icons = icons,
            onAction = viewModel::onAction,
            onNavigateToConfig = onNavigateToConfig,
            modifier = scaffoldModifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsScreen(
    state: PluginSettingsUiState,
    icons: Map<String, ImageBitmap?>,
    onAction: (PluginSettingsAction) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        if (state.plugins.isEmpty()) {
            PreferenceBanner(
                text = stringResource(R.string.plugin_empty),
                tone = PreferenceBannerTone.Info,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        // 下拉刷新替代列表顶部的按钮刷新；isRefreshing 由 ViewModel 在 refresh() 期间置位。
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(PluginSettingsAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    // 插件数量有限，整组渲染进同一张 One UI 卡片。
                    PreferenceGroupCard {
                        state.plugins.forEachIndexed { index, plugin ->
                            PluginRow(
                                plugin = plugin,
                                icon = icons[plugin.id],
                                showDivider = index < state.plugins.lastIndex,
                                onToggle = { enabled ->
                                    onAction(PluginSettingsAction.SetEnabled(plugin.id, enabled))
                                },
                                onClick = { onNavigateToConfig(plugin.id) },
                                onUninstallClick = {
                                    onAction(PluginSettingsAction.RequestUninstall(plugin.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    // 卸载确认框浮在整个列表上层；pendingUninstallPluginId 由 ViewModel 管理，旋转重建不丢失。
    state.pendingUninstallPluginId
        ?.let { id -> state.plugins.firstOrNull { it.id == id } }
        ?.let { plugin ->
            UninstallConfirmDialog(
                plugin = plugin,
                onConfirm = { onAction(PluginSettingsAction.ConfirmUninstall(plugin.id)) },
                onDismiss = { onAction(PluginSettingsAction.DismissUninstall) },
            )
        }
}

@Composable
private fun PluginRow(
    plugin: PluginDescriptor,
    icon: ImageBitmap?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onUninstallClick: () -> Unit,
    showDivider: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 统一「应用图标」样式：白底圆角方片 + 居中 logo，宽版 wordmark（如知乎）等比缩放不拉伸。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.66f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.plugin_row_tools_summary, plugin.version, plugin.toolCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.plugin_row_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.plugin_uninstall_menu),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onUninstallClick()
                        },
                    )
                }
            }
            Switch(
                checked = plugin.isEnabled,
                onCheckedChange = onToggle,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun UninstallConfirmDialog(
    plugin: PluginDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plugin_uninstall_title, plugin.name)) },
        text = {
            Text(text = stringResource(R.string.plugin_uninstall_message, plugin.toolCount))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.plugin_uninstall_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.plugin_uninstall_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PluginSettingsScreenEmptyPreview() {
    AsssistantaiTheme {
        PluginSettingsScreen(
            state = PluginSettingsUiState(),
            icons = emptyMap(),
            onAction = {},
            onNavigateToConfig = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PluginSettingsScreenWithPluginsPreview() {
    AsssistantaiTheme {
        PluginSettingsScreen(
            state = PluginSettingsUiState(
                plugins = listOf(
                    PluginDescriptor(
                        id = "zhihu",
                        name = "知乎",
                        packageName = "github.ponyhuang.gimi.plugin.zhihu",
                        version = 1,
                        toolCount = 3,
                        isEnabled = true,
                    ),
                    PluginDescriptor(
                        id = "spotify",
                        name = "Spotify",
                        packageName = "github.ponyhuang.gimi.plugin.spotify",
                        version = 2,
                        toolCount = 5,
                        isEnabled = false,
                    ),
                ),
            ),
            icons = emptyMap(),
            onAction = {},
            onNavigateToConfig = {},
        )
    }
}
