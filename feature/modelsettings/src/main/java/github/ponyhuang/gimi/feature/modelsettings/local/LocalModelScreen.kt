package github.ponyhuang.gimi.feature.modelsettings.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.*
import java.util.Locale

/** 品牌目录与版本管理共用的无状态页面，品牌跳转由 Route 回调完成。 */
@Composable
fun LocalModelScreen(
    state: LocalModelUiState,
    brandId: String?,
    onAction: (LocalModelAction) -> Unit,
    onOpenBrand: (String) -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CompactLocalModelNote(stringResource(R.string.local_model_description)) }
            if (state.mutationBlocked) item {
                PreferenceBanner(stringResource(R.string.modelsettings_agent_mutation_blocked), tone = PreferenceBannerTone.Error)
            }
            state.notice?.let { notice -> item { PreferenceBanner(stringResource(notice), tone = PreferenceBannerTone.Error) } }
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (brandId == null) {
                item { CompactLocalModelSectionTitle(stringResource(R.string.local_model_brands)) }
                items(state.models.groupBy { it.variant.brandId }.toList(), key = { it.first }) { (id, models) ->
                    PreferenceGroupCard {
                        PreferenceNavigationCard(
                            icon = Icons.Default.Memory,
                            title = stringResource(R.string.local_model_gemma4),
                            subtitle = stringResource(R.string.local_model_brand_summary, models.count { it.status == LocalModelDownloadStatus.Ready }, models.count { it.enabled }),
                            onClick = { onOpenBrand(id) },
                        )
                    }
                }
            } else {
                item { CompactLocalModelSectionTitle(stringResource(R.string.local_model_variants)) }
                item { CompactLocalModelNote(stringResource(R.string.local_model_enable_hint)) }
                val models = state.models.filter { it.variant.brandId == brandId }
                if (models.isNotEmpty()) item {
                    PreferenceGroupCard {
                        models.forEachIndexed { index, model ->
                            LocalModelVersionRow(model, state.operating, state.mutationBlocked, onAction)
                            if (index < models.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                item { CompactLocalModelNote(stringResource(R.string.local_model_backend_hint)) }
                item { TextButton(onClick = onOpenSource, modifier = Modifier.padding(horizontal = 24.dp)) { Text(stringResource(R.string.local_model_source)) } }
            }
        }
    }
    state.pendingRemoval?.let { model ->
        AlertDialog(
            onDismissRequest = { onAction(LocalModelAction.DismissRemoval) },
            title = { Text(stringResource(R.string.local_model_remove_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.local_model_remove_message, model.variant.name, modelSize(model.variant.bytes)))
                    state.notice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(enabled = !state.operating, onClick = { onAction(LocalModelAction.DismissRemoval) }) { Text(stringResource(R.string.local_model_cancel)) } },
            confirmButton = {
                TextButton(enabled = !state.operating && !state.mutationBlocked, onClick = { onAction(LocalModelAction.ConfirmRemoval) }) {
                    Text(stringResource(R.string.local_model_remove), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun CompactLocalModelSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp).padding(top = 4.dp))
}

@Composable
private fun CompactLocalModelNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp))
}

@Composable
private fun LocalModelVersionRow(model: LocalModelState, operating: Boolean, blocked: Boolean, onAction: (LocalModelAction) -> Unit) {
    val id = model.variant.id
    val ready = model.status == LocalModelDownloadStatus.Ready
    val downloading = model.status == LocalModelDownloadStatus.Downloading || model.status == LocalModelDownloadStatus.Verifying
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.variant.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.local_model_quantization_size, modelSize(model.variant.bytes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val label = stringResource(R.string.local_model_enable_description, model.variant.name)
            Checkbox(checked = model.enabled, enabled = ready && !operating && !blocked,
                onCheckedChange = { onAction(LocalModelAction.SetEnabled(id, it)) },
                modifier = Modifier.semantics { contentDescription = label })
        }
        val status = when (model.status) {
            LocalModelDownloadStatus.NotDownloaded -> stringResource(R.string.local_model_not_downloaded)
            LocalModelDownloadStatus.Downloading -> stringResource(R.string.local_model_downloading, (model.progress * 100).toInt())
            LocalModelDownloadStatus.Verifying -> stringResource(R.string.local_model_verifying)
            LocalModelDownloadStatus.Ready -> stringResource(if (model.enabled) R.string.local_model_enabled else R.string.local_model_downloaded)
            LocalModelDownloadStatus.Failed -> stringResource(when (model.failure) {
                LocalModelFailure.Storage -> R.string.local_model_storage_error
                LocalModelFailure.Integrity -> R.string.local_model_integrity_error
                LocalModelFailure.AccessDenied -> R.string.local_model_access_error
                else -> R.string.local_model_network_error
            })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (model.status == LocalModelDownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            TextButton(enabled = !operating && (!ready || !blocked), contentPadding = PaddingValues(horizontal = 12.dp), onClick = {
                onAction(when {
                    ready -> LocalModelAction.RequestRemoval(id)
                    downloading -> LocalModelAction.CancelDownload(id)
                    else -> LocalModelAction.Download(id)
                })
            }) { Text(stringResource(when { ready -> R.string.local_model_remove; downloading -> R.string.local_model_cancel_download; else -> R.string.local_model_download })) }
        }
        if (downloading) LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
    }
}

/** 使用 GB 十进制大小，与下载来源提供的文件长度保持一致。 */
@Composable
private fun modelSize(bytes: Long): String = stringResource(R.string.local_model_size_gb, String.format(Locale.getDefault(), "%.2f", bytes / 1_000_000_000.0))
