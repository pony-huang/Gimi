package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 云上传使用不可变归档副本，失败或取消不能移动、删除正在编辑的原草稿。 */
interface CloudFileLocalSource {
    suspend fun archive(sessionId: String, draft: DraftAttachment): FileAttachment
}

/** 每个文件的真实准备状态；SDK 无字节回调时不生成虚假的百分比。 */
sealed interface CloudAttachmentState {
    /** 尚未开始该文件的准备。 */
    data object Waiting : CloudAttachmentState
    /** 正在归档到共享工作区，原草稿保留。 */
    data object Preparing : CloudAttachmentState
    /** 正在等待 Files 上传或复用校验结果。 */
    data object Uploading : CloudAttachmentState
    /** 只有持久化的远端记录成功后才允许进入模型请求；expiresAtMillis 为毫秒级复用截止时间。 */
    data class Ready(val attachment: FileAttachment, val expiresAtMillis: Long = Long.MAX_VALUE) : CloudAttachmentState
    /** 失败只属于当前文件，已就绪的其它文件不回退。 */
    data class Failed(val reason: CloudFileFailure) : CloudAttachmentState
    /** 单文件取消不移除本次发送快照，批次继续等待显式重试或整体取消。 */
    data object Cancelled : CloudAttachmentState
}

/** 索引关联原发送快照；UI 保留原名称、缩略图，与后来编辑的草稿分离。 */
data class CloudAttachmentItem(val draft: DraftAttachment, val state: CloudAttachmentState = CloudAttachmentState.Waiting)

class CloudAttachmentBatchFactory @Inject constructor(
    private val localSource: CloudFileLocalSource,
    private val prepareFile: PrepareCloudFileUseCase,
    private val support: CloudFileChatSupport,
) {
    fun create(sessionId: String, service: LLMModelSetting, modelId: String, drafts: List<DraftAttachment>) =
        CloudAttachmentBatch(sessionId, service, modelId, drafts.toList(), localSource, prepareFile, support)
}

