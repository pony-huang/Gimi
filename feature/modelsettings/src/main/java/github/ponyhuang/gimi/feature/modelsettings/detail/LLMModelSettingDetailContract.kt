package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.feature.modelsettings.R

/** 编辑器内部页面共享同一份草稿，子页返回不创建另一份配置。 */
enum class ServiceEditorPage { Detail, Models }

/** 草稿、候选目录和业务操作状态；原始快照用于判断退出时是否需要提示。 */
data class LLMModelSettingDetailUiState(
    val isLoading: Boolean = true,
    val service: LLMModelSetting? = null,
    val baseline: LLMModelSetting? = null,
    /**
     * 该服务的端点来源策略。
     *
     * 端点由厂商自有的服务不展示"接入地址"字段：用户填写的地址不会参与请求构造，
     * 让用户填写一个不生效的地址只会制造误解。
     */
    val endpointStrategy: EndpointStrategy = EndpointStrategy.ConfiguredBaseUrl,
    val page: ServiceEditorPage = ServiceEditorPage.Detail,
    val candidates: List<LLMModelSettingDetailRow.LLMModelItem> = emptyList(),
    val pendingModelKeys: Set<ServiceModelKey> = emptySet(),
    val candidateGroupNames: Map<String, String> = emptyMap(),
    val supportedTools: List<OfficialToolSpec> = emptyList(),
    val isTestingKey: Boolean = false,
    val connectionResult: ServiceConnectionResult? = null,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val isClosing: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isConnectionExpanded: Boolean = false,
    val isProtocolMenuExpanded: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val isDiscardDialogVisible: Boolean = false,
    val newModelId: String = "",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isMutationBlocked: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    /** 服务类别约束手动添加和模型分组；语音厂商接受级联与实时语音模型，不接受聊天模型。 */
    val allowedModelKinds: List<NewModelKind>
        get() = when (service?.kind) {
            ModelServiceKind.Speech -> NewModelKind.entries.filter { it != NewModelKind.Chat }
            else -> listOf(NewModelKind.Chat)
        }
    val isDirty: Boolean get() = service != baseline
    val canEdit: Boolean get() = !isMutationBlocked && !isSaving && !isClosing
    val rows: List<LLMModelSettingDetailRow.LLMModelItem>
        get() = service?.groups.orEmpty().flatMap { group ->
            group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) }
        }
}

/** 模型选择保留原始分组 ID，避免破坏默认模型引用。 */
data class ServiceModelKey(val groupId: String, val modelId: String)

