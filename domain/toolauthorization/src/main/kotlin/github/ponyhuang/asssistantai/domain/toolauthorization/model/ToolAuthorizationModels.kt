package github.ponyhuang.asssistantai.domain.toolauthorization.model

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: LocalToolCategory,
)

data class ToolDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
)
