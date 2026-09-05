package github.ponyhuang.gimi.ui.chatcontent

/** 聊天气泡消息角色，决定对齐方向与配色；与具体业务的会话模型解耦。 */
enum class ChatBubbleRole {
    /** 用户输入：右对齐、填充色气泡。 */
    USER,

    /** 助手回复：左对齐开放式排版，无填充气泡。 */
    ASSISTANT,
}
