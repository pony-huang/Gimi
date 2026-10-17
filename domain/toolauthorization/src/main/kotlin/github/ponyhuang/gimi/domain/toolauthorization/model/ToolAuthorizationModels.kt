package github.ponyhuang.gimi.domain.toolauthorization.model

/** 本地工具的定义，包含唯一 ID、名称与功能描述。 */
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
)

data class ToolDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
)
