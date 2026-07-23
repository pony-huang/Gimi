package github.ponyhuang.asssistantai.domain.assistant.repository

/** 系统助理角色与入口能力的只读状态。 */
interface AssistantSystemStatusRepository {
    /** ROM 是否提供默认数字助理角色。 */
    fun isAssistantRoleAvailable(): Boolean

    /** 应用当前是否持有默认数字助理角色。 */
    fun isDefaultAssistant(): Boolean
}
