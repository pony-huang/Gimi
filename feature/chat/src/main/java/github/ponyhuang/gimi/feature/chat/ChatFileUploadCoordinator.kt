package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 单个文档草稿的厂商上传状态；键为草稿 reference。
 *
 * 上传是 chip 阶段的显式业务操作：失败不静默降级（失败的 chip 提供原地重试），
 * 发送在上传完成前被 UI 禁用，因此消息形态唯一——发送时 file_id 必然就绪。
 */
sealed interface DraftUploadState {
    /** 上传中；发送禁用。 */
    data object Uploading : DraftUploadState

    /** 已就绪；屏上草稿随后被替换为携带 fileId 的副本。 */
    data class Ready(val fileId: String, val vendor: String) : DraftUploadState

    /** 失败；发送禁用，chip 提供原地重试。 */
    data class Failed(
        val reason: VendorFileFailureReason,
        val message: String? = null,
    ) : DraftUploadState
}

/**
 * 聊天输入框文档草稿的厂商上传编排；助手 / Chatbot / 实时语音三个 surface 共用。
 *
 * 两个入口共用同一套任务去重、状态机与注册表记录（上传成功即记录，与是否发送无关）：
 * - [upload]：chip 阶段预热上传（选完即传）；已带厂商引用的草稿直接忽略。
 *   历史重放不经过这里（会话引用直通 file_id，不上传不哈希）。
 * - [awaitUpload]：发送时补传，挂起至终态。供发送前准备路径兜底：同厂商已就绪的草稿
 *   短路复用不重传；厂商切换后归属旧厂商的本地副本草稿换厂商重传。
 * 上传端点与凭据来自当前选择实例的对话连接解析视图。
 */