/** 一次发送独占的准备任务；归档缓存和逐文件 Job 不跨发送共享，远端去重由仓库负责。 */
class CloudAttachmentBatch internal constructor(
    private val sessionId: String,
    private val service: LLMModelSetting,
    private val modelId: String,
    drafts: List<DraftAttachment>,
    private val localSource: CloudFileLocalSource,
    private val prepareFile: PrepareCloudFileUseCase,
    private val support: CloudFileChatSupport,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    val id: String = java.util.UUID.randomUUID().toString()
    private val _items = MutableStateFlow(drafts.map(::CloudAttachmentItem))
    val items = _items.asStateFlow()
    private val started = AtomicBoolean()
    private val commands = Channel<Pair<Int, Boolean>>(Channel.UNLIMITED)

    fun retry(index: Int) { commands.trySend(index to true) }
    fun cancel(index: Int) { commands.trySend(index to false) }

    /** null 表示副作用前的能力拒绝；其它失败保留批次等待，全部 Ready 才返回模型输入。 */
    suspend fun awaitReady(): List<FileAttachment>? = coroutineScope {
        check(started.compareAndSet(false, true)) { "A cloud preparation batch can run only once" }
        val rejected = _items.value.map { item -> when {
            !service.isFileUploadEnabled -> CloudFileFailure.Disabled
            !service.isEnabled || !service.hasValidConnectionConfiguration() ||
                service.apiProtocol != github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions -> CloudFileFailure.InvalidConfiguration
            !support.supports(service, modelId, item.draft.mimeType) -> CloudFileFailure.UnverifiedChatReference
            else -> null
        } }
        if (rejected.any { it != null }) {
            _items.update { items -> items.mapIndexed { index, item -> item.copy(state =
                rejected[index]?.let(CloudAttachmentState::Failed) ?: CloudAttachmentState.Cancelled) } }
            commands.close()
            return@coroutineScope null
        }
        val jobs = arrayOfNulls<Job>(_items.value.size)
        val tokens = arrayOfNulls<Any>(_items.value.size)
        val archived = arrayOfNulls<FileAttachment>(_items.value.size)
        fun set(index: Int, token: Any, state: CloudAttachmentState) {
            if (tokens[index] === token) _items.update { list -> list.mapIndexed { i, item -> if (i == index) item.copy(state = state) else item } }
        }
        fun start(index: Int) {
            jobs[index]?.cancel()
            val token = Any()
            tokens[index] = token
            set(index, token, CloudAttachmentState.Preparing)
            jobs[index] = launch {
                val draft = _items.value[index].draft
                try {
                    val reference = draft.cloudReference
                    val flow = if (reference != null) {
                        prepareFile.existing(service, modelId, reference.fileId, reference.scope)
                    } else {
                        val source = archived[index] ?: localSource.archive(sessionId, draft).also { archived[index] = it }
                        prepareFile(service, modelId, draft.copy(reference = requireNotNull(source.payloadReference), sizeBytes = source.sizeBytes, mimeType = source.mimeType))
                    }
                    flow.collect { result -> set(index, token, when (result) {
                        CloudFilePreparation.Uploading -> CloudAttachmentState.Uploading
                        is CloudFilePreparation.Failed -> CloudAttachmentState.Failed(result.reason)
                        is CloudFilePreparation.Ready -> {
                            val file = result.file
                            val attachment = archived[index] ?: FileAttachment(file.mimeType, file.id, file.sizeBytes,
                                file.displayName, payloadReference = file.localReference,
                                cloudReference = CloudAttachmentReference(file.id, file.scope))
                            CloudAttachmentState.Ready(attachment.copy(cloudReference = CloudAttachmentReference(file.id, file.scope)), file.expiresAtMillis)
                        }
                    }) }
                    val terminal = _items.value[index].state
                    if (terminal == CloudAttachmentState.Preparing || terminal == CloudAttachmentState.Uploading) {
                        set(index, token, CloudAttachmentState.Failed(CloudFileFailure.InvalidResponse))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: CloudFileException) {
                    set(index, token, CloudAttachmentState.Failed(failure.reason))
                } catch (_: IOException) {
                    set(index, token, CloudAttachmentState.Failed(CloudFileFailure.LocalFileUnavailable))
                } catch (_: IllegalArgumentException) {
                    set(index, token, CloudAttachmentState.Failed(CloudFileFailure.LocalFileUnavailable))
                } catch (_: Exception) {
                    set(index, token, CloudAttachmentState.Failed(CloudFileFailure.UploadRejected))
                }
            }
        }
        _items.value.indices.forEach(::start)
        val controls = launch {
            for ((index, retry) in commands) {
                val current = _items.value.getOrNull(index)?.state ?: continue
                if (current is CloudAttachmentState.Ready) continue
                if (retry) {
                    if (current is CloudAttachmentState.Failed || current == CloudAttachmentState.Cancelled) start(index)
                } else {
                    tokens[index] = Any() // 已取消 Job 的迟到结果不能覆盖新的状态或重试结果。
                    jobs[index]?.cancel()
                    _items.update { list -> list.mapIndexed { i, item -> if (i == index) item.copy(state = CloudAttachmentState.Cancelled) else item } }
                }
            }
        }
        try {
            var ready = _items.first { list -> list.all { it.state is CloudAttachmentState.Ready } }
            fun expired(): List<Int> {
                val now = nowMillis()
                return ready.indices.filter { (ready[it].state as CloudAttachmentState.Ready).expiresAtMillis <= now }
            }
            var expiredIndices = expired()
            while (expiredIndices.isNotEmpty()) {
                // 某文件等待其它附件期间可能到期；只重做过期项，不能把过期 ID 发给模型。
                expiredIndices.forEach(::start)
                ready = _items.first { list -> list.all { it.state is CloudAttachmentState.Ready } }
                expiredIndices = expired()
            }
            ready.map { (it.state as CloudAttachmentState.Ready).attachment }
        } finally {
            commands.close()
            controls.cancel()
            jobs.forEach { it?.cancel() }
            _items.update { list -> list.map { item -> when (item.state) {
                CloudAttachmentState.Preparing, CloudAttachmentState.Uploading, CloudAttachmentState.Waiting ->
                    item.copy(state = CloudAttachmentState.Cancelled)
                else -> item
            } } }
        }
    }
}
