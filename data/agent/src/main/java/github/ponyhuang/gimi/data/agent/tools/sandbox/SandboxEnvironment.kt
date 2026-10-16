@file:OptIn(com.google.adk.kt.annotations.ExperimentalEnvironmentApi::class)

package github.ponyhuang.gimi.data.agent.tools.sandbox

import com.google.adk.kt.agents.Context
import com.google.adk.kt.environment.Environment
import com.google.adk.kt.environment.EnvironmentException
import com.google.adk.kt.environment.ExecutionResult
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import github.ponyhuang.gimi.domain.sandbox.SandboxSessionKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/** 官方环境接口到聊天沙箱的适配；共享实例每次读取调用会话，不持有或关闭远端资源。 */
@Singleton
class SandboxEnvironment @Inject constructor(private val execution: SandboxExecutionRepository) : Environment {
    override suspend fun execute(context: Context, command: String, timeout: Duration?): Result<ExecutionResult> =
        operation(context, SandboxOperation.Command(command, timeoutSeconds = timeout.secondsLimit())) { output ->
            val result = output as SandboxOutput.Command
            ExecutionResult(exitCode = result.exitCode, stdout = result.stdout, stderr = result.stderr)
        }

    override suspend fun readFile(context: Context, path: String): Result<ByteArray> =
        operation(context, SandboxOperation.ReadBytes(path)) { (it as SandboxOutput.ReadBytes).content }

    override suspend fun writeFile(context: Context, path: String, content: ByteArray): Result<Unit> =
        operation(context, SandboxOperation.WriteBytes(path, content.copyOf())) { Unit }

    private suspend fun <T> operation(context: Context, operation: SandboxOperation, map: (SandboxOutput) -> T): Result<T> {
        val key = context.session.key
        val id = key.id?.takeIf(String::isNotBlank)
            ?: return Result.failure(EnvironmentException("A persistent conversation identity is required."))
        // 仓库复查开关并按聊天串行执行；取消自然传播，不重试未知执行状态。
        val result = execution.execute(SandboxSessionKey(key.userId, id), operation)
        val output = result.output
        if (!result.success || output == null) return Result.failure(EnvironmentException(result.error.ifBlank { "Environment operation failed." }))
        return Result.success(map(output))
    }
}

/** SDK 只接受整秒；向上取整避免短时限意外成为不设超时。 */
private fun Duration?.secondsLimit(): Int =
    if (this == null || isInfinite()) 0 else ((inWholeMilliseconds + 999) / 1000).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