class ChatFileUploadCoordinator(
    private val scope: CoroutineScope,
    private val gateway: VendorFileGateway,
    private val registry: UploadedFileRegistry,
    private val resolveConnection: (ModelSelection) -> ResolvedServiceConnection?,
    /** 当前选择是否启用厂商文件上传（实例开关推导的 FILE_REFERENCE 能力）。 */
    private val isFileUploadEnabled: (ModelSelection) -> Boolean,
) {
    private val _states = MutableStateFlow<Map<String, DraftUploadState>>(emptyMap())
    val states: StateFlow<Map<String, DraftUploadState>> = _states.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /** 启动 [draft] 的 chip 阶段上传；未启用上传或已是厂商引用的草稿直接忽略。 */
    fun upload(draft: DraftAttachment, selection: ModelSelection) {
        if (draft.category != AttachmentCategory.DOCUMENT || draft.isVendorUploaded) return
        if (!isFileUploadEnabled(selection)) return
        startUpload(draft, selection)
    }

    /**
     * 发送时补传：挂起至该草稿的上传终态并返回。
     *
     * 与 chip 阶段共用任务去重（进行中的同引用任务直接等待，不同入口重复触发不重传）；
     * 同厂商已就绪的草稿短路返回，不做网络往返。与 [upload] 不同，这里不设
     * "已带厂商引用即跳过"门——厂商切换后的本地副本草稿必须换厂商重传。
     * 调用方（发送准备路径）据终态决定放行或落失败。
     */
    suspend fun awaitUpload(draft: DraftAttachment, selection: ModelSelection): DraftUploadState {
        jobs[draft.reference]?.let { job ->
            job.join()
            return _states.value[draft.reference] ?: DraftUploadState.Failed(VendorFileFailureReason.VENDOR_ERROR)
        }
        if (draft.category != AttachmentCategory.DOCUMENT) {
            return DraftUploadState.Failed(VendorFileFailureReason.VENDOR_ERROR, "non-document draft")
        }
        if (!isFileUploadEnabled(selection)) {
            return DraftUploadState.Failed(VendorFileFailureReason.VENDOR_ERROR, "file upload disabled")
        }
        val vendor = resolveConnection(selection)?.templateId.orEmpty()
        // 已带同厂商 file 引用：发送门控已保证 chip 阶段落袋，直接复用无需网络往返。
        if (draft.isVendorUploaded && draft.fileVendor == vendor) {
            return DraftUploadState.Ready(requireNotNull(draft.fileId), vendor)
        }
        // chip 阶段已完成同厂商上传但草稿副本尚未回填 fileId：复用终态，不重复上传。
        (_states.value[draft.reference] as? DraftUploadState.Ready)
            ?.takeIf { it.vendor == vendor }
            ?.let { return it }
        startUpload(draft, selection)
        jobs[draft.reference]?.join()
        return _states.value[draft.reference] ?: DraftUploadState.Failed(VendorFileFailureReason.VENDOR_ERROR)
    }

    /** 统一的上传任务体：状态置 Uploading → 网关上传 → 成功写注册表并置 Ready / 失败置 Failed。 */
    private fun startUpload(draft: DraftAttachment, selection: ModelSelection) {
        // 同引用去重：进行中的任务直接复用（chip 阶段与发送时补传可能先后触发同草稿）。
        if (jobs.containsKey(draft.reference)) return
        val resolved = resolveConnection(selection)
        if (resolved == null) {
            // 与网关失败同记一条：静默置 Failed 会让真机上"无网络请求、无日志"的失败无法定位。
            Log.w(TAG, "Vendor file upload aborted: connection unresolved for selection=$selection")
            _states.update {
                it + (draft.reference to DraftUploadState.Failed(VendorFileFailureReason.VENDOR_ERROR))
            }
            return
        }
        val vendor = resolved.templateId.orEmpty()
        _states.update { it + (draft.reference to DraftUploadState.Uploading) }
        jobs[draft.reference] = scope.launch {
            val result = gateway.upload(
                vendor = vendor,
                protocol = resolved.protocol,
                endpoint = resolved.endpoint.trimEnd('/'),
                apiKey = resolved.apiKey,
                request = VendorFileUpload(
                    mimeType = draft.mimeType,
                    displayName = draft.displayName,
                    filePath = draft.reference,
                ),
            )
            when (result) {
                is VendorFileUploadResult.Uploaded -> {
                    registry.record(
                        UploadedFileEntry(
                            fileId = result.fileId,
                            vendor = vendor,
                            displayName = draft.displayName,
                            mimeType = draft.mimeType,
                            bytes = result.bytes,
                            createdAtEpochSeconds = result.createdAtEpochSeconds,
                            // 上传时草稿在 cache 目录，不是持久归档副本，不能写作本地绑定：cache
                            // 会被系统回收甚至被新草稿复用，悬空的绑定轻则失效、重则打开错文件。
                            // localPath 只在两处回写真实的工作区路径：发送时 archiveDraftAttachment
                            // 归档收口（AndroidChatAttachmentRepository.read），或打开时下载兜底成功
                            // （resolveVendorPayload）。此处留空与厂商列表刷新写入的 null 一致。
                        ),
                    )
                    _states.update {
                        it + (draft.reference to DraftUploadState.Ready(result.fileId, vendor))
                    }
                }
                is VendorFileUploadResult.Failure -> {
                    Log.w(TAG, "Vendor file upload failed: reason=${result.reason} message=${result.message} vendor=$vendor endpoint=${resolved.endpoint}")
                    _states.update {
                        it + (draft.reference to DraftUploadState.Failed(result.reason, result.message))
                    }
                }
            }
            jobs.remove(draft.reference)
        }
    }

    private companion object {
        const val TAG = "ChatFileUpload"
    }

    /** 清理引用对应的上传状态（附件移除或发送完成后）。 */
    fun clear(references: Collection<String>) {
        references.forEach { jobs.remove(it)?.cancel() }
        _states.update { current -> current - references.toSet() }
    }

    /** 清空全部状态（会话切换/草稿清空）。 */
    fun clearAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _states.value = emptyMap()
    }
}
