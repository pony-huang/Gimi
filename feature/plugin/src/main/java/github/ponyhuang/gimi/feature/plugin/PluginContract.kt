package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogSource
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.isUpdateFor

/** 单个条目的安装阶段；失败状态保留原因供卡片展示。 */
sealed interface PluginInstallUiState {
    /** 条目当前无下载任务。 */
    data object Idle : PluginInstallUiState

    /** 正在解析下载资产。 */
    data object Resolving : PluginInstallUiState

    /** 下载进度；[progress] 范围为 0 到 1。 */
    data class Downloading(val progress: Float) : PluginInstallUiState

    /** 本次下载失败；[failure] 用于选择本地化错误文案。 */
    data class Failed(val failure: PluginInstallFailure) : PluginInstallUiState

    /** 是否正在占用下载/安装通道。 */
    val isInProgress: Boolean
        get() = this is Resolving || this is Downloading
}

/**
 * 插件列表项：已安装插件与订阅目录条目的合并投影。
 *
 * @property catalog 订阅目录元数据；已安装但不在目录中的插件为 null。
 * @property installed 已安装插件描述；未安装为 null。
 * @property installState 安装进度/失败状态。
 */
data class PluginListItemUi(
    val catalog: PluginCatalogEntry?,
    val installed: PluginDescriptor?,
    val installState: PluginInstallUiState,
) {
    val key: String
        get() = catalog?.key ?: requireNotNull(installed).id

    val isInstalled: Boolean
        get() = installed != null

    val isUpdateAvailable: Boolean
        get() = installed?.let { catalog?.isUpdateFor(it) } == true

    val title: String
        get() = installed?.name?.takeIf(String::isNotBlank) ?: catalog?.title.orEmpty()
}

/** 插件列表页状态。 */
data class PluginSettingsUiState(
    val items: List<PluginListItemUi> = emptyList(),
    val query: String = "",
    /** 是否正在重新发现插件/刷新订阅，驱动下拉刷新指示器。 */
    val isRefreshing: Boolean = false,
    val isLoadingCatalog: Boolean = true,
    val catalogSource: PluginCatalogSource = PluginCatalogSource.BUNDLED,
    /** 至少一个远端订阅源失败。 */
    val catalogError: Boolean = false,
    val canRequestPackageInstalls: Boolean = true,
    /** 当前占用下载/安装通道的条目。 */
    val installingEntryKey: String? = null,
    /** 等待用户确认卸载的插件 id；非空时显示卸载确认框。 */
    val pendingUninstallPluginId: String? = null,
)

sealed interface PluginSettingsAction {
    /** 搜索已安装或可安装插件。 */
    data class QueryChanged(val value: String) : PluginSettingsAction

    /** 切换插件启用状态。 */
    data class SetEnabled(val pluginId: String, val enabled: Boolean) : PluginSettingsAction

    /** 重新发现插件并强制刷新订阅目录。 */
    data object Refresh : PluginSettingsAction

    /** 安装订阅目录中的插件。 */
    data class Install(val entryKey: String) : PluginSettingsAction

    /** 更新已安装的同包名插件，复用下载和系统安装通道。 */
    data class Update(val entryKey: String) : PluginSettingsAction

    /** 请求卸载插件；仅弹出确认框，不直接执行。 */
    data class RequestUninstall(val pluginId: String) : PluginSettingsAction

    /** 用户取消卸载确认框。 */
    data object DismissUninstall : PluginSettingsAction

    /** 用户确认卸载，交由宿主拉起系统卸载页。 */
    data class ConfirmUninstall(val pluginId: String) : PluginSettingsAction
}

sealed interface PluginSettingsEffect {
    /** 发现新安装的插件。 */
    data class ShowPluginAdded(val pluginIds: List<String>) : PluginSettingsEffect

    /**
     * 请求宿主拉起系统卸载页卸载插件包。
     *
     * @property packageName 待卸载的插件 APK 包名。
     */
    data class RequestSystemUninstall(val packageName: String) : PluginSettingsEffect

