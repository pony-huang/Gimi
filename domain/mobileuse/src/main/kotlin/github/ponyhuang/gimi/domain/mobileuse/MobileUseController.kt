package github.ponyhuang.gimi.domain.mobileuse

/**
 * 与主屏、副屏无关的手机操作后端。owner 标识一次执行，chatId 标识所属聊天。
 * 坐标统一为完整截图上的 0..999；改变界面的动作尝试后返回截图，截图失败不代表动作失败。
 * 实现自行管理最新观察及其有效性，不要求 Agent 传入观察标识，也不得自动重发结果未知的动作。
 */
interface MobileUseController {
    fun availability(): MobileUseAvailability
    suspend fun registerExecution(owner: String, chatId: String)
    suspend fun openApp(owner: String, appName: String): MobileUseResult
    suspend fun click(owner: String, x: Int, y: Int): MobileUseResult
    suspend fun listApps(owner: String): MobileUseResult
    suspend fun wait(owner: String, seconds: Int = 1): MobileUseResult
    suspend fun goBack(owner: String): MobileUseResult
    /** 在当前焦点的光标或选区插入文本；无可确认的输入目标时以 text_focus_unavailable / text_focus_changed 拒绝并指向先点击字段，不自动点击或重试。 */
    suspend fun type(owner: String, text: String, pressEnter: Boolean = false): MobileUseResult
    /** 同一次按下、移动、抬起；holdMs 为起点停留时间，0 表示普通滑动。 */
    suspend fun dragAndDrop(owner: String, startX: Int, startY: Int, endX: Int, endY: Int, holdMs: Int = 0): MobileUseResult
    suspend fun longPress(owner: String, x: Int, y: Int, seconds: Int = 2): MobileUseResult
    suspend fun pressKey(owner: String, key: MobileKey): MobileUseResult
    suspend fun takeScreenshot(owner: String): MobileUseResult
    /** 当前执行持有显示会话时运行 shell 兜底命令；不限制设备级作用范围，input 命令自动定向受控显示（显式 -d/--display 除外），非 ASCII 的 input text 载荷被拒绝；不自动重试。 */
    suspend fun runShell(owner: String, command: String): MobileUseResult
    /** 释放本次执行权；界面与资源是否保留由后端决定。 */
    suspend fun finishExecution(owner: String): MobileUseResult
    /** 停止本次控制并释放本后端持有的资源；不要求关闭用户的真实应用。 */
    suspend fun stopExecution(owner: String): MobileUseResult
}

/** 可启动应用的显示名与唯一包名；显示名可能重名。 */
data class MobileApp(val name: String, val packageName: String)

/** 手机操作允许的符号按键；具体平台的键码由后端转换。 */
enum class MobileKey {
    ENTER, BACK, HOME, APP_SWITCH, DEL, FORWARD_DEL, TAB, ESCAPE, SPACE,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
    MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN,
}

/** 将模型的归一化坐标映射至实际截图边界，两个端点均落在有效像素内。 */
object MobileScreenCoordinates {
    fun pixel(value: Int, length: Int): Int {
        require(value in 0..999 && length > 0)
        return (value.toLong() * (length - 1) / 999).toInt()
    }
}
