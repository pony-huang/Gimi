package github.ponyhuang.gimi.feature.modelsettings.local

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import java.util.Locale

/** 扁平的本地模型目录；下载状态和操作进入 ViewModel，展开状态仅控制信息展示。 */
@Composable
fun LocalModelScreen(
    state: LocalModelUiState,
    onAction: (LocalModelAction) -> Unit,
    onOpenLicense: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.local_model_count, state.models.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            if (state.mutationBlocked) item {
                PreferenceBanner(stringResource(R.string.modelsettings_agent_mutation_blocked), tone = PreferenceBannerTone.Error)
            }
            state.notice?.let { notice -> item { PreferenceBanner(stringResource(notice), tone = PreferenceBannerTone.Error) } }
            if (state.loading) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                items(state.models, key = { it.variant.id }) { model ->
                    LocalModelCard(model, state.operating, state.mutationBlocked, model.variant.id in state.expandedModelIds, onAction, onOpenLicense)
                }
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
private fun LocalModelCard(
    model: LocalModelState,
    operating: Boolean,
    blocked: Boolean,
    expanded: Boolean,
    onAction: (LocalModelAction) -> Unit,
    onOpenLicense: (String) -> Unit,
) {
    val id = model.variant.id
    val ready = model.status == LocalModelDownloadStatus.Ready
    val downloading = model.status == LocalModelDownloadStatus.Downloading
    val verifying = model.status == LocalModelDownloadStatus.Verifying
    val progress = model.progress.coerceIn(0f, 1f)
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.variant.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction(LocalModelAction.ToggleDetails(id)) }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.local_model_collapse else R.string.local_model_expand))
                }
            }
            Text(stringResource(R.string.local_model_quantization_size, modelSize(model.variant.bytes)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onOpenLicense(model.variant.modelPageUrl) },
                enabled = model.variant.modelPageUrl.isNotBlank(), contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.local_model_source))
            }
            AnimatedVisibility(visible = expanded) {
                Text(stringResource(R.string.local_model_backend_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            val status = when (model.status) {
                LocalModelDownloadStatus.NotDownloaded -> stringResource(R.string.local_model_not_downloaded)
                LocalModelDownloadStatus.Downloading -> stringResource(R.string.local_model_download_bytes,
                    modelSize((model.variant.bytes * progress).toLong()), modelSize(model.variant.bytes))
                LocalModelDownloadStatus.Verifying -> stringResource(R.string.local_model_verifying)
                LocalModelDownloadStatus.Ready -> stringResource(R.string.local_model_downloaded)
                LocalModelDownloadStatus.Failed -> stringResource(when (model.failure) {
                    LocalModelFailure.Storage -> R.string.local_model_storage_error
                    LocalModelFailure.Integrity -> R.string.local_model_integrity_error
                    LocalModelFailure.AccessDenied -> R.string.local_model_access_error
                    else -> R.string.local_model_network_error
                })
            }
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = if (model.status == LocalModelDownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            when {
                downloading || verifying -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (verifying) LinearProgressIndicator(Modifier.weight(1f))
                        else {
                            Text(stringResource(R.string.local_model_progress, (progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                        }
                        IconButton(enabled = !operating, onClick = { onAction(LocalModelAction.CancelDownload(id)) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.local_model_cancel_download))
                        }
                    }
                }
                ready -> TextButton(enabled = !operating && !blocked,
                    onClick = { onAction(LocalModelAction.RequestRemoval(id)) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.local_model_remove), color = MaterialTheme.colorScheme.error)
                }
                else -> FilledTonalButton(enabled = !operating, onClick = { onAction(LocalModelAction.Download(id)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (model.status == LocalModelDownloadStatus.Failed) R.string.local_model_retry_download else R.string.local_model_download))
                }
            }
        }
    }
}

/** 使用 GB 十进制大小，与下载来源提供的文件长度保持一致。 */
@Composable
private fun modelSize(bytes: Long): String = stringResource(R.string.local_model_size_gb, String.format(Locale.getDefault(), "%.2f", bytes / 1_000_000_000.0))