    /** APK 已下载并校验，调起系统安装器。 */
    data class LaunchInstaller(val contentUri: String) : PluginSettingsEffect

    /** 未授予未知来源安装权限，打开系统设置。 */
    data object OpenUnknownSourcesSettings : PluginSettingsEffect
}

/** 插件订阅源编辑页状态。 */
data class PluginSubscriptionUiState(
    val urls: List<String> = emptyList(),
    val defaultUrl: String = "",
    val isAddDialogVisible: Boolean = false,
    val draftUrl: String = "",
    val error: PluginSubscriptionError? = null,
)

/** 订阅 URL 的本地校验失败原因。 */
enum class PluginSubscriptionError {
    INVALID_URL,
    DUPLICATE,
}

sealed interface PluginSubscriptionAction {
    /** 打开追加订阅源对话框。 */
    data object AddClicked : PluginSubscriptionAction

    /** 关闭追加订阅源对话框。 */
    data object DismissAdd : PluginSubscriptionAction

    data class DraftChanged(val value: String) : PluginSubscriptionAction

    /** 校验并追加当前草稿地址。 */
    data object ConfirmAdd : PluginSubscriptionAction

    /** 移除指定订阅源。 */
    data class Remove(val url: String) : PluginSubscriptionAction

    /** 恢复为仅包含官方默认源。 */
    data object Reset : PluginSubscriptionAction
}

/** 插件配置页状态。 */
data class PluginConfigUiState(
    val pluginId: String = "",
    val fields: List<PluginConfigFieldUiState> = emptyList(),
    val actions: List<PluginActionUiState> = emptyList(),
    val callback: PluginActionCallbackUiState? = null,
) {
    val hasFields: Boolean get() = fields.isNotEmpty()
    val hasActions: Boolean get() = actions.isNotEmpty()
    val isAnyActionRunning: Boolean get() = actions.any { it.running }
}

/**
 * 配置动作等待宿主回调时的交互页状态。
 *
 * @property actionId 触发弹窗的配置页动作。
 * @property handlerId 宿主交互处理器标识。
 * @property parameters 由处理器解释的不透明参数。
 */
data class PluginActionCallbackUiState(
    val actionId: String,
    val handlerId: String,
    val parameters: Map<String, String> = emptyMap(),
)

/**
 * 配置页动作的可点击状态。
 *
 * @property id 动作标识。
 * @property label 按钮文案。
 * @property running 是否正在执行（执行中按钮禁用）。
 */
data class PluginActionUiState(
    val id: String,
    val label: String,
    val running: Boolean = false,
    val params: Map<String, Any> = emptyMap(),
)

/**
 * 配置页单个字段的可编辑状态。
 *
 * @property value 当前字符串值（TOGGLE 为 `"true"`/`"false"`）。
 */
data class PluginConfigFieldUiState(
    val key: String,
    val label: String,
    val kind: PluginConfigFieldDescriptor.Kind,
    val secret: Boolean = false,
    val options: List<String> = emptyList(),
    val value: String = "",
)

sealed interface PluginConfigAction {
    data class SetValue(val key: String, val value: String) : PluginConfigAction
    data object Save : PluginConfigAction
    data class RunAction(val actionId: String) : PluginConfigAction

    /**
     * 把宿主交互生成的通用键值回传插件。
     *
     * @property actionId 等待回调的配置动作。
     * @property values 插件自行解释的回调参数。
     */
    data class ReceiveActionCallback(
        val actionId: String,
        val values: Map<String, String>,
    ) : PluginConfigAction

    /** 用户取消并关闭配置动作的回调交互页。 */
    data object DismissActionCallback : PluginConfigAction
}

sealed interface PluginConfigEffect {
    /**
     * 通过宿主 Toast 展示插件动作或保存结果。
     *
     * @property message 插件返回的文本；与 [messageRes] 二选一。
     * @property messageRes 本地化文案资源 ID；与 [message] 二选一。
     */
    data class ShowToast(
        val message: String? = null,
        val messageRes: Int? = null,
    ) : PluginConfigEffect
}
