package github.ponyhuang.gimi.feature.modelsettings.defaults

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

// effect 携带动态 string res id，无法在组合期用 stringResource 解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DefaultModelSettingsRoute(
    onBack: () -> Unit,
    /** 进入即打开某用途的选择器；用于通话页「去配置」与用途选择器补配返回。 */
    openRole: DefaultModelRole? = null,
    /** 从选择器的空态进入添加服务；保存后宿主回到本页并再次打开该用途选择器。 */
    onAddService: (DefaultModelRole) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(openRole) {
        when (openRole) {
            DefaultModelRole.Realtime -> viewModel.showDialog(DefaultModelDialog.RealtimeModel)
            DefaultModelRole.Assistant -> viewModel.showDialog(DefaultModelDialog.Assistant)
            DefaultModelRole.Fast -> viewModel.showDialog(DefaultModelDialog.Fast)
            DefaultModelRole.SpeechRecognition -> viewModel.showDialog(DefaultModelDialog.Speech)
            DefaultModelRole.SpeechSynthesis -> viewModel.showDialog(DefaultModelDialog.Tts)
            null -> Unit
        }
    }

    val noticeMessage = state.notice?.let { notice ->
        when (notice) {
            is DefaultModelSettingsNotice.ShowToast -> stringResource(notice.messageRes)
        }
    }
    LaunchedEffect(state.notice, noticeMessage) {
        val message = noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(DefaultModelSettingsAction.ConsumeNotice)
    }

    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_defaults_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        DefaultModelSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onAddService = { dialog ->
                dialog.toRole()?.let(onAddService)
            },
            modifier = scaffoldModifier,
        )
    }
}

/** 用途选择器到默认角色的映射；对话方式、链路与音色选择器没有补配流程。 */
private fun DefaultModelDialog.toRole(): DefaultModelRole? = when (this) {
    DefaultModelDialog.Assistant -> DefaultModelRole.Assistant
    DefaultModelDialog.Fast -> DefaultModelRole.Fast
    DefaultModelDialog.Speech -> DefaultModelRole.SpeechRecognition
    DefaultModelDialog.Tts -> DefaultModelRole.SpeechSynthesis
    DefaultModelDialog.RealtimeModel -> DefaultModelRole.Realtime
    DefaultModelDialog.TtsVoice -> null
}
