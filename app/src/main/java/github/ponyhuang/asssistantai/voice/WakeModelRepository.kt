package github.ponyhuang.asssistantai.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.R
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelInfo
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelSource
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val installJobs = mutableMapOf<String, Job>()

    val states: StateFlow<Map<String, WakeModelState>> = _states.asStateFlow()

    fun modelPath(modelId: String): String? =
        installedDir(modelId).takeIf(::isValidModel)?.absolutePath

    fun install(modelId: String) {
        val info = WakeModelCatalog.byId(modelId) ?: return
        if (_states.value[modelId]?.status == WakeModelStatus.Ready) return
        if (installJobs[modelId]?.isActive == true) return
        installJobs[modelId] = scope.launch { installInternal(info) }
    }

    private fun installInternal(info: WakeModelInfo) {
        val archive = File(context.cacheDir, "${info.id}.archive")
        val extracting = File(rootDir, "${info.id}.extracting")
        runCatching {
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
            target.deleteRecursively()
            if (!extractedModel.renameTo(target)) {
                extractedModel.copyRecursively(target, overwrite = true)
            }
            check(isValidModel(target)) { getString(R.string.wake_model_files_incomplete) }
            updateState(info.id, WakeModelState(WakeModelStatus.Ready, 1f))
        }.onFailure { error ->
            updateState(
                info.id,
                WakeModelState(
                    WakeModelStatus.Error,
                    message = error.message ?: getString(R.string.wake_model_install_failed),
                ),
            )
        }
        archive.delete()
        extracting.deleteRecursively()
    }

    private fun copyBundledAsset(info: WakeModelInfo, source: WakeModelSource.Bundled, archive: File) {
        val message = getString(R.string.wake_model_reading_bundled)
        updateState(info.id, WakeModelState(WakeModelStatus.Downloading, 0f, message))
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(source.assetPath).use { input ->
            val total = input.available().toLong()
            FileOutputStream(archive).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
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

    private fun downloadArchive(info: WakeModelInfo, source: WakeModelSource.Downloadable, archive: File) {
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
        _states.value = _states.value + (modelId to state)
    }

    private fun getString(resId: Int): String = context.getString(resId)

    private fun installedDir(modelId: String): File = File(rootDir, modelId)

    private fun initialStates(): Map<String, WakeModelState> {
        migrateLegacyChineseModel()
        return WakeModelCatalog.models.associate { info ->
            val state = if (isValidModel(installedDir(info.id))) {
                WakeModelState(WakeModelStatus.Ready, 1f)
            } else {
                WakeModelState()
            }
            info.id to state
        }
    }

    /** 旧版本中文模型目录按模型文件命名，迁移到按模型 id 命名的目录。 */
    private fun migrateLegacyChineseModel() {
        val legacy = File(rootDir, LEGACY_CHINESE_MODEL_DIR)
        val target = installedDir(WakeModelCatalog.Chinese.id)
        if (legacy.isDirectory && !target.exists()) {
            if (!legacy.renameTo(target)) {
                legacy.copyRecursively(target, overwrite = true)
                legacy.deleteRecursively()
            }
        }
    }

    private fun unzipSafely(archive: File, destination: File) {
        destination.mkdirs()
        val destinationPath = destination.canonicalPath + File.separator
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name)
                require(output.canonicalPath.startsWith(destinationPath)) {
                    getString(R.string.wake_model_package_invalid)
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { zip.copyTo(it) }
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

    private companion object {
        const val LEGACY_CHINESE_MODEL_DIR = "vosk-model-small-cn-0.22"
    }
}
