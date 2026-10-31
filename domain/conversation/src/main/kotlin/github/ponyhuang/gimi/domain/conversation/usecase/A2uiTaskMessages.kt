package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message

/** 按原调用标识替换任务快照，保留原消息 id、时间和卡片位置。 */
fun List<Message>.updateA2uiTask(response: FunctionResponseView): List<Message> = map { message ->
    if (message.functionResponses.none { it.a2uiTask?.surfaceId == response.a2uiTask?.surfaceId && it.a2uiTask != null }) {
        message
    } else message.copy(functionResponses = message.functionResponses.map {
        if (it.a2uiTask?.surfaceId == response.a2uiTask?.surfaceId) response else it
    })
}

/** 无活跃执行时收尾遗留任务：进程重启和主动取消不能保留永久加载状态。 */
fun List<Message>.interruptA2uiTasks(): List<Message> = map { message ->
    message.copy(functionResponses = message.functionResponses.map { response ->
        val task = response.a2uiTask
        if (task?.status == A2uiGenerationStatus.PENDING) {
            response.copy(a2uiTask = task.copy(status = A2uiGenerationStatus.CANCELLED))
        } else response
    })
}
