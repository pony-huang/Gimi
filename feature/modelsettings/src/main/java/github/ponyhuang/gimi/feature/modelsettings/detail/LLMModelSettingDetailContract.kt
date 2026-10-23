package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.feature.modelsettings.R

/** 编辑器内部页面共享同一份草稿，子页返回不创建另一份配置。 */
enum class ServiceEditorPage { Detail, Models }

/** 草稿、候选目录和业务操作状态；原始快照用于判断退出时是否需要提示。 */
data class LLMModelSettingDetailUiState(
    val isLoading: Boolean = true,
    val service: LLMModelSetting? = null,
    val baseline: LLMModelSetting? = null,
    val page: ServiceEditorPage = ServiceEditorPage.Detail,
    val candidates: List<LLMModelSettingDetailRow.LLMModelItem> = emptyList(),
    val pendingModelKeys: Set<ServiceModelKey> = emptySet(),
    val candidateGroupNames: Map<String, String> = emptyMap(),
    val supportedTools: List<OfficialToolSpec> = emptyList(),
    val isTestingKey: Boolean = false,
    val connectionResult: Boolean? = null,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val isClosing: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val isConnectionExpanded: Boolean = false,
    val isProtocolMenuExpanded: Boolean = false,
    val isServiceMenuExpanded: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val isDiscardDialogVisible: Boolean = false,
    val isDeleteDialogVisible: Boolean = false,
    val newModelId: String = "",
    val newModelKind: NewModelKind = NewModelKind.Chat,
    val isMutationBlocked: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
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
    /** 暂停或恢复已保存的服务。 */
    data class EnabledChanged(val value: Boolean) : LLMModelSettingDetailAction
    /** 修改聊天工具入口的草稿偏好。 */
    data class OfficialToolsEnabledChanged(val value: Boolean) : LLMModelSettingDetailAction
    /** 修改独立的文件上传草稿偏好。 */
    data class FileUploadEnabledChanged(val value: Boolean) : LLMModelSettingDetailAction
    /** 只修改未来上传的有效期。 */
    data class FileUploadRetentionChanged(val days: Int) : LLMModelSettingDetailAction
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
    /** 显示或隐藏密钥。 */
    data object ToggleApiKeyVisibility : LLMModelSettingDetailAction
    /** 切换协议菜单。 */
    data object ToggleProtocolMenu : LLMModelSettingDetailAction
    /** 关闭协议菜单。 */
    data object DismissProtocolMenu : LLMModelSettingDetailAction
    /** 切换服务管理菜单。 */
    data object ToggleServiceMenu : LLMModelSettingDetailAction
    /** 关闭服务管理菜单。 */
    data object DismissServiceMenu : LLMModelSettingDetailAction
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
    /** 显示删除确认。 */
    data object RequestDelete : LLMModelSettingDetailAction
    /** 取消删除。 */
    data object CancelDelete : LLMModelSettingDetailAction
    /** 删除配置与凭据。 */
    data object ConfirmDelete : LLMModelSettingDetailAction
}

sealed interface LLMModelSettingDetailNotice {
    /** 厂商目录中不存在该项。 */
    data object SettingNotFoundLLM : LLMModelSettingDetailNotice
    /** 配置成功保存。 */
    data object Saved : LLMModelSettingDetailNotice
    /** 删除配置成功。 */
    data object Deleted : LLMModelSettingDetailNotice
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

enum class NewModelKind(@StringRes val labelRes: Int) {
    Chat(R.string.modelsettings_new_model_chat),
    Stt(R.string.modelsettings_new_model_stt),
    Tts(R.string.modelsettings_new_model_tts),
}
