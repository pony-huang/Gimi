package github.ponyhuang.gimi.domain.mobileuse

/**
 * 一次兜底 shell 命令的结果；非零退出码或超时不能证明命令没有产生副作用。
 *
 * @property exitCode shell 正常结束时的退出码；超时未确认退出时为空。
 * @property output 合并的标准输出和错误输出，长度由执行后端限制。
 * @property timedOut 是否达到执行时限并请求终止 shell 进程。
 * @property outputTruncated 输出超长或未完整读取，不能把返回文本当成完整结果。
 */
data class MobileShellResult(
    val exitCode: Int?,
    val output: String,
    val timedOut: Boolean,
    val outputTruncated: Boolean,
)
