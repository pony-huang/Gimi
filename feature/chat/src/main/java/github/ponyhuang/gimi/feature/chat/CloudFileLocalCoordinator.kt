package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.ResolveCloudFileLocalUseCase
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** ViewModel 所有的本地原文件恢复：同一引用仅加载一次，配置改变取消旧连接并丢弃迟到结果。 */
internal class CloudFileLocalCoordinator(
    private val scope: CoroutineScope,
    private val resolve: ResolveCloudFileLocalUseCase,
    private val services: () -> List<LLMModelSetting>,
    private val update: (Map<CloudAttachmentReference, String?>) -> Unit,
) {
    private var snapshot = emptyList<LLMModelSetting>()
    private var token = Any()
    private val jobs = mutableMapOf<CloudAttachmentReference, Job>()
    private val attempted = mutableSetOf<CloudAttachmentReference>()
    private val paths = mutableMapOf<CloudAttachmentReference, String?>()
    private val permits = Semaphore(3)

    fun reset() {
        token = Any()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        attempted.clear()
        paths.clear()
        snapshot = services().toList()
        update(emptyMap())
    }

    fun request(references: Set<CloudAttachmentReference>) {
        if (snapshot != services()) reset()
        val current = token
        references.forEach { reference ->
            if (!attempted.add(reference)) return@forEach
            val connection = snapshot
            jobs[reference] = scope.launch {
                val path = try {
                    permits.withPermit { resolve(connection, reference) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: CloudFileException) {
                    null
                }
                if (token === current && services() == connection) {
                    paths[reference] = path
                    update(paths.toMap())
                }
            }
        }
    }
}

/** 只更新展示副本，不修改发送快照和历史文件 ID；大图直接复用恢复后的原文件。 */
internal fun FileAttachment.withCloudLocal(paths: Map<CloudAttachmentReference, String?>): FileAttachment {
    val reference = cloudReference ?: return this
    return copy(payloadReference = paths[reference], inlineData = null, isMissing = paths[reference] == null)
}

internal fun Message.withCloudLocal(paths: Map<CloudAttachmentReference, String?>): Message =
    if (fileAttachments.none { it.cloudReference != null }) this
    else copy(fileAttachments = fileAttachments.map { it.withCloudLocal(paths) })

internal fun ChatListItem.withCloudLocal(paths: Map<CloudAttachmentReference, String?>): ChatListItem = when (this) {
    is ChatListItem.UserMessage -> copy(message = message.withCloudLocal(paths))
    is ChatListItem.AssistantTurn -> copy(timeline = timeline.copy(segments = timeline.segments.map {
        if (it is TurnSegment.Answer) it.copy(message = it.message.withCloudLocal(paths)) else it
    }))
}
