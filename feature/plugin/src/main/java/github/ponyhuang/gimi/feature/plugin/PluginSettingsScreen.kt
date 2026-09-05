package github.ponyhuang.gimi.feature.plugin

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

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
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginSettingsEffect.ShowPluginAdded -> Toast.makeText(
                    context,
                    context.getString(R.string.plugin_added_notice, effect.pluginIds.joinToString()),
                    Toast.LENGTH_SHORT,
                ).show()
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
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginDescriptor,
    icon: ImageBitmap?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
