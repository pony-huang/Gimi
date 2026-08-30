package github.ponyhuang.gimi.data.agent.tools.system

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Launches an external intent after ADK has obtained the user's tool confirmation. */
@Singleton
class IntentActionQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("QueryPermissionsNeeded")
    fun request(title: String, summary: String, intent: Intent): Map<String, Any> {
        val launch = {
            launchIntentSafely(
                summary = summary,
                canResolve = { intent.resolveActivity(context.packageManager) != null },
                launch = {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return launch()
        }
        return runCatching {
            val result = CompletableFuture<Map<String, Any>>()
            check(mainHandler.post { result.complete(launch()) }) {
                "The main thread rejected the action."
            }
            result.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrElse { error ->
            mapOf(
                "success" to false,
                "error" to "$title failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    /**
     * 启动外部应用并挂起等待它返回结果，供"要从意图获取值"的工具使用
     * （文件选择器、联系人选择器等）。
     *
     * Activity Result 只对 Activity 身份生效，而工具运行在非 Activity 上下文，因此这里
     * 先在 [ToolResultRegistry] 登记请求，再拉起透明中转 [ToolResultActivity] 完成中转，
     * 结果经 `CompletableDeferred` 投递回挂起的工具协程。
     *
     * 等待期间整个 agent turn 保持运行态——工具立即返回会让模型在应用已跳到后台时
     * 继续流式输出，容易被系统打断；挂起等待则把模型后续生成推迟到用户带着结果
     * 回到前台之后。
     *
     * 用户取消与超时都归一为 `cancelled=true` 的成功响应（见 [toolResultState]），
     * 让模型把"用户没选"当正常分支继续对话，而不是当作故障重试。
     */
    @SuppressLint("QueryPermissionsNeeded")
    suspend fun requestForResult(
        title: String,
        summary: String,
        intent: Intent,
    ): Map<String, Any> {
        val resolvable = try {
            intent.resolveActivity(context.packageManager) != null
        } catch (_: SecurityException) {
            false
        }
        if (!resolvable) {
            return mapOf("success" to false, "error" to "No installed app can handle this action.")
        }
        val requestId = UUID.randomUUID().toString()
        val pending = ToolResultRegistry.PendingToolResultLaunch(
            intent = intent,
            result = CompletableDeferred(),
        )
        ToolResultRegistry.put(requestId, pending)
        // 等待结束（拿到结果、超时或协程被取消）后清掉登记项，避免残留条目被后续请求误用。
        pending.result.invokeOnCompletion { ToolResultRegistry.consume(requestId) }
        try {
            withContext(Dispatchers.Main.immediate) {
                context.startActivity(
                    Intent(context, ToolResultActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(ToolResultActivity.EXTRA_REQUEST_ID, requestId),
                )
            }
        } catch (error: ActivityNotFoundException) {
            ToolResultRegistry.consume(requestId)
            return mapOf(
                "success" to false,
                "error" to "$title failed: ${error.message ?: "The action could not be started."}",
            )
        } catch (error: SecurityException) {
            ToolResultRegistry.consume(requestId)
            return mapOf(
                "success" to false,
                "error" to "$title failed: ${error.message ?: "The action could not be started."}",
            )
        }
        val outcome = withTimeoutOrNull(RESULT_TIMEOUT_MS) { pending.result.await() }
        ToolResultRegistry.consume(requestId)
        return toolResultState(outcome)
    }

    private companion object {
        const val LAUNCH_TIMEOUT_SECONDS = 5L

        /** 用户在外部应用里挑选内容可能耗时很久；超时只结束等待，不视为工具故障。 */
        const val RESULT_TIMEOUT_MS = 10L * 60_000
    }
}

internal fun launchIntentSafely(
    summary: String,
    canResolve: () -> Boolean,
    launch: () -> Unit,
): Map<String, Any> {
    if (!canResolve()) {
        return mapOf("success" to false, "error" to "No installed app can handle this action.")
    }
    return runCatching {
        launch()
        mapOf("success" to true, "summary" to summary)
    }.getOrElse { error ->
        mapOf(
            "success" to false,
            "error" to (error.message ?: "The action could not be started."),
        )
    }
}

/**
 * 把外部应用回传的结果归一为模型可读的工具响应。
 *
 * - [outcome] 为 null（等待超时）或结果码非 RESULT_OK（用户取消）→ `cancelled=true`；
 * - RESULT_OK 且带数据 → `data`（主 Uri）与多选时的 `uris`；
 * - RESULT_OK 但未携带数据 → 仅 `success=true`，部分选择器以空数据表示完成。
 */
internal fun toolResultState(outcome: ToolActivityResult?): Map<String, Any> = when {
    outcome == null -> mapOf(
        "success" to true,
        "cancelled" to true,
        "timedOut" to true,
        "message" to "No selection was made in time; the user may still be choosing.",
    )
    outcome.resultCode != RESULT_OK -> mapOf("success" to true, "cancelled" to true)
    else -> buildMap {
        put("success", true)
        put("cancelled", false)
        outcome.dataUri?.let { put("data", it) }
        if (outcome.uris.size > 1) put("uris", outcome.uris)
    }
}

private const val RESULT_OK = Activity.RESULT_OK
