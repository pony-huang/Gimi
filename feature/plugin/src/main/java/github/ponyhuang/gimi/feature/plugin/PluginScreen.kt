package github.ponyhuang.gimi.feature.plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun PluginSettingsRoute(
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
    PluginSettingsScreen(
        state = state,
        icons = icons,
        onAction = viewModel::onAction,
        onNavigateToConfig = onNavigateToConfig,
        modifier = modifier,
    )
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
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        icon = icons[plugin.id],
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

@Composable
private fun PluginRow(
    plugin: PluginDescriptor,
    icon: ImageBitmap?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
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
                .padding(start = 28.dp, end = 12.dp),
        ) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = plugin.isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

/** 从插件包读取应用图标；失败（无图标/不可见）返回 null，交由调用方回退默认图标。 */
private fun loadPluginIcon(context: Context, packageName: String): Drawable? = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationIcon(info)
}.getOrNull()

/** 把 Android Drawable 转成 Bitmap，供 Compose [Image] 渲染。 */
private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}

@Composable
fun PluginConfigRoute(
    pluginId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(pluginId) { viewModel.load(pluginId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginConfigEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message ?: effect.messageRes?.let(context::getString).orEmpty(),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    PluginConfigScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PluginConfigScreen(
    state: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val browser = state.browser
    PreferenceScaffold(
        title = stringResource(
            if (browser == null) R.string.plugin_config_title else R.string.plugin_browser_title,
        ),
        onBack = if (browser == null) {
            onBack
        } else {
            { onAction(PluginConfigAction.CloseBrowser) }
        },
    ) { contentModifier ->
        if (browser != null) {
            PluginBrowserScreen(
                browser = browser,
                onComplete = { url ->
                    onAction(PluginConfigAction.CompleteAction(browser.actionId, url))
                },
                modifier = contentModifier,
            )
            return@PreferenceScaffold
        }
        PluginConfigContent(
            state = state,
            onAction = onAction,
            modifier = contentModifier.then(modifier),
        )
    }
}

@Composable
private fun PluginConfigContent(
    state: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.hasFields && !state.hasActions) {
                PreferenceBanner(
                    text = stringResource(R.string.plugin_config_no_fields),
                    tone = PreferenceBannerTone.Info,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.fields, key = { it.key }) { field ->
                    when (field.kind) {
                        PluginConfigFieldDescriptor.Kind.TEXT -> PluginTextField(
                            field = field,
                            onValueChange = { onAction(PluginConfigAction.SetValue(field.key, it)) },
                        )
                        PluginConfigFieldDescriptor.Kind.TOGGLE -> PluginToggleField(
                            field = field,
                            onCheckedChange = { onAction(PluginConfigAction.SetValue(field.key, it.toString())) },
                        )
                        PluginConfigFieldDescriptor.Kind.SELECT -> PluginSelectField(
                            field = field,
                            onSelect = { onAction(PluginConfigAction.SetValue(field.key, it)) },
                        )
                    }
                }
                state.actions.forEach { action ->
                    item(key = "action-${action.id}") {
                        Button(
                            onClick = { onAction(PluginConfigAction.RunAction(action.id)) },
                            enabled = !action.running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (action.running) {
                                    stringResource(R.string.plugin_action_running)
                                } else {
                                    action.label
                                },
                            )
                        }
                    }
                }
            }
            if (state.hasFields) {
                Button(
                    onClick = { onAction(PluginConfigAction.Save) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.plugin_config_save))
                }
            }
        }
    }
}

@Composable
private fun PluginTextField(
    field: PluginConfigFieldUiState,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = field.value,
        onValueChange = onValueChange,
        label = { Text(field.label) },
        singleLine = true,
        visualTransformation = if (field.secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun PluginToggleField(
    field: PluginConfigFieldUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceListItem(
        icon = Icons.Default.Tune,
        title = field.label,
        trailingContent = {
            Switch(
                checked = field.value.toBoolean(),
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginSelectField(
    field: PluginConfigFieldUiState,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            field.options.forEach { option ->
                FilterChip(
                    selected = field.value == option,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
