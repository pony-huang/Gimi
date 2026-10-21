package github.ponyhuang.gimi.feature.appfunction

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appfunction.*
import github.ponyhuang.gimi.ui.preference.*
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** MCP 风格应用列表与独立详情页，数据和操作均由 Route 传入。 */
@Composable
fun AppFunctionScreen(
    state: AppFunctionUiState,
    packageName: String?,
    icons: Map<String, ImageBitmap>,
    onAction: (AppFunctionAction) -> Unit,
    onViewInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isRefreshing) item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.appfunction_loading))
                }
            }
            if (state.saveFailed) item {
                PreferenceBanner(stringResource(R.string.appfunction_save_failed), tone = PreferenceBannerTone.Error)
            }
            val status = state.catalog.availability
            if (status != AppFunctionAvailability.AVAILABLE) {
                item {
                    PreferenceGroupCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(when (status) {
                                AppFunctionAvailability.UNSUPPORTED -> R.string.appfunction_unsupported
                                AppFunctionAvailability.PERMISSION_REQUIRED -> R.string.appfunction_permission_required
                                else -> R.string.appfunction_load_failed
                            }))
                            TextButton(onClick = { onAction(AppFunctionAction.Refresh) }, enabled = !state.isRefreshing) {
                                Text(stringResource(R.string.appfunction_retry))
                            }
                        }
                    }
                }
            } else if (packageName == null) {
                if (state.catalog.apps.isEmpty() && !state.isRefreshing) item {
                    Text(stringResource(R.string.appfunction_empty), Modifier.padding(16.dp))
                }
                items(state.catalog.apps, key = { it.packageName }) { app ->
                    AppCard(app, icons[app.packageName], app.packageName in state.enabledPackages,
                        enabled = !state.isSaving && !state.isRefreshing,
                        onToggle = { onAction(AppFunctionAction.SetEnabled(app.packageName, it)) },
                        onViewInfo = { onViewInfo(app.packageName) })
                }
            } else {
                val app = state.catalog.apps.firstOrNull { it.packageName == packageName }
                if (app == null && !state.isRefreshing) item {
                    Text(stringResource(R.string.appfunction_missing), Modifier.padding(16.dp))
                }
                if (app != null) {
                    item {
                        PreferenceGroupCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(app.description.ifBlank { stringResource(R.string.appfunction_description_empty) })
                                Text(stringResource(R.string.appfunction_callability), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { PreferenceSectionTitle(stringResource(R.string.appfunction_count, app.functions.size)) }
                    items(app.functions, key = { it.id }) { function ->
                        FunctionCard(function, "${app.packageName}/${function.id}" in state.expandedFunctions) {
                            onAction(AppFunctionAction.ToggleFunction(app.packageName, function.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppFunctionApp, icon: ImageBitmap?, checked: Boolean, enabled: Boolean,
    onToggle: (Boolean) -> Unit, onViewInfo: () -> Unit) {
    PreferenceGroupCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.appfunction_count, app.functions.size), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled,
                    modifier = Modifier.semanticsForAppSwitch(app.name))
            }
            TextButton(onClick = onViewInfo) { Text(stringResource(R.string.appfunction_view_info)) }
        }
    }
}

@Composable
private fun FunctionCard(function: AppFunctionInfo, expanded: Boolean, onToggle: () -> Unit) {
    val toggleDescription = stringResource(if (expanded) R.string.appfunction_collapse else R.string.appfunction_expand)
    PreferenceGroupCard {
        Column {
            PreferenceListItem(icon = Icons.Default.Build, title = function.id,
                subtitle = if (expanded) null else function.description,
                onClick = onToggle, trailingContent = {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = toggleDescription)
                })
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text(stringResource(R.string.appfunction_system_state, stringResource(when (function.isEnabled) {
                        true -> R.string.appfunction_enabled
                        false -> R.string.appfunction_disabled
                        null -> R.string.appfunction_unknown
                    })), style = MaterialTheme.typography.labelMedium)
                    Text(function.description.ifBlank { stringResource(R.string.appfunction_description_empty) })
                    Text(stringResource(R.string.appfunction_parameters), style = MaterialTheme.typography.labelLarge)
                    if (function.parameters.isEmpty()) Text(stringResource(R.string.appfunction_no_parameters))
                    function.parameters.forEach { parameter ->
                        val requirement = stringResource(if (parameter.required) R.string.appfunction_required else R.string.appfunction_optional)
                        Text(stringResource(R.string.appfunction_parameter_summary, parameter.name, parameter.type, requirement), style = MaterialTheme.typography.bodyMedium)
                        if (parameter.description.isNotBlank()) Text(parameter.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.appfunction_return_type, function.returnType), style = MaterialTheme.typography.labelLarge)
                    if (function.returnDescription.isNotBlank()) Text(function.returnDescription, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 开关明确标记所属应用，避免无障碍服务只读出无上下文的开关。 */
@Composable
private fun Modifier.semanticsForAppSwitch(appName: String): Modifier {
    val description = stringResource(R.string.appfunction_enable_app, appName)
    return this.then(Modifier.semantics { contentDescription = description })
}

/** 预览固定声明，用于检查列表、展开详情和加载/权限状态。 */
private val previewApp = AppFunctionApp("com.example.notes", "示例笔记", "创建和检索笔记", listOf(
    AppFunctionInfo("Notes#createNote", "创建一条笔记", listOf(AppFunctionParameter("title", "string", true, "笔记标题")), "Note", isEnabled = true),
    AppFunctionInfo("Notes#searchNotes", "搜索笔记", returnType = "array<Note>", isEnabled = false),
))

@Preview(name = "应用列表 · 浅色", showBackground = true, widthDp = 393)
@Preview(name = "应用列表 · 深色", showBackground = true, widthDp = 393, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppFunctionListPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(catalog = AppFunctionCatalog(apps = listOf(previewApp)), isRefreshing = false), null, emptyMap(), {}, {}) }
}

@Preview(name = "功能详情 · 浅色", showBackground = true, widthDp = 393)
@Preview(name = "功能详情 · 深色", showBackground = true, widthDp = 393, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppFunctionDetailsPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(catalog = AppFunctionCatalog(apps = listOf(previewApp)), isRefreshing = false,
        expandedFunctions = setOf("${previewApp.packageName}/${previewApp.functions.first().id}")), previewApp.packageName, emptyMap(), {}, {}) }
}

@Preview(name = "需要系统授权", showBackground = true)
@Composable
private fun AppFunctionPermissionPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(catalog = AppFunctionCatalog(AppFunctionAvailability.PERMISSION_REQUIRED), isRefreshing = false), null, emptyMap(), {}, {}) }
}

@Preview(name = "正在加载", showBackground = true)
@Composable
private fun AppFunctionLoadingPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(), null, emptyMap(), {}, {}) }
}

@Preview(name = "没有应用", showBackground = true)
@Composable
private fun AppFunctionEmptyPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(isRefreshing = false), null, emptyMap(), {}, {}) }
}

@Preview(name = "设备不支持", showBackground = true)
@Composable
private fun AppFunctionUnsupportedPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(catalog = AppFunctionCatalog(AppFunctionAvailability.UNSUPPORTED), isRefreshing = false), null, emptyMap(), {}, {}) }
}

@Preview(name = "查询失败", showBackground = true)
@Composable
private fun AppFunctionFailedPreview() {
    AsssistantaiTheme { AppFunctionScreen(AppFunctionUiState(catalog = AppFunctionCatalog(AppFunctionAvailability.FAILED), isRefreshing = false), null, emptyMap(), {}, {}) }
}