sealed interface LLMModelSettingDetailAction {
    /** 加载厂商模板或已有配置。 */
    data class Load(val serviceId: String) : LLMModelSettingDetailAction
    /** 修改草稿密钥。 */
    data class ApiKeyChanged(val value: String) : LLMModelSettingDetailAction
    /** 修改当前协议的地址。 */
    data class ApiBaseUrlChanged(val value: String) : LLMModelSettingDetailAction
    /** 切换厂商支持的协议。 */
    data class ApiProtocolChanged(val value: ApiProtocol) : LLMModelSettingDetailAction
    /** 修改聊天工具入口的草稿偏好。 */
    data class OfficialToolsEnabledChanged(val value: Boolean) : LLMModelSettingDetailAction
    /** 打开共享草稿的子页面。 */
    data class OpenPage(val page: ServiceEditorPage) : LLMModelSettingDetailAction
    /** 勾选或取消候选模型。 */
    data class ToggleModel(val key: ServiceModelKey) : LLMModelSettingDetailAction
    /** 将全部候选加入待确认选择。 */
    data object SelectAllModels : LLMModelSettingDetailAction
    /** 清空待确认选择，不直接修改已保存配置。 */
    data object ClearModels : LLMModelSettingDetailAction
    /** 页内展开或收起接口设置。 */
    data object ToggleConnection : LLMModelSettingDetailAction
    /** 修改手动添加的 ID。 */
    data class NewLLMModelIdChanged(val value: String) : LLMModelSettingDetailAction
    /** 修改手动添加的模型类型。 */
    data class NewLLMModelKindChanged(val value: NewModelKind) : LLMModelSettingDetailAction
    /** 修改百炼业务空间 ID；留空走官方默认域名。 */
    data class WorkspaceIdChanged(val value: String) : LLMModelSettingDetailAction
    /** 显示或隐藏密钥。 */
    data object ToggleApiKeyVisibility : LLMModelSettingDetailAction
    /** 切换协议菜单。 */
    data object ToggleProtocolMenu : LLMModelSettingDetailAction
    /** 关闭协议菜单。 */
    data object DismissProtocolMenu : LLMModelSettingDetailAction
    /** 打开手动添加。 */
    data object ShowAddDialog : LLMModelSettingDetailAction
    /** 关闭手动添加。 */
    data object DismissAddDialog : LLMModelSettingDetailAction
    /** 添加候选并勾选，不写入仓库。 */
    data object ConfirmAddLLMModel : LLMModelSettingDetailAction
    /** 用当前草稿检测连接。 */
    data object TestConnection : LLMModelSettingDetailAction
    /** 获取模型候选，不改变已选模型。 */
    data object RefreshModels : LLMModelSettingDetailAction
    /** 将模型子页的选择应用到服务草稿。 */
    data object ConfirmModels : LLMModelSettingDetailAction
    /** 原子保存并关闭编辑器。 */
    data object Save : LLMModelSettingDetailAction
    /** 处理系统返回及工具栏返回。 */
    data object Back : LLMModelSettingDetailAction
    /** 放弃修改并退出。 */
    data object Discard : LLMModelSettingDetailAction
    /** 继续编辑。 */
    data object ContinueEditing : LLMModelSettingDetailAction
}

sealed interface LLMModelSettingDetailNotice {
    /** 本次请求实际返回的有效、去重模型数量，不是累计候选数。 */
    data class ModelsRefreshed(val count: Int) : LLMModelSettingDetailNotice
    /** 厂商目录中不存在该项。 */
    data object SettingNotFoundLLM : LLMModelSettingDetailNotice
    /** 配置成功保存。 */
    data object Saved : LLMModelSettingDetailNotice
    /** Agent 正在运行，禁止改变配置。 */
    data object AgentMutationBlocked : LLMModelSettingDetailNotice
}

/** Route 负责反馈和退出等 Android 副作用。 */
sealed interface LLMModelSettingDetailEffect {
    /** 一次性业务反馈。 */
    data class ShowToast(val notice: LLMModelSettingDetailNotice) : LLMModelSettingDetailEffect
    /** 返回上级导航。 */
    data object Close : LLMModelSettingDetailEffect
}

sealed interface LLMModelSettingDetailRow {
    /** 展示模型及其稳定的目录归属。 */
    data class LLMModelItem(val groupId: String, val model: Model) : LLMModelSettingDetailRow {
        val key: ServiceModelKey get() = ServiceModelKey(groupId, model.id)
    }
}

/**
 * 用户为模型选择的任务类型。
 *
 * 每个类型对应一组 [github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability]；
 * 选定类型即把该能力集合写进模型的能力集合。
 */
enum class NewModelKind(@StringRes val labelRes: Int) {
    Chat(R.string.modelsettings_model_kind_chat),
    Stt(R.string.modelsettings_model_kind_stt),
    Asr(R.string.modelsettings_model_kind_asr),
    Tts(R.string.modelsettings_model_kind_tts),
    Realtime(R.string.modelsettings_model_kind_realtime),
    ;

    /** 该类型对应的模型能力集合；Realtime 是独立的会话标记，不携带级联能力。 */
    val capabilities: Set<ModelCapability>
        get() = when (this) {
            Chat -> setOf(ModelCapability.LLM)
            Stt -> setOf(ModelCapability.STT)
            Asr -> setOf(ModelCapability.ASR)
            Tts -> setOf(ModelCapability.TTS)
            Realtime -> setOf(ModelCapability.Realtime)
        }
}
