package github.ponyhuang.gimi.domain.voice.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 简单聊天（实时语音 Simple 模式）的自定义提示词预设。
 * @property id 稳定唯一标识，编辑时保持不变；官方预设使用 `builtin.` 前缀，与用户自建预设不冲突。
 * @property name 供列表展示的短名称。
 * @property content 预设正文；选中后将完全替换简单聊天的内置语音指令。
 * @property description 列表副标题用的简短说明；为空时界面回退展示正文。
 */
data class SimpleChatPromptPreset(
    val id: String,
    val name: String,
    val content: String,
    val description: String = "",
)

/**
 * 简单聊天提示词预设的全局仓库；所有通话共享同一份配置，进程内即时生效。
 * 选中项为 null 表示不使用预设，简单聊天回退到内置语音指令。
 */
interface SimpleChatPromptRepository {
    /** 用户自建的预设，按创建顺序排列。 */
    val presets: StateFlow<List<SimpleChatPromptPreset>>

    /** 随应用发布的官方预设，只读；与用户自建预设一起参与选中项解析。 */
    val builtInPresets: List<SimpleChatPromptPreset>

    /** 当前选中的预设 id；可能指向官方预设或用户自建预设，为 null 表示不使用预设。 */
    val selectedPresetId: StateFlow<String?>

    /** 新增或更新预设；id 已存在时按位覆盖，否则追加到列表尾部。 */
    fun upsertPreset(preset: SimpleChatPromptPreset)

    /**
     * 删除预设；被删除的是当前选中项时选中态自动回退为 null，不自动改选其他预设。
     * id 不存在时为无操作。官方预设不参与删除。
     */
    fun deletePreset(presetId: String)

    /**
     * 设置当前选中预设；null 表示回到内置语音指令。
     * 传入既不是官方预设也不是用户自建预设的 id 会被忽略并归一化为 null，避免悬空选中。
     */
    fun selectPreset(presetId: String?)
}
