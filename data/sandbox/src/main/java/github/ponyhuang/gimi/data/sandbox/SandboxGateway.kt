package github.ponyhuang.gimi.data.sandbox

import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput

/** 一个聊天独占的 SDK 客户端；open 不创建远端资源，create 才发起请求。 */
interface SandboxGatewayFactory {
    fun open(connection: SandboxConnection): SandboxGateway
}

/** Android 无关的远端调用接缝，生命周期测试无需真实网络或凭据。 */
interface SandboxGateway {
    suspend fun create()
    suspend fun isRunning(): Boolean
    suspend fun refreshTimeout()
    suspend fun execute(operation: SandboxOperation): SandboxOutput
    fun cancelRequests()
    suspend fun shutdown()
}

/** SDK 边界已脱敏的可恢复错误，不携带 URL、响应正文或密钥。 */
class SandboxGatewayException(val failure: SandboxGatewayFailure, val partialOutput: String = "") : Exception(failure.message)

/** 模型可据此判断是否修改任务或配置，不应自动重放副作用操作。 */
enum class SandboxGatewayFailure(val message: String) {
    UNAVAILABLE("Sandbox is unavailable. The operation was not replayed."),
    AUTHENTICATION("E2B authentication or permission was denied. Check sandbox settings."),
    RATE_LIMIT("E2B rate limit was reached. Retry later."),
    TIMEOUT("Sandbox operation timed out. Its execution status may be unknown; do not automatically replay it."),
    TOO_LARGE("File exceeds the 1 MiB limit."),
    FILE_NOT_FOUND("Sandbox file or directory was not found."),
    TEMPLATE("The configured sandbox template is unavailable."),
    INTERPRETER("The configured template does not provide an accessible Code Interpreter."),
    TEXT_ENCODING("This file is not valid UTF-8 text."),
    PROTOCOL("The sandbox returned an invalid or oversized response."),
    FAILED("The sandbox operation could not complete. Its execution status may be unknown."),
}
