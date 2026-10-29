package github.ponyhuang.gimi.feature.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** 导出专用路由副作用：按归档类型创建文档，取消选择不产生空归档或失败提示。 */
@Composable
internal fun SessionExportEffects(viewModel: SessionExportViewModel) {
    val context = LocalContext.current
    val jsonlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson"),
    ) { viewModel.destinationSelected(it?.toString()) }
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { viewModel.destinationSelected(it?.toString()) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionExportEffect.ChooseDestination -> try {
                    if (effect.request.mimeType == "application/zip") {
                        zipLauncher.launch(effect.request.fileName)
                    } else {
                        jsonlLauncher.launch(effect.request.fileName)
                    }
                } catch (_: ActivityNotFoundException) {
                    viewModel.destinationUnavailable()
                }
                is SessionExportEffect.ShowNotice -> Toast.makeText(
                    context,
                    when (effect.notice) {
                        SessionExportNotice.Succeeded -> R.string.session_export_succeeded
                        SessionExportNotice.Failed -> R.string.session_export_failed
                        SessionExportNotice.Blocked -> R.string.session_export_blocked
                        SessionExportNotice.Empty -> R.string.session_export_empty
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
