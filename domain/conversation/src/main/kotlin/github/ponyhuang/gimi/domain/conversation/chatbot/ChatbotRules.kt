package github.ponyhuang.gimi.domain.conversation.chatbot

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.modelcatalog.model.CHATBOT_WEB_SEARCH_TOOL_IDS

/**
 * Chatbot 形态的纯业务规则。
 *
 * Chatbot 是 conversation 存储中与助手并列的一种会话形态（分区隔离的「当前 Chatbot 会话」），
 * 规则只依赖模型 ID、厂商模板与文案字符串，不感知存储与运行时形态。
 */
object ChatbotRules {

    /**
     * 模型锁定校验：Chatbot 会话提交首条消息后模型即锁定，换模型须新建会话。
     * 模型身份只看模型 ID——同模型跨实例（换连接）仍视为同一模型。
     *
     * @param lockedModelId 已锁定会话的模型 ID；null 表示尚未提交首条消息（未锁定）。
     * @param selectedModelId 本次选择的模型 ID。
     */
    fun requiresNewConversation(lockedModelId: String?, selectedModelId: String): Boolean =
        lockedModelId != null && lockedModelId != selectedModelId

    /**
     * 首轮话语回落标题：现有标题优先；否则取首条用户消息文本；
     * 文本为空白时退到首个附件名；最终截断到 32 字符。
     */
    fun fallbackTitle(
        currentTitle: String,
        firstUserText: String,
        firstAttachmentName: String? = null,
    ): String = currentTitle.ifBlank {
        firstUserText.ifBlank { firstAttachmentName.orEmpty() }.take(32)
    }

    /**
     * 新会话的默认工具与思考配置：[templateId] 厂商的全部 Chatbot 检索工具默认启用；
     * 思考默认开启且强度为高——Chatbot 只暴露 Anthropic Messages 协议连接（均支持
     * 思考强度），滑杆与 chip 因此默认落在「强」档；用户仍可随时滑回关闭。
     * 无检索工具的厂商（含自定义服务）得到空工具集，即无工具的普通 agent。
     */
    fun defaultToolConfiguration(templateId: String?): ConversationToolConfiguration =
        ConversationToolConfiguration(
            thinkingEnabled = true,
            reasoningEffort = ReasoningEffort.HIGH,
            enabledOfficialFunctionIds = CHATBOT_WEB_SEARCH_TOOL_IDS[templateId].orEmpty()
                .associateWith { setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER) },
        )
}
