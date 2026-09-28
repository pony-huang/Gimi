package github.ponyhuang.gimi.feature.mobileuse

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

/** 持有导航和外部 Shizuku 应用打开动作的页面入口。 */
@Composable
fun MobileUseRoute(
    onBack: () -> Unit,
    viewModel: MobileUseViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.availability.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    PreferenceScaffold(title = stringResource(R.string.mobile_use_title), onBack = onBack) { modifier ->
        MobileUseScreen(
            availability = status,
            onPrimaryAction = {
                if (status == MobileUseAvailability.PERMISSION_REQUIRED) {
                    viewModel.requestPermission()
                } else {
                    viewModel.refresh()
                }
            },
            onOpenShizuku = {
                val intent = context.packageManager
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.refresh()
                    }
                }
            },
            modifier = modifier,
        )
    }
}
