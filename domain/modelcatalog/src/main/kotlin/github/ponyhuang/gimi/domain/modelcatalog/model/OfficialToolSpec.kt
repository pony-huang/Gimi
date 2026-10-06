package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 官方工具的纯目录描述，不持有凭据、SDK 类型或工具构造闭包。
 *
 * @property toolId 厂商唯一、用于会话配置的目录 ID。
 * @property serviceId 提供凭据的模型服务 ID。
 * @property protocols 来源服务支持的 API 协议。
 * @property modelFamilies 当前聊天模型的家族前缀限制；空集合表示不限。
 * @property displayName Agent 检索候选的来源名称。
 * @property staticFunctionIds 固定函数 ID；空列表表示需要动态加载。
 * @property searchCandidate 是否参与按需工具检索。
 * @property binding 接入方式，不包含实际执行实例。
 * @property canBeUsedAcrossModels 是否允许其他聊天模型使用独立 API 工具。
 */
data class OfficialToolSpec(
    val toolId: String,
    val serviceId: String,
    val protocols: Set<ApiProtocol>,
    val modelFamilies: Set<String> = emptySet(),
    val displayName: String,
    val staticFunctionIds: List<String> = emptyList(),
    val searchCandidate: Boolean = false,
    val binding: OfficialToolBinding,
    val canBeUsedAcrossModels: Boolean = false,
)

/** 官方工具的接入方式；具体 SDK 适配与执行由 Agent 实现。 */
sealed interface OfficialToolBinding {
    /** 厂商远端执行的协议声明，wireName 是 API 保留字面量。 */
    data class ProviderDeclaration(val wireName: String) : OfficialToolBinding

    /** 由 Agent SDK 透传的原生工具。 */
    data object ProviderNative : OfficialToolBinding

    /** 由本地函数调用厂商独立 API。 */
    data object LocalFunctions : OfficialToolBinding
}
