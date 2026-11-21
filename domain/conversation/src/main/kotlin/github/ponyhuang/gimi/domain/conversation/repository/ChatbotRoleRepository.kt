package github.ponyhuang.gimi.domain.conversation.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Chatbot 文字与语音共用的角色预设。
 * @property id 稳定唯一标识，编辑时保持不变；官方预设使用 `builtin.` 前缀，与用户自建预设不冲突。
 * @property name 供列表展示的短名称。
 * @property content 预设正文；选中后作为当前会话的人设正文，输出规范由各入口追加。
 * @property description 列表副标题用的简短说明；为空时界面回退展示正文。
 */
data class ChatbotRolePreset(
    val id: String,
    val name: String,
    val content: String,
    val description: String = "",
)

/**
 * Chatbot 角色目录与每会话的角色选择仓库，进程内即时生效。
 * 会话未选择角色时使用内置指令，新会话不会继承其他会话的选择。
 */
interface ChatbotRoleRepository {
    /** 用户自建的预设，按创建顺序排列。 */
    val presets: StateFlow<List<ChatbotRolePreset>>

    /** 随应用发布的官方预设，只读；与用户自建预设一起参与选中项解析。 */
    val builtInPresets: List<ChatbotRolePreset>

    /** 会话 ID 到角色 ID 的映射；缺失项表示默认角色。 */
    val selectedPresetIds: StateFlow<Map<String, String>>

    /** 新增或更新预设；id 已存在时按位覆盖，否则追加到列表尾部。官方预设只读，其 id 不可覆盖。 */
    fun upsertPreset(preset: ChatbotRolePreset)

    /**
     * 删除预设；被删除的是当前选中项时选中态自动回退为 null，不自动改选其他预设。
     * id 不存在时为无操作。官方预设不参与删除。
     */
    fun deletePreset(presetId: String)

    /**
     * 设置指定会话的角色；null 表示回到内置角色。
     * 传入既不是官方预设也不是用户自建预设的 id 会被忽略并归一化为 null，避免悬空选中。
     */
    fun selectPreset(conversationId: String, presetId: String?)
}

/** 解析指定会话的角色正文，已删除或未选择的角色回到内置指令。 */
fun ChatbotRoleRepository.contentFor(conversationId: String?): String? {
    val id = selectedPresetIds.value[conversationId] ?: return null
    return (builtInPresets + presets.value).firstOrNull { it.id == id }?.content
}
