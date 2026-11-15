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
 * 聊天输入框文档草稿的厂商上传编排。
 *
 * 只编排"chip 阶段预热上传"这一条路径：成功后写注册表（上传即记录，与是否发送无关）
 * 并置 Ready；历史重放不经过这里（会话引用直通 file_id，不上传不哈希）。
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

    /** 启动 [draft] 的上传；同引用去重，未启用上传或已是厂商引用的草稿直接忽略。 */
    fun upload(draft: DraftAttachment, selection: ModelSelection) {
        if (draft.category != AttachmentCategory.DOCUMENT || draft.isVendorUploaded) return
        if (!isFileUploadEnabled(selection)) return
        if (jobs.containsKey(draft.reference)) return
        val resolved = resolveConnection(selection)
        if (resolved == null) {
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
