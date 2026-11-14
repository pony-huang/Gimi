package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.feature.modelsettings.R

/** 编辑器内部页面共享同一份草稿；子页返回不创建另一份配置。 */
enum class ServiceEditorPage { Detail, Models, Connections, OfficialTools, Suggestions }

/** 模型管理页的候选行：候选来自模板目录、已选模型、手动添加与远端刷新。 */
data class ModelCandidateRow(
    val connectionId: String,
    val capability: ServiceCapability,
    val model: Model,
) {
    /** 模型管理页的选择键；确认后按连接写回草稿。 */
    val key: String get() = "$connectionId/${model.id}"
}

/** 连接测试状态；版本绑定草稿，凭据或端点修改后结果不再适用。 */
data class ConnectionTestState(
    val draftVersion: Long = -1L,
    val isRunning: Boolean = false,
    val result: ServiceConnectionResult? = null,
) {
    /** 当前草稿版本下结果是否仍然适用。 */
    fun isApplicable(currentVersion: Long): Boolean = draftVersion == currentVersion && result != null
}

/** 接口设置页的单条连接编辑投影。 */
data class ConnectionEditor(
    val connection: ServiceConnection,
    val endpointPolicy: EndpointPolicy,
    val endpointSchemes: Set<String>,
    val supportsWorkspace: Boolean,
    val protocols: List<ApiProtocol>,
    val test: ConnectionTestState,
) {
    val isConversation: Boolean get() = connection.capability == ServiceCapability.Conversation
}

/** 空用途默认建议；保存页确认一次后随服务保存一起提交。 */
data class DefaultModelSuggestion(
    val role: DefaultModelRole,
    @param:StringRes val roleLabelRes: Int,
    val selection: github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection,
    /** 建议展示名："模型 · 实例"。 */
    val modelLabel: String,
)

/** 厂商工具页的目录行：支持矩阵 + 当前协议可用性。 */
data class OfficialToolRow(
    val spec: OfficialToolSpec,
    val isSupportedByProtocol: Boolean,
)

/** 详情编辑器状态：草稿、基线与各子页投影；原始快照用于退出提示。 */
data class ModelServiceDetailUiState(
    val isLoading: Boolean = true,
    /** 尚未落库的新实例；保存时由仓库分配稳定 ID。 */
    val isNewInstance: Boolean = true,
    /** 来源模板；null 表示自定义 LLM 服务。 */
    val template: ModelServiceTemplate? = null,
    val draft: ModelServiceDraft? = null,
    val baseline: ModelServiceDraft? = null,
    /** 已有实例的启停状态；来自仓库，独立于草稿提交。 */
    val isEnabled: Boolean = true,
    val page: ServiceEditorPage = ServiceEditorPage.Detail,
    val modelQuery: String = "",
    val selectedCategory: ServiceCapability? = null,
    val candidates: List<ModelCandidateRow> = emptyList(),
    /** 会话级候选增量：远端刷新与手动添加；确认选择后并入草稿并从这里消失。 */
    val extraCandidates: List<ModelCandidateRow> = emptyList(),
    val pendingModelKeys: Set<String> = emptySet(),
    val connections: List<ConnectionEditor> = emptyList(),
    val supportedTools: List<OfficialToolRow> = emptyList(),
    val connectionTestVersion: Long = 0L,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val isClosing: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isConnectionKeyVisible: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val newModelId: String = "",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isDiscardDialogVisible: Boolean = false,
    val deletionImpact: DeletionImpact? = null,
    /** 非空表示进入保存前的建议确认页；确认后随服务一起提交。 */
    val suggestions: List<DefaultModelSuggestion>? = null,
    /** 建议确认页中被用户接受的角色集合。 */
    val acceptedSuggestionRoles: Set<DefaultModelRole> = emptySet(),
    val isMutationBlocked: Boolean = false,
    /** 从默认模型用途选择器进入；保存后返回原选择器，建议流程被跳过。 */
    val originRole: DefaultModelRole? = null,
    @param:StringRes val errorRes: Int? = null,
) {
    val isDirty: Boolean get() = draft != baseline
    val canEdit: Boolean get() = !isMutationBlocked && !isSaving && !isClosing
    val supportsTools: Boolean get() = draft?.supportsOfficialTools == true
    val pendingSelectionCount: Int get() = pendingModelKeys.size

    /** 是否存在需要确认的未保存修改（含模型选择工作集未确认的差异由确认键承载）。 */
    val hasUnsavedChanges: Boolean get() = isDirty

    /** 删除入口仅对已落库实例展示。 */
    val canDelete: Boolean get() = !isNewInstance && draft != null
}

