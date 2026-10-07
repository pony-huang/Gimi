package github.ponyhuang.gimi.data.modelcatalog.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/** 应用生命周期的本地模型目录；下载可跨页面继续，进程中断后的临时文件会清理。 */
@Singleton
class DownloadedLocalModelRepository internal constructor(
    private val directory: File,
    private val specs: List<LocalModelDownloadSpec>,
    private val downloader: LocalModelFileDownloader,
) : LocalModelRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, "local-models"),
        Gemma4Catalog.specs,
        LocalModelFileDownloader(OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private val ready = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(LocalModelCatalogState(models = specs.map { LocalModelState(it.variant) }))
    override val state = mutableState.asStateFlow()

    init {
        scope.launch {
            directory.mkdirs()
            val restored = specs.map { spec ->
                File(directory, spec.variant.id + ".litertlm.part").delete()
                val valid = isVerified(spec)
                // 重命名后、marker 写入前被系统杀进程时，文件不能保留为下次发布的目标。
                if (!valid) {
                    file(spec.variant.id).delete()
                    marker(spec.variant.id).delete()
                }
                LocalModelState(spec.variant,
                    status = if (valid) LocalModelDownloadStatus.Ready else LocalModelDownloadStatus.NotDownloaded,
                )
            }
            mutableState.value = LocalModelCatalogState(loading = false, models = restored)
            ready.complete(Unit)
        }
    }

    override suspend fun awaitReady() = ready.await()

    override suspend fun download(modelId: String) {
        awaitReady()
        mutationMutex.withLock {
            val spec = spec(modelId)
            if (jobs[modelId]?.isActive == true || model(modelId).status == LocalModelDownloadStatus.Ready) return
            update(modelId) { it.copy(status = LocalModelDownloadStatus.Downloading, progress = 0f, failure = null) }
            jobs[modelId] = scope.launch {
                try {
                    downloader.download(spec, file(modelId),
                        onProgress = { progress -> update(modelId) { it.copy(progress = progress) } },
                        onVerifying = { update(modelId) { it.copy(status = LocalModelDownloadStatus.Verifying) } },
                    )
                    // marker 只在完整校验后写入；恢复时校验 marker 和固定文件长度。
                    try { marker(modelId).writeText(spec.sha256) } catch (error: IOException) {
                        throw LocalModelDownloadException(LocalModelFailure.Storage, error)
                    }
                    update(modelId) { it.copy(status = LocalModelDownloadStatus.Ready, progress = 1f) }
                } catch (cancelled: CancellationException) {
                    update(modelId) { it.copy(status = LocalModelDownloadStatus.NotDownloaded, progress = 0f) }
                    throw cancelled
                } catch (error: IOException) {
                    if (!currentCoroutineContext().isActive) {
                        update(modelId) { it.copy(status = LocalModelDownloadStatus.NotDownloaded, progress = 0f) }
                        currentCoroutineContext().ensureActive()
                    }
                    update(modelId) { it.copy(status = LocalModelDownloadStatus.Failed,
                        failure = (error as? LocalModelDownloadException)?.reason ?: LocalModelFailure.Network) }
                } finally {
                    if (model(modelId).status != LocalModelDownloadStatus.Ready) {
                        file(modelId).delete()
                        marker(modelId).delete()
                    }
                }
            }
        }
    }

    override suspend fun cancelDownload(modelId: String) {
        awaitReady()
        mutationMutex.withLock {
            spec(modelId)
            jobs.remove(modelId)?.cancelAndJoin()
        }
    }

    override suspend fun remove(modelId: String) = withContext(Dispatchers.IO) {
        awaitReady()
        mutationMutex.withLock {
            spec(modelId)
            check(model(modelId).status != LocalModelDownloadStatus.Downloading && model(modelId).status != LocalModelDownloadStatus.Verifying)
            // Ready 发布与下载协程退出之间有短暂窗口，等待退出后再删除文件。
            jobs.remove(modelId)?.join()
            val runtimeCache = File(directory, "$modelId.litertlm.cache")
            if (runtimeCache.exists() && !runtimeCache.deleteRecursively()) throw IOException("Unable to remove local model cache")
            // 删除失败保留 UI 状态，不能谎报文件已经移除。
            val target = file(modelId)
            if (target.exists() && !target.delete()) throw IOException("Unable to remove local model")
            marker(modelId).delete()
            update(modelId) { LocalModelState(it.variant) }
        }
    }

    override fun resolve(modelId: String): LocalModelRuntimeConfig? {
        val value = state.value.models.firstOrNull { it.variant.id == modelId } ?: return null
        if (value.status != LocalModelDownloadStatus.Ready || !isVerified(spec(modelId))) return null
        return LocalModelRuntimeConfig(file(modelId).absolutePath, value.variant.backend)
    }

    private fun spec(id: String) = specs.firstOrNull { it.variant.id == id } ?: error("Unknown local model")
    private fun model(id: String) = state.value.models.first { it.variant.id == id }
    private fun file(id: String) = File(directory, "$id.litertlm")
    private fun marker(id: String) = File(directory, "$id.verified")
    private fun isVerified(spec: LocalModelDownloadSpec): Boolean = try {
        file(spec.variant.id).let { it.isFile && it.length() == spec.variant.bytes } &&
            marker(spec.variant.id).let { it.isFile && it.length() == 64L && it.readText() == spec.sha256 }
    } catch (_: IOException) { false }

    private fun update(id: String, transform: (LocalModelState) -> LocalModelState) {
        mutableState.update { state -> state.copy(models = state.models.map { if (it.variant.id == id) transform(it) else it }) }
    }
}
