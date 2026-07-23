package github.ponyhuang.asssistantai.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import okhttp3.Request

@Singleton
class WakeModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rootDir = File(context.filesDir, "voice/wake-model")
    private val installedDir = File(rootDir, MODEL_NAME)
    private val _state = MutableStateFlow(
        if (isValidModel(installedDir)) WakeModelState(WakeModelStatus.Ready, 1f)
        else WakeModelState(),
    )
    private var installJob: Job? = null

    val state: StateFlow<WakeModelState> = _state.asStateFlow()

    fun modelPath(): String? = installedDir.takeIf(::isValidModel)?.absolutePath

    fun install() {
        if (_state.value.status == WakeModelStatus.Ready || installJob?.isActive == true) return
        installJob = scope.launch {
            val archive = File(context.cacheDir, "$MODEL_NAME.download")
            val extracting = File(rootDir, "$MODEL_NAME.extracting")
            runCatching {
                rootDir.mkdirs()
                archive.delete()
                extracting.deleteRecursively()
                download(archive)
                _state.value = WakeModelState(WakeModelStatus.Extracting, 1f, "正在安装唤醒模型")
                unzipSafely(archive, extracting)
                val extractedModel = findModelRoot(extracting)
                    ?: error("下载包不包含有效的 Vosk 中文模型")
                installedDir.deleteRecursively()
                if (!extractedModel.renameTo(installedDir)) {
                    extractedModel.copyRecursively(installedDir, overwrite = true)
                }
                check(isValidModel(installedDir)) { "唤醒模型文件不完整" }
                _state.value = WakeModelState(WakeModelStatus.Ready, 1f)
            }.onFailure { error ->
                _state.value = WakeModelState(
                    WakeModelStatus.Error,
                    message = error.message ?: "唤醒模型下载失败",
                )
            }
            archive.delete()
            extracting.deleteRecursively()
        }
    }

    private fun download(archive: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        _state.value = WakeModelState(WakeModelStatus.Downloading, 0f, "正在下载中文唤醒模型")
        httpClient.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
            check(response.isSuccessful) { "唤醒模型下载失败：HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "唤醒模型下载失败：空响应体" }
            val total = body.contentLength()
            body.byteStream().use { input ->
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
                        _state.value = WakeModelState(
                            WakeModelStatus.Downloading,
                            progress.coerceIn(0f, 1f),
                            "正在下载中文唤醒模型",
                        )
                    }
                }
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        check(sha256 == MODEL_SHA256) { "唤醒模型校验失败" }
    }

    private fun unzipSafely(archive: File, destination: File) {
        destination.mkdirs()
        val destinationPath = destination.canonicalPath + File.separator
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name)
                require(output.canonicalPath.startsWith(destinationPath)) { "模型压缩包路径无效" }
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
        const val MODEL_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SHA256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba"
    }
}
