package github.ponyhuang.gimi.domain.modelaccess

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 模型选择契约：从配置源解析当前请求应使用的模型配置。
 *
 * 编排层（:data:agent）只依赖本接口获取 [ModelConfig]；协议适配（按协议构造
 * SDK 会话对象、注入凭据）留在 :data:modelaccess 的实现中，不进入编排层。
 */
interface AgentLlmModelSource {

    /**
     * 选当前模型配置。
     *
     * 优先用用户显式选择的模型；若选择为空 / 指向的服务被禁用 / 组或模型已不存在，
     * 自动回退到"第一个启用服务 + 第一个非空组的第一个模型"的旧逻辑。
     *
     * 没有可用配置时抛 [IllegalStateException]，由 UI 层提示用户在
     * Settings → Model Service 启用至少一个服务。
     */
    fun selectModelConfig(explicitSelection: ModelSelection?): ModelConfig

    /** 解析快速模型的配置；未配置快速模型时为 null。 */
    fun selectFastModelConfig(): ModelConfig?

    /** 格式化推荐只走快速模型所属实例的 OpenAI 兼容对话端点，不沿用聊天协议。 */
    fun forRecommendationJson(config: ModelConfig): ModelConfig
}
