package github.ponyhuang.gimi.data.modelaccess.qwen.speech

import com.google.gson.JsonObject
import github.ponyhuang.gimi.domain.voice.repository.VoiceException
import github.ponyhuang.gimi.domain.voice.repository.VoiceFailureKind

/**
 * 阿里云百炼的语音协议。
 *
 * 一套协议同时服务语音识别与语音合成：控制事件统一是
 * `{header:{action|task_id|event}, payload:{...}}` 结构，客户端发
 * `run-task` / `continue-task` / `finish-task`，服务端回
 * `task-started` / `result-generated` / `task-finished` / `task-failed`；
 * 音频走独立的二进制帧通道。
 *
 * 识别与合成的差异只在任务载荷（`task` / `function`）与事件处理上，
 * 因此协议细节集中在本文件，传输由 [VoiceWsKernel] 承担，各能力适配器只负责构造载荷与解释事件。
 */

/** 构造一个客户端控制事件。 */
internal fun dashScopeControlEvent(action: String, taskId: String, payload: JsonObject): JsonObject =
    JsonObject().apply {
        add("header", JsonObject().apply {
            addProperty("action", action)
            addProperty("task_id", taskId)
            addProperty("streaming", DASHSCOPE_STREAMING_DUPLEX)
        })
        add("payload", payload)
    }

/** 双向流式会话的固定标记。 */
internal const val DASHSCOPE_STREAMING_DUPLEX = "duplex"

/** 音频任务组。 */
internal const val DASHSCOPE_TASK_GROUP_AUDIO = "audio"

internal const val DASHSCOPE_EVENT_TASK_STARTED = "task-started"
internal const val DASHSCOPE_EVENT_RESULT_GENERATED = "result-generated"
internal const val DASHSCOPE_EVENT_TASK_FINISHED = "task-finished"
internal const val DASHSCOPE_EVENT_TASK_FAILED = "task-failed"

internal const val DASHSCOPE_ACTION_RUN_TASK = "run-task"
internal const val DASHSCOPE_ACTION_CONTINUE_TASK = "continue-task"
internal const val DASHSCOPE_ACTION_FINISH_TASK = "finish-task"

/** 百炼业务空间请求头；业务空间 ID 非空时与专属域名一同下发（与官方 SDK 行为一致）。 */
internal const val DASHSCOPE_WORKSPACE_HEADER = "X-DashScope-WorkSpace"

/** 服务端事件的类型名。 */
internal fun JsonObject.dashScopeEventName(): String =
    getAsJsonObject("header")?.get("event")?.takeUnless { it.isJsonNull }?.asString.orEmpty()

/** 把 task-failed 事件转成可抛出的异常。 */
internal fun JsonObject.toDashScopeTaskException(): DashScopeTaskException {
    val header = getAsJsonObject("header")
    val code = header?.get("error_code")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    val message = header?.get("error_message")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    return DashScopeTaskException(code, message)
}

/** result-generated 里的句子对象。 */
private fun JsonObject.sentence(): JsonObject? =
    getAsJsonObject("payload")?.getAsJsonObject("output")?.getAsJsonObject("sentence")

/** 句子文本；心跳包没有文本。 */
internal fun JsonObject.dashScopeSentenceText(): String =
    sentence()?.get("text")?.takeUnless { it.isJsonNull }?.asString.orEmpty()

/** 句子序号；心跳包固定为 0。 */
internal fun JsonObject.dashScopeSentenceId(): Int =
    sentence()?.get("sentence_id")?.takeUnless { it.isJsonNull }?.asInt ?: 0

/** 是否为句末最终结果。 */
internal fun JsonObject.dashScopeSentenceEnded(): Boolean =
    sentence()?.get("sentence_end")?.takeUnless { it.isJsonNull }?.asBoolean ?: false

/** 服务端返回的 task-failed 事件。 */
internal class DashScopeTaskException(
    val errorCode: String,
    val errorMessage: String,
) : VoiceException(
    VoiceFailureKind.Service,
    "语音服务任务失败（$errorCode）" +
        errorMessage.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
)
