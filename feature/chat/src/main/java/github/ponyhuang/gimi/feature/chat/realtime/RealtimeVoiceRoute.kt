package github.ponyhuang.gimi.feature.chat.realtime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.feature.chat.R

/**
 * 权限和 Android 副作用只在入口处理；无状态页面不接触模型副作用。
 * 懒启动：进入页面不占用麦克风，「开始通话」按钮触发权限确认后才真正 start；
 * 挂断只结束会话并留在待启动状态，只有回聊天才离开页面（系统后台切换仍由前台服务维持通话）。
 */
@Composable
fun RealtimeVoiceRoute(
    conversationId: String,
    selection: ModelSelection?,
    conversationTitle: String,
    active: Boolean = true,
    onDismiss: () -> Unit,
) {
    val viewModel: RealtimeVoiceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    var presentedState by remember { mutableStateOf(state) }
    // 退场期间保留最后一帧，防止挂断清理产生的 Closed/空字幕闪成“连接中”。
    // 只冻结展示快照，麦克风和后台通话仍立即由控制器释放。
    SideEffect { if (active && !leaving) presentedState = state }
    // 进入页面只登记会话绑定参数（模式预选可用性与「开始通话」使用），不启动通话。
    LaunchedEffect(viewModel, active) {
        if (active) viewModel.prepare(conversationId, selection, conversationTitle)
    }
    val context = LocalContext.current
    val permissionMessage = stringResource(R.string.voice_permission)
    val newSessionFailedMessage = stringResource(R.string.voice_new_session_failed)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && active) viewModel.startCall() else if (active) {
            // 拒绝权限时停留在待启动页面，用户可再次点「开始通话」重试。
            Toast.makeText(context, permissionMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val startCall = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCall()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // 助手模式新建会话失败：保持原会话继续通话，仅提示。
    LaunchedEffect(viewModel) {
        var seen = viewModel.newSessionFailedRevision.value
        viewModel.newSessionFailedRevision.collect { revision ->
            if (revision > seen) Toast.makeText(context, newSessionFailedMessage, Toast.LENGTH_SHORT).show()
            seen = revision
        }
    }
    // 语音设置写入被通话/Agent 拦截（二级页已置灰，这里是兜底）：仅提示。
    val settingsBlockedMessage = stringResource(R.string.voice_settings_mutation_blocked)
    LaunchedEffect(viewModel) {
        var seen = viewModel.settingsWriteBlockedRevision.value
        viewModel.settingsWriteBlockedRevision.collect { revision ->
            if (revision > seen) Toast.makeText(context, settingsBlockedMessage, Toast.LENGTH_SHORT).show()
            seen = revision
        }
    }
    // 通话中点「更多选项」：抽屉里的模式与语音配置在通话中本就不可写，只提示先结束通话。
    val moreOptionsBlockedMessage = stringResource(R.string.voice_more_options_blocked)
    // 音色抽屉的「查看官方音色列表」：外链是 Android 副作用，归 Route。
    val onOpenUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    val routing = state.voice.audioRouting
    val currentDeviceName = voiceAudioDeviceName(routing.devices.firstOrNull { it.id == routing.currentDeviceId })
    val routingMessage = stringResource(
        if (routing.selectionFailed) R.string.voice_device_failed else R.string.voice_device_disconnected,
        currentDeviceName,
    )
    // revision 持久在控制器状态里：以进入本组合时的 revision 为基线，
    // 只提示之后新发生的路由事件，避免重进页面重放历史 Toast。
    val shownNoticeRevision = remember { routing.noticeRevision }
    LaunchedEffect(routing.noticeRevision) {
        if (routing.noticeRevision != shownNoticeRevision) Toast.makeText(context, routingMessage, Toast.LENGTH_SHORT).show()
    }
    // 系统返回与左胶囊「回聊天」同义：结束通话并返回文字聊天。
    val backToChat = {
        if (!leaving) {
            presentedState = state
            leaving = true
            onDismiss()
            viewModel.end()
        }
    }
    // 主按钮挂断只是关闭会话：留在语音页回到待启动状态，不返回聊天列表。
    val hangUp = { viewModel.end() }
    BackHandler { backToChat() }
    // 展示快照（含退场冻结帧）同时决定主按钮状态，避免离场动画期间闪回「开始通话」。
    val presented = if (active && !leaving) state else presentedState
    RealtimeVoiceScreen(
        uiState = presented,
        started = presented.active,
        onStartCall = startCall,
        onSelectMode = viewModel::selectMode,
        onSelectSettingsModel = { kind, selection ->
            when (kind) {
                VoiceSettingsKind.Speech -> selection?.let(viewModel::selectSpeechModel)
                VoiceSettingsKind.Tts -> selection?.let(viewModel::selectTtsModel)
                VoiceSettingsKind.Realtime -> viewModel.selectRealtimeModel(selection)
            }
        },
        onSelectSettingsVoice = { kind, voiceId ->
            when (kind) {
                VoiceSettingsKind.Tts -> voiceId?.let(viewModel::selectTtsVoice)
                VoiceSettingsKind.Realtime -> viewModel.selectRealtimeVoice(voiceId)
                else -> Unit
            }
        },
        onSelectPromptPreset = viewModel::selectPromptPreset,
        onOpenUrl = onOpenUrl,
        onMoreOptionsBlocked = { Toast.makeText(context, moreOptionsBlockedMessage, Toast.LENGTH_SHORT).show() },
        onOpenDevicePicker = viewModel::openDevicePicker,
        onDismissDevicePicker = viewModel::dismissDevicePicker,
        onSelectAudioDevice = viewModel::selectAudioDevice,
        onToggleMute = viewModel::toggleMute,
        onRetry = viewModel::retry,
        onNewSession = viewModel::newSession,
        onEnd = hangUp,
        onBack = backToChat,
    )
}
