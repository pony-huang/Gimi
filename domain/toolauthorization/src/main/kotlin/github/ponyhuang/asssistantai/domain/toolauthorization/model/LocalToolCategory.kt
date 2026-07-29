package github.ponyhuang.asssistantai.domain.toolauthorization.model

/**
 * 设备本地工具的业务大类。
 *
 * 与 `:data:agent.tools.system` 下的 `XxxTool` 类一一对应：每个类别一个
 * `XxxTool.kt` 文件，通过 `LocalToolCatalog` 按类别聚合后暴露给 Agent 与 UI。
 *
 * 同时作为 `tool_search` 检索信号（[github.ponyhuang.asssistantai.agent.tools.dynamic]
 * 内的网关在打分与返回结果中携带本类别），并作为后续设置页按类别分组的稳定维度。
 *
 * @property id 跨模块稳定的字符串 ID，用于 invocation 上下文与序列化。
 * @property displayName 面向终端用户的中文展示名。
 */
enum class LocalToolCategory(
    val id: String,
    val displayName: String,
) {
    AUDIO("audio", "音频"),
    CALENDAR("calendar", "日历"),
    CLOCK("clock", "时钟"),
    COMMUNICATION("communication", "通讯"),
    DEVICE("device", "设备"),
    FILES("files", "文件"),
    LAUNCHERS("launchers", "应用"),
    LOCATION("location", "位置"),
    MEDIA("media", "媒体"),
    PEOPLE("people", "联系人"),
    SETTINGS("settings", "系统设置"),
    WEB("web", "Web 与搜索"),
}