sealed interface ModelServiceDetailAction {
    /** 加载已有实例或按模板构建新实例草稿。 */
    data class Load(val instanceId: String?, val templateId: String?, val origin: DefaultModelRole?) :
        ModelServiceDetailAction
    data class NameChanged(val value: String) : ModelServiceDetailAction
    data class SharedApiKeyChanged(val value: String) : ModelServiceDetailAction
    data object ToggleApiKeyVisibility : ModelServiceDetailAction
    data object ToggleConnectionKeyVisibility : ModelServiceDetailAction
    /** 独立启停；停用保留默认引用。 */
    data class EnabledChanged(val enabled: Boolean) : ModelServiceDetailAction
    /** 厂商工具偏好（详情页与工具页共用）。 */
    data class OfficialToolsEnabledChanged(val value: Boolean) : ModelServiceDetailAction
    data class OpenPage(val page: ServiceEditorPage) : ModelServiceDetailAction
    // 模型管理
    data class ModelQueryChanged(val value: String) : ModelServiceDetailAction
    data class SelectCategory(val capability: ServiceCapability?) : ModelServiceDetailAction
    data class ToggleModel(val key: String) : ModelServiceDetailAction
    data object ConfirmModels : ModelServiceDetailAction
    data object ShowAddDialog : ModelServiceDetailAction
    data object DismissAddDialog : ModelServiceDetailAction
    data class NewModelIdChanged(val value: String) : ModelServiceDetailAction
    data class NewModelKindChanged(val value: NewModelKind) : ModelServiceDetailAction
    data object ConfirmAddModel : ModelServiceDetailAction
    /** 获取对话连接的远端候选；可选能力，不改变已选模型。 */
    data object FetchModels : ModelServiceDetailAction
    // 接口设置
    data class ConnectionProtocolChanged(val connectionId: String, val protocol: ApiProtocol) :
        ModelServiceDetailAction
    data class ConnectionEndpointChanged(val connectionId: String, val endpoint: String) :
        ModelServiceDetailAction
    data class ConnectionCredentialSharedChanged(val connectionId: String, val shared: Boolean) :
        ModelServiceDetailAction
    data class ConnectionIndependentKeyChanged(val connectionId: String, val apiKey: String) :
        ModelServiceDetailAction
    data class ConnectionWorkspaceChanged(val connectionId: String, val workspaceId: String) :
        ModelServiceDetailAction
    data class TestConnection(val connectionId: String) : ModelServiceDetailAction
    // 删除
    data object RequestDelete : ModelServiceDetailAction
    data object DismissDelete : ModelServiceDetailAction
    data object ConfirmDelete : ModelServiceDetailAction
    // 保存
    data object Save : ModelServiceDetailAction
    /** 切换一条默认建议的接受状态；保存时一并提交。 */
    data class ToggleSuggestion(val role: DefaultModelRole, val accepted: Boolean) : ModelServiceDetailAction
    /** 确认默认建议并保存服务。 */
    data object ConfirmSuggestions : ModelServiceDetailAction
    data object SkipSuggestions : ModelServiceDetailAction
    // 退出
    data object Back : ModelServiceDetailAction
    data object Discard : ModelServiceDetailAction
    data object ContinueEditing : ModelServiceDetailAction
}

sealed interface ModelServiceDetailNotice {
    /** 本次远端刷新实际返回的有效、去重模型数量。 */
    data class ModelsFetched(val count: Int) : ModelServiceDetailNotice
    data object InstanceNotFound : ModelServiceDetailNotice
    data object Saved : ModelServiceDetailNotice
    data object Deleted : ModelServiceDetailNotice
    data object AgentMutationBlocked : ModelServiceDetailNotice
}

/** Route 负责反馈和退出等 Android 副作用。 */
sealed interface ModelServiceDetailEffect {
    data class ShowToast(val notice: ModelServiceDetailNotice) : ModelServiceDetailEffect
    /** 返回上级导航。 */
    data object Close : ModelServiceDetailEffect
    /**
     * 保存成功。实例已由 VM 原地收编（后续保存不会再新建）；导航语义由 Route 决定：
     * 用途来源流程弹栈后进默认模型页，普通新建弹栈回服务列表，编辑已有实例原地停留。
     *
     * @property popToServiceList 新实例保存后连弹两层（详情 + 添加目录）回服务列表。
     */
    data class Saved(val instanceId: String, val popToServiceList: Boolean) : ModelServiceDetailEffect
    /** 删除成功，返回服务列表。 */
    data object Deleted : ModelServiceDetailEffect
}

/** 用户为模型选择的任务类型；手动添加时把能力集合写进模型。 */
enum class NewModelKind(@StringRes val labelRes: Int) {
    Chat(R.string.modelsettings_model_kind_chat),
    Stt(R.string.modelsettings_model_kind_stt),
    Asr(R.string.modelsettings_model_kind_asr),
    Tts(R.string.modelsettings_model_kind_tts),
    Realtime(R.string.modelsettings_model_kind_realtime),
    ;

    val capabilities: Set<ModelCapability>
        get() = when (this) {
            Chat -> setOf(ModelCapability.LLM)
            Stt -> setOf(ModelCapability.STT)
            Asr -> setOf(ModelCapability.ASR)
            Tts -> setOf(ModelCapability.TTS)
            Realtime -> setOf(ModelCapability.Realtime)
        }

    /** 手动模型落入哪个连接；同一能力类别只有一条连接。 */
    val capability: ServiceCapability
        get() = when (this) {
            Chat -> ServiceCapability.Conversation
            Stt, Asr -> ServiceCapability.Recognition
            Tts -> ServiceCapability.Synthesis
            Realtime -> ServiceCapability.Realtime
        }
}
