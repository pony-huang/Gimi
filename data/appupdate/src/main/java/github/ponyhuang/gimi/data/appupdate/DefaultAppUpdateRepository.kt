package github.ponyhuang.gimi.data.appupdate

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.data.appupdate.apk.ApkAssetSelector
import github.ponyhuang.gimi.data.appupdate.apk.ApkDownloadException
import github.ponyhuang.gimi.data.appupdate.apk.ApkDownloader
import github.ponyhuang.gimi.data.appupdate.apk.ApkInstaller
import github.ponyhuang.gimi.data.appupdate.notification.UpdateNotifier
import github.ponyhuang.gimi.data.appupdate.remote.GitHubReleaseGateway
import github.ponyhuang.gimi.data.appupdate.remote.RateLimitException
import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import github.ponyhuang.gimi.domain.appupdate.model.AppVersion
import github.ponyhuang.gimi.domain.appupdate.model.ApkAsset
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateCheckResult
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateFailure
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Singleton
class DefaultAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    fileDownloader: HttpFileDownloader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppUpdateRepository {

    private val gateway = GitHubReleaseGateway(okHttpClient)
    private val downloader = ApkDownloader(fileDownloader)
    private val installer = ApkInstaller(context)
    private val notifier = UpdateNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val updatesDir = File(context.cacheDir, "updates")

    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private var downloadJob: Job? = null
    private var currentVersionName: String = currentVersionName()
    private var lastAutoCheckAt = 0L
    /** 上次检查结果，供自动检查节流窗口内复用（与易变的 UI 状态解耦）。 */
    private var lastCheckOutcome: UpdateCheckResult? = null

    override suspend fun checkForUpdate(manual: Boolean): UpdateCheckResult {
        // 手动检查总是直接请求网络对比；仅自动检查走 6 小时节流。
        if (!manual) {
            val now = System.currentTimeMillis()
            val cached = lastCheckOutcome
            if (cached != null && now - lastAutoCheckAt < AUTO_CHECK_INTERVAL_MS) {
                restoreCachedState(cached)
                return cached
            }
            lastAutoCheckAt = now
        } else {
            mutableState.value = AppUpdateState.Checking
        }

        val result = runCatching { fetchUpdateInfo() }.fold(
            onSuccess = { info ->
                when {
                    info == null -> {
                        if (manual) mutableState.value = AppUpdateState.Idle
                        UpdateCheckResult.UpToDate
                    }
                    else -> {
                        mutableState.value = AppUpdateState.Available(info, currentVersionName)
                        UpdateCheckResult.UpdateAvailable(info)
                    }
                }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                val failure = when (error) {
                    is RateLimitException -> UpdateFailure.RateLimited
                    else -> UpdateFailure.Network
                }
                if (manual) mutableState.value = AppUpdateState.Failed(failure)
                UpdateCheckResult.Error(failure)
            },
        )
        lastCheckOutcome = result
        return result
    }

    /** 节流窗口内命中缓存时，把「有新版本」恢复到 state（用户可能已 reset 成 Idle）。 */
    private fun restoreCachedState(cached: UpdateCheckResult) {
        if (cached is UpdateCheckResult.UpdateAvailable &&
            mutableState.value is AppUpdateState.Idle
        ) {
            mutableState.value = AppUpdateState.Available(cached.info, currentVersionName)
        }
    }

    override fun startDownload(info: AppUpdateInfo) {
        val asset = ApkAssetSelector.select(info.assets, Build.SUPPORTED_ABIS.toList())
        if (asset == null) {
            mutableState.value = AppUpdateState.Failed(UpdateFailure.NoCompatibleApk)
            return
        }
        downloadJob?.cancel()
        updatesDir.listFiles()?.forEach { it.delete() }
        updatesDir.mkdirs()
        val dest = File(updatesDir, asset.name)
        mutableState.value = AppUpdateState.Downloading(info, 0f)
        notifier.showProgress(info, 0f, asset.sizeBytes)
        downloadJob = scope.launch {
            try {
                downloader.download(
                    url = asset.downloadUrl,
                    sizeHintBytes = asset.sizeBytes,
                    expectedSha256 = asset.sha256,
                    dest = dest,
                ) { progress ->
                    mutableState.value = AppUpdateState.Downloading(info, progress)
                    notifier.showProgress(info, progress, asset.sizeBytes)
                }
                val mismatch = !installer.hasSameSignature(dest)
                mutableState.value = AppUpdateState.Downloaded(info, dest.absolutePath, mismatch)
                notifier.showCompleted(info, dest)
            } catch (cancelled: CancellationException) {
                notifier.cancel()
                throw cancelled
            } catch (error: ApkDownloadException) {
                val failure = when (error.reason) {
                    ApkDownloadException.Reason.ChecksumMismatch -> UpdateFailure.ChecksumMismatch
                    ApkDownloadException.Reason.Network -> UpdateFailure.Network
                }
                mutableState.value = AppUpdateState.Failed(failure)
                notifier.showFailed(info)
            } catch (error: Exception) {
                mutableState.value = AppUpdateState.Failed(UpdateFailure.Network)
                notifier.showFailed(info)
            } finally {
                downloadJob = null
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        notifier.cancel()
        val current = mutableState.value
        if (current is AppUpdateState.Downloading) {
            mutableState.value = AppUpdateState.Available(current.info, currentVersionName)
        }
    }

    override fun reset() {
        when (mutableState.value) {
            is AppUpdateState.Available,
            is AppUpdateState.Failed,
            is AppUpdateState.Downloaded,
            -> mutableState.value = AppUpdateState.Idle
            else -> Unit
        }
    }

    /** null 表示已是最新（或远端版本号无法解析）。 */
    private suspend fun fetchUpdateInfo(): AppUpdateInfo? = withContext(ioDispatcher) {
        val release = gateway.fetchLatestRelease()
        if (release.draft) return@withContext null
        val remoteVersion = AppVersion.parse(release.tagName) ?: return@withContext null
        val current = AppVersion.parse(currentVersionName)
        if (current != null && remoteVersion <= current) return@withContext null
        AppUpdateInfo(
            version = remoteVersion,
            tagName = release.tagName,
            title = release.name ?: release.tagName,
            changelog = release.body.orEmpty(),
            assets = release.assets.orEmpty().map { asset ->
                ApkAsset(
                    name = asset.name,
                    downloadUrl = asset.downloadUrl,
                    sizeBytes = asset.size,
                    sha256 = asset.digest
                        ?.takeIf { it.startsWith("sha256:") }
                        ?.removePrefix("sha256:"),
                )
            },
            publishedAt = release.publishedAt,
        )
    }

    private fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
