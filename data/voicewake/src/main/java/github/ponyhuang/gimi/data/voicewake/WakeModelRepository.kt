package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.WakeModelSource
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient

@Singleton
class WakeModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    private val downloader = WakeModelDownloader(okHttpClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rootDir = File(context.filesDir, "voice/wake-model")
    private val _states = MutableStateFlow(initialStates())
    private val installJobs = ConcurrentHashMap<String, Job>()

    val states: StateFlow<Map<String, WakeModelState>> = _states.asStateFlow()

    fun modelPath(modelId: String): String? =
        installedDir(modelId)
            .takeIf {
                _states.value[modelId]?.status == WakeModelStatus.Ready && isValidModel(it)
            }
            ?.absolutePath

    fun install(modelId: String) {
        val info = WakeModelCatalog.byId(modelId) ?: return
        if (_states.value[modelId]?.status == WakeModelStatus.Ready) return
        installJobs.compute(modelId) { _, existing ->
            existing?.also { it.cancel() }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    installInternal(info)
                } finally {
                    installJobs.remove(modelId, currentCoroutineContext().job)
                }
            }
            job.start()
            job
        }
    }

    fun cancelInstall(modelId: String) {
        installJobs.remove(modelId)?.cancel()
        updateState(modelId, WakeModelState())
    }

    private suspend fun installInternal(info: WakeModelInfo) {
        val archive = File(context.cacheDir, "${info.id}.archive")
        val extracting = File(rootDir, "${info.id}.extracting")
        try {
            rootDir.mkdirs()
            archive.delete()
            extracting.deleteRecursively()
            when (val source = info.source) {
                is WakeModelSource.Bundled -> copyBundledAsset(info, source, archive)
                is WakeModelSource.Downloadable -> downloadArchive(info, source, archive)
            }
            updateState(info.id, WakeModelState(WakeModelStatus.Extracting, 1f, getString(R.string.wake_model_installing)))
            unzipSafely(archive, extracting)
            val extractedModel = findModelRoot(extracting)
                ?: error(getString(R.string.wake_model_package_invalid))
            val target = installedDir(info.id)
            val backup = File(rootDir, "${info.id}.backup")
            backup.deleteRecursively()
            if (target.exists()) {
                check(target.renameTo(backup)) { getString(R.string.wake_model_install_failed) }
            }
            val installed = extractedModel.renameTo(target)
            if (!installed) {
                backup.renameTo(target)
                error(getString(R.string.wake_model_install_failed))
            }
            if (!isValidModel(target)) {
                target.deleteRecursively()
                backup.renameTo(target)
                error(getString(R.string.wake_model_files_incomplete))
            }
            backup.deleteRecursively()
            updateState(info.id, WakeModelState(WakeModelStatus.Ready, 1f))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateState(
                info.id,
                WakeModelState(
                    WakeModelStatus.Error,
                    message = error.message ?: getString(R.string.wake_model_install_failed),
                ),
            )
        } finally {
            archive.delete()
            extracting.deleteRecursively()
        }
    }

    private suspend fun copyBundledAsset(
        info: WakeModelInfo,
        source: WakeModelSource.Bundled,
        archive: File,
    ) {
        val message = getString(R.string.wake_model_reading_bundled)
        updateState(info.id, WakeModelState(WakeModelStatus.Downloading, 0f, message))
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(source.assetPath).use { input ->
            val total = input.available().toLong()
            FileOutputStream(archive).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    copied += read
                    val progress = if (total > 0) copied.toFloat() / total else 0f
                    updateState(
                        info.id,
                        WakeModelState(WakeModelStatus.Downloading, progress.coerceIn(0f, 1f), message),
                    )
                }
            }
        }
        verifyChecksum(info, digest)
    }

    private suspend fun downloadArchive(
        info: WakeModelInfo,
        source: WakeModelSource.Downloadable,
        archive: File,
    ) {
        val message = getString(R.string.wake_model_downloading)
        try {
            downloader.download(source.url, source.sizeBytes, info.sha256, archive) { progress ->
                updateState(info.id, WakeModelState(WakeModelStatus.Downloading, progress, message))
            }
        } catch (error: WakeModelDownloadException) {
            val messageRes = when (error.reason) {
                WakeModelDownloadException.Reason.ChecksumMismatch -> R.string.wake_model_checksum_failed
                WakeModelDownloadException.Reason.Network -> R.string.wake_model_download_failed
            }
            error(getString(messageRes))
        }
    }

    private fun verifyChecksum(info: WakeModelInfo, digest: MessageDigest) {
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        check(sha256 == info.sha256) { getString(R.string.wake_model_checksum_failed) }
    }

    private fun updateState(modelId: String, state: WakeModelState) {
        _states.update { it + (modelId to state) }
    }

    private fun getString(resId: Int): String = context.getString(resId)

    private fun installedDir(modelId: String): File = File(rootDir, modelId)

    private fun initialStates(): Map<String, WakeModelState> {
        return WakeModelCatalog.models.associate { info ->
            val state = if (isValidModel(installedDir(info.id))) {
                WakeModelState(WakeModelStatus.Ready, 1f)
            } else {
                WakeModelState()
            }
            info.id to state
        }
    }

    private suspend fun unzipSafely(archive: File, destination: File) {
        destination.mkdirs()
        val destinationPath = destination.canonicalPath + File.separator
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name)
                require(output.canonicalPath.startsWith(destinationPath)) {
                    getString(R.string.wake_model_package_invalid)
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun findModelRoot(directory: File): File? = sequenceOf(directory)
        .plus(directory.walkTopDown().maxDepth(2).filter(File::isDirectory))
        .firstOrNull(::isValidModel)

    private fun isValidModel(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/phones/word_boundary.int").isFile

}
