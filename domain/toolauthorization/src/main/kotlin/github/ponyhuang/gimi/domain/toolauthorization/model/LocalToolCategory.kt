package github.ponyhuang.gimi.domain.toolauthorization.model

/**
 * 设备本地工具的业务大类。
 *
 * 仅作为权限设置页的稳定展示分组；Agent 运行时目录和 `tool_search` 均使用
 * 扁平工具集合，不再按本枚举拆分候选来源或参与相关性计算。
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
