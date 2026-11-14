package github.ponyhuang.gimi.feature.modelsettings.detail

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp

/**
 * 服务详情编辑器入口：加载实例或模板草稿，承接反馈与导航副作用。
 *
 * @property instanceId 已有实例 ID；null 表示新建实例草稿。
 * @property templateId 新实例的来源模板；null 表示自定义 LLM 服务。
 * @property origin 从哪个默认模型用途进入；保存后由宿主返回原选择器。
 */
@Composable
fun ServiceDetailRoute(
    instanceId: String?,
    templateId: String?,
    origin: DefaultModelRole?,
    onBack: () -> Unit,
    onOpenDefaults: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(instanceId, templateId, origin) {
        viewModel.onAction(ModelServiceDetailAction.Load(instanceId, templateId, origin))
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModelServiceDetailEffect.ShowToast -> {
                    val message = when (val notice = effect.notice) {
                        is ModelServiceDetailNotice.ModelsFetched ->
                            context.getString(R.string.modelsettings_notice_models_synced, notice.count)
                        ModelServiceDetailNotice.InstanceNotFound ->
                            context.getString(R.string.modelsettings_notice_instance_missing)
                        ModelServiceDetailNotice.Saved ->
                            context.getString(R.string.modelsettings_notice_saved)
                        ModelServiceDetailNotice.Deleted ->
                            context.getString(R.string.modelsettings_notice_deleted)
                        ModelServiceDetailNotice.AgentMutationBlocked ->
                            context.getString(R.string.modelsettings_agent_mutation_blocked)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                ModelServiceDetailEffect.Close -> onBack()
                is ModelServiceDetailEffect.Saved -> when {
                    // 用途来源流程：弹掉详情与添加目录，再进默认模型页确认一次用途。
                    origin != null -> {
                        onBack()
                        onBack()
                        onOpenDefaults()
                    }
                    // 普通新建：连弹两层回服务列表；编辑已有实例原地停留。
                    effect.popToServiceList -> {
                        onBack()
                        onBack()
                    }
                }
                ModelServiceDetailEffect.Deleted -> onBack()
            }
        }
    }
    PreferenceScaffold(
        modifier = modifier,
        style = PreferenceStyle.Compact,
        title = state.draft?.name?.ifBlank {
            stringResource(
                if (state.isNewInstance) R.string.modelsettings_add_service else R.string.modelsettings_list_title,
            )
        } ?: stringResource(R.string.modelsettings_list_title),
        onBack = { viewModel.onAction(ModelServiceDetailAction.Back) },
        actions = {
            if (state.page == ServiceEditorPage.Models && state.template?.supportsConversation != false) {
                FetchModelsAction(
                    isRefreshing = state.isRefreshing,
                    onClick = { viewModel.onAction(ModelServiceDetailAction.FetchModels) },
                )
            }
        },
        bottomBar = {
            val saveLabel = when {
                state.page == ServiceEditorPage.Models ->
                    stringResource(R.string.modelsettings_confirm_models, state.pendingSelectionCount)
                state.page == ServiceEditorPage.Suggestions ->
                    stringResource(R.string.modelsettings_save_with_defaults)
                state.isNewInstance -> stringResource(R.string.modelsettings_save_new_service)
                else -> stringResource(R.string.modelsettings_save_changes)
            }
            val showSaveBar = state.page != ServiceEditorPage.Connections &&
                state.page != ServiceEditorPage.OfficialTools
            if (showSaveBar) {
                PreferenceSaveBarHost(
                    label = saveLabel,
                    enabled = state.canEdit &&
                        (state.page != ServiceEditorPage.Models || state.pendingSelectionCount > 0),
                    enabledState = state,
                    onSave = {
                        when (state.page) {
                            ServiceEditorPage.Models -> viewModel.onAction(ModelServiceDetailAction.ConfirmModels)
                            ServiceEditorPage.Suggestions ->
                                viewModel.onAction(ModelServiceDetailAction.ConfirmSuggestions)
                            else -> viewModel.onAction(ModelServiceDetailAction.Save)
                        }
                    },
                )
            }
        },
    ) { scaffoldModifier ->
        ServiceDetailScreen(state, viewModel::onAction, onOpenUrl, scaffoldModifier)
    }
}

@Composable
private fun FetchModelsAction(isRefreshing: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        if (isRefreshing) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp))
        } else {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.modelsettings_fetch_models),
            )
        }
    }
}

@Composable
private fun PreferenceSaveBarHost(
    label: String,
    enabled: Boolean,
    enabledState: ModelServiceDetailUiState,
    onSave: () -> Unit,
) {
    github.ponyhuang.gimi.ui.preference.PreferenceSaveBar(
        label = label,
        onSave = onSave,
        enabled = enabled,
        notice = if (!enabledState.isEnabled) {
            stringResource(R.string.modelsettings_save_notice_paused)
        } else {
            null
        },
    )
}
