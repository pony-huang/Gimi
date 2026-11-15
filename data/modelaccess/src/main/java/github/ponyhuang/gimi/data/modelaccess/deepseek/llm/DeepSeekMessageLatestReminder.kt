package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import com.anthropic.models.messages.MessageParam
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DeepSeek 用户元信息标记（latest_reminder，见 api-docs.deepseek.com/zh-cn/guides/latest_reminder.md）。
 *
 * 格式：`<date>,<weekday>,<location>,<platform>,<language>`，紧挨所属 user 消息之前。
 * 契约约束：content 必须是字符串（否则 422）；后面必须紧跟 user 消息（否则 400）；
 * 不能夹在 tool_use 与对应的 tool_result 之间（否则 400）。
 *
 * 注入策略（已确认）：仅注入在本轮最新一条纯输入 user 消息之前，不随历史回传——
 * 前缀稳定，对 prompt cache 最友好；日期取设备时区当前时间，location 留空（可选字段）。
 */
const val LATEST_REMINDER_ROLE = "latest_reminder"

/** 拼装 latest_reminder 内容；`now` 与 `locale` 可注入便于测试。 */
fun latestReminderContent(
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
    val weekday = SimpleDateFormat("EEEE", locale).format(Date(now))
    // 语言名按语言自身书写（zh → 中文、en → English），与 DeepSeek 支持的语言取值对齐。
    val language = locale.getDisplayLanguage(locale).ifBlank {
        locale.language.ifBlank { "English" }
    }
    return "$date,$weekday,,App,$language"
}

/**
 * 在末位「纯输入」user 消息之前插入 latest_reminder。
 *
 * 末位 user 若携带 tool_result（工具循环回传轮或与文本混排），一律不注入，
 * 避免 latest_reminder 落在 tool_use 与 tool_result 之间。无 user 消息或内容为空时原样返回。
 */
internal fun List<MessageParam>.withLatestReminder(reminder: String?): List<MessageParam> {
    if (reminder.isNullOrBlank()) return this
    val index = indexOfLast { it.role().asString() == "user" }
    if (index < 0) return this
    val blocks = this[index].content().asBlockParams()
    if (blocks.isEmpty() || blocks.any { it.isToolResult() }) return this
    val reminderParam = MessageParam.builder()
        .role(MessageParam.Role.of(LATEST_REMINDER_ROLE))
        .content(reminder)
        .build()
    return subList(0, index) + reminderParam + subList(index, size)
}
