package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFilePreparation
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileRepository
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 共享上传记录与内容去重；只有持久化成功才发布 Ready，取消向上传播。 */
internal class DefaultCloudFileRepository(
    private val storage: CloudFileStorage,
    private val remote: CloudFileRemoteGateway,
    private val localDirectory: File? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CloudFileRepository {
    // 同一作用域的查重、上传和提交为一个临界区，避免并发重试重复创建远端文件。
    // 不同连接仍可并发；存储读改写由独立锁保护，避免覆盖其它连接新写入的记录。
    private val scopeLocks = mutableMapOf<String, Mutex>()
    private val storageLock = Mutex()

    override suspend fun list(service: LLMModelSetting): List<CloudFile> = withContext(Dispatchers.IO) {
        val scope = cloudFileScope(service)
        storageLock.withLock { storage.read().filter { it.scope == scope }.sortedByDescending { it.createdAtMillis } }
    }

    override suspend fun resolveLocal(service: LLMModelSetting, reference: CloudAttachmentReference): CloudFile = withContext(Dispatchers.IO) {
        val scope = cloudFileScope(service)
        // 先检查作用域再读取任何本地路径或发起网络请求，旧凭据的图片也不能越界复用。
        if (scope != reference.scope) throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        val existing = list(service).firstOrNull { it.id == reference.fileId }
            ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        // 本地预览不能排在其它文件的慢上传后面；只有恢复下载才进入连接临界区。
        if (existing.hasIntactLocalSource()) return@withContext existing
        val lock = synchronized(scopeLocks) { scopeLocks.getOrPut(scope) { Mutex() } }
        lock.withLock {
            val record = list(service).firstOrNull { it.id == reference.fileId }
                ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
            try {
                if (record.hasIntactLocalSource()) return@withLock record
                if (!record.isReusable(nowMillis()) || !service.isEnabled || !service.hasValidConnectionConfiguration()) {
                    throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
                }
                val directory = localDirectory ?: throw CloudFileException(CloudFileFailure.StorageUnavailable)
                if (!directory.isDirectory && !directory.mkdirs()) throw CloudFileException(CloudFileFailure.StorageUnavailable)
                val scratch = File.createTempFile("restore-", ".part", directory)
                var published: File? = null
                try {
                    remote.download(service, record, scratch)
                    currentCoroutineContext().ensureActive()
                    if (scratch.length() != record.sizeBytes || scratch.contentHash() != record.contentHash) {
                        throw CloudFileException(CloudFileFailure.InvalidResponse)
                    }
                    val extension = record.displayName.substringAfterLast('.', "").takeIf { it.length in 1..12 && it.all(Char::isLetterOrDigit) }
                    val target = File(directory, java.util.UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: ""))
                    if (!scratch.renameTo(target)) throw CloudFileException(CloudFileFailure.StorageUnavailable)
                    published = target
                    val restored = record.copy(localReference = target.absolutePath)
                    storageLock.withLock {
                        storage.write(storage.read().map { if (it.scope == scope && it.id == record.id) restored else it })
                    }
                    // 磁盘提交成功后该原文件属于共享记录；缩略图与大图共同读取这一路径。
                    published = null
                    restored
                } finally {
                    scratch.delete()
                    published?.delete()
                }
            } catch (_: IOException) {
                throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
            }
        }
    }

    private suspend fun CloudFile.hasIntactLocalSource(): Boolean {
        val source = localReference?.let(::File) ?: return false
        return try {
            source.isFile && source.canRead() && source.length() == sizeBytes && source.contentHash() == contentHash
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun canReupload(service: LLMModelSetting, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val record = list(service).firstOrNull { it.id == fileId } ?: return@withContext false
        val source = record.localReference?.let(::File) ?: return@withContext false
        try {
            source.isFile && source.length() == record.sizeBytes && source.contentHash() == record.contentHash
        } catch (_: IOException) {
            false
        }
    }

    override fun prepareExisting(service: LLMModelSetting, fileId: String): Flow<CloudFilePreparation> = flow {
        try {
            if (!service.isFileUploadEnabled) throw CloudFileException(CloudFileFailure.Disabled)
            if (!service.isEnabled || !service.hasValidConnectionConfiguration()) {
                throw CloudFileException(CloudFileFailure.InvalidConfiguration)
            }
            val record = list(service).firstOrNull { it.id == fileId }
                ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
            if (record.isReusable(nowMillis())) {
                emit(CloudFilePreparation.Ready(record))
            } else {
                val reference = record.localReference
                    ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
                val source = File(reference)
                if (!source.isFile || source.length() != record.sizeBytes || source.contentHash() != record.contentHash) {
                    throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
                }
                val category = AttachmentCategory.from(record.mimeType, record.displayName)
                    ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
                emitAll(prepare(service, DraftAttachment(reference, record.displayName,
                    record.mimeType, record.sizeBytes, category)))
            }
        } catch (failure: CloudFileException) {
            emit(CloudFilePreparation.Failed(failure.reason))
        } catch (_: IOException) {
            emit(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable))
        }
    }.flowOn(Dispatchers.IO)

    override fun prepare(service: LLMModelSetting, attachment: DraftAttachment): Flow<CloudFilePreparation> = flow {
        try {
            if (!service.isFileUploadEnabled) throw CloudFileException(CloudFileFailure.Disabled)
            if (!service.isEnabled || !service.hasValidConnectionConfiguration()) {
                throw CloudFileException(CloudFileFailure.InvalidConfiguration)
            }
            val scope = cloudFileScope(service)
            val scopeLock = synchronized(scopeLocks) { scopeLocks.getOrPut(scope) { Mutex() } }
            scopeLock.withLock {
                val source = File(attachment.reference)
                if (!source.isFile || !source.canRead() || source.length() != attachment.sizeBytes) {
                    throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
                }
                val hash = source.contentHash()
                val cached = storageLock.withLock {
                    storage.read().firstOrNull {
                        it.scope == scope && it.contentHash == hash && it.isReusable(nowMillis())
                    }
                }
                if (cached != null) {
                    val reusable = cached.copy(localReference = attachment.reference)
                    storageLock.withLock {
                        storage.write(storage.read().map { if (it.scope == scope && it.id == cached.id) reusable else it })
                    }
                    emit(CloudFilePreparation.Ready(reusable))
                    return@withLock
                }
                emit(CloudFilePreparation.Uploading)
                val uploaded = remote.upload(service, attachment, source)
                currentCoroutineContext().ensureActive()
                val requestedExpiry = uploaded.createdAtMillis + service.fileUploadRetentionDays * 86_400_000L
                val expiresAt = minOf(requestedExpiry, uploaded.remoteExpiresAtMillis ?: requestedExpiry)
                if (uploaded.id.isBlank() || uploaded.createdAtMillis <= 0 || expiresAt <= nowMillis()) {
                    throw CloudFileException(CloudFileFailure.InvalidResponse)
                }
                val record = CloudFile(
                    id = uploaded.id, scope = scope, displayName = attachment.displayName,
                    mimeType = attachment.mimeType, sizeBytes = attachment.sizeBytes, contentHash = hash,
                    createdAtMillis = uploaded.createdAtMillis, expiresAtMillis = expiresAt,
                    remoteExpiresAtMillis = uploaded.remoteExpiresAtMillis, localReference = attachment.reference,
                )
                storageLock.withLock {
                    val records = storage.read().filterNot { it.scope == scope && it.id == record.id }
                    storage.write(records + record)
                }
                emit(CloudFilePreparation.Ready(record))
            }
        } catch (failure: CloudFileException) {
            emit(CloudFilePreparation.Failed(failure.reason))
        } catch (_: IOException) {
            emit(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun File.contentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }
}

/** 长度前缀消除拼接歧义；协议、地址或密钥变化都生成新的隔离空间。 */
internal fun cloudFileScope(service: LLMModelSetting): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(service.id, service.apiProtocol.name, service.activeApiBaseUrl.trim().trimEnd('/'), service.apiKey.trim())
        .forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
