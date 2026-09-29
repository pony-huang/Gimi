package github.ponyhuang.gimi.data.mobileuse

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.milliseconds

/** 客户端只接受 shell UID；全局互斥锁使多个 Agent 会话不能共享同一副屏。 */
@Singleton
class ShizukuMobileUseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MobileUseRepository {
    private val mutex = Mutex()
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellMobileUseService::class.java.name),
    ).tag("gimi.mobileuse.shell").version(2).daemon(false).processNameSuffix("mobileuse")
    private var owner: String? = null
    private var displayId: Int? = null
    private var service: IMobileUseService? = null
    private var connection: ServiceConnection? = null

    override fun availability(): MobileUseAvailability {
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        if (!installed) return MobileUseAvailability.SHIZUKU_MISSING
        if (!Shizuku.pingBinder()) return MobileUseAvailability.SHIZUKU_STOPPED
        return try {
            when {
                Shizuku.getUid() != 2000 -> MobileUseAvailability.ROOT_UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    if (owner == null) MobileUseAvailability.READY else MobileUseAvailability.BUSY
                Shizuku.shouldShowRequestPermissionRationale() ->
                    MobileUseAvailability.PERMISSION_DENIED
                else -> MobileUseAvailability.PERMISSION_REQUIRED
            }
        } catch (_: RuntimeException) {
            MobileUseAvailability.SHIZUKU_STOPPED
        }
    }

    override fun requestPermission(requestCode: Int) {
        if (availability() == MobileUseAvailability.PERMISSION_REQUIRED) {
            Shizuku.requestPermission(requestCode)
        }
    }

    override fun textInputAvailable(): Boolean = MobileTextAccessibilityService.current() != null

    override suspend fun observe(owner: String): MobileUseResult = perform(owner) { remote, id ->
        resultWithCapture(remote, id, "ready", "Virtual display screenshot attached to the next model request.")
    }

    override suspend fun launch(owner: String, packageName: String): MobileUseResult = perform(owner) { remote, id ->
        val launcher = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.component ?: return@perform MobileUseResult(
                "app_unavailable", "No launchable activity for package $packageName.",
            )
        remote.launch(launcher.flattenToString(), id)
        resultWithCapture(remote, id, "launched", "App started on virtual display.")
    }

    override suspend fun tap(owner: String, x: Int, y: Int): MobileUseResult = perform(owner) { remote, id ->
        if (!insideDisplay(remote, id, x, y)) {
            return@perform MobileUseResult("invalid_argument", "Point outside virtual display.")
        }
        remote.tap(id, x, y)
        resultWithCapture(remote, id, "tapped", "Tap delivered to virtual display.")
    }

    override suspend fun swipe(
        owner: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int,
    ): MobileUseResult = perform(owner) { remote, id ->
        if (!insideDisplay(remote, id, x1, y1) || !insideDisplay(remote, id, x2, y2)) {
            return@perform MobileUseResult("invalid_argument", "Point outside virtual display.")
        }
        remote.swipe(id, x1, y1, x2, y2, durationMs)
        resultWithCapture(remote, id, "swiped", "Swipe delivered to virtual display.")
    }

    override suspend fun back(owner: String): MobileUseResult = perform(owner) { remote, id ->
        remote.back(id)
        resultWithCapture(remote, id, "back", "Back delivered to virtual display.")
    }

    override suspend fun typeText(owner: String, x: Int, y: Int, text: String): MobileUseResult =
        perform(owner) { remote, id ->
            if (!insideDisplay(remote, id, x, y)) {
                return@perform MobileUseResult("invalid_argument", "Point outside virtual display.")
            }
            val accessibility = MobileTextAccessibilityService.current()
                ?: return@perform MobileUseResult(
                    "accessibility_required", "Enable Gimi Background App Control · Text Input in Accessibility settings.",
                )
            if (!accessibility.replaceText(id, x, y, text)) {
                return@perform MobileUseResult(
                    "text_target_unavailable", "No editable secondary-display node supports direct text replacement at this point.",
                )
            }
            resultWithCapture(remote, id, "text_set", "Text replaced on secondary display.")
        }

    override suspend fun stop(owner: String): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (this@ShizukuMobileUseRepository.owner != owner) {
                return@withLock MobileUseResult("not_active", "This Agent task does not own a display.")
            }
            closeLocked()
            MobileUseResult("stopped", "Virtual display removed.")
        }
    }

    private suspend fun perform(
        owner: String,
        operation: (IMobileUseService, Int) -> MobileUseResult,
    ): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (this@ShizukuMobileUseRepository.owner != null &&
                this@ShizukuMobileUseRepository.owner != owner
            ) return@withLock MobileUseResult("busy", "Another Agent task owns the virtual display.")
            val available = availability()
            if (available != MobileUseAvailability.READY &&
                !(available == MobileUseAvailability.BUSY && this@ShizukuMobileUseRepository.owner == owner)
            ) return@withLock unavailable(available)
            try {
                val remote = service ?: bind().also { service = it }
                if (remote.uid() != 2000) {
                    closeLocked()
                    return@withLock MobileUseResult("root_unsupported", "Only Shizuku ADB shell mode is supported.")
                }
                val id = displayId ?: remote.createDisplay().also { displayId = it }
                this@ShizukuMobileUseRepository.owner = owner
                val result = operation(remote, id)
                val geometry = remote.geometry(id)
                result.copy(displayId = id, width = geometry[0], height = geometry[1])
            } catch (failure: Exception) {
                closeLocked()
                if (failure is CancellationException) throw failure
                MobileUseResult("unsupported", "Virtual display operation failed: ${failure.message}")
            }
        }
    }

    private fun resultWithCapture(
        remote: IMobileUseService, id: Int, status: String, message: String,
    ): MobileUseResult {
        Thread.sleep(450)
        val frame = MobileCaptureRecovery.capture(
            captureFrame = { remote.capture() },
            recoverSurface = { remote.recoverSurface(id) },
        ) ?: return MobileUseResult(
            "frame_unavailable", "No display frame yet; the app and virtual display remain active. Retry mobile_observe.",
        )
        return MobileUseResult(status, message, imageJpeg = frame)
    }

    private fun insideDisplay(remote: IMobileUseService, id: Int, x: Int, y: Int): Boolean {
        val dimensions = remote.geometry(id)
        return x in 0 until dimensions[0] && y in 0 until dimensions[1]
    }

    private suspend fun bind(): IMobileUseService {
        val deferred = CompletableDeferred<IMobileUseService>()
        val candidate = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                deferred.complete(IMobileUseService.Stub.asInterface(binder))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                displayId = null
                owner = null
                if (!deferred.isCompleted) deferred.completeExceptionally(
                    IllegalStateException("Shizuku UserService disconnected"),
                )
            }
        }
        connection = candidate
        try {
            Shizuku.bindUserService(args, candidate)
            return withTimeout(10_000.milliseconds) { deferred.await() }
        } catch (failure: Exception) {
            closeLocked()
            throw failure
        }
    }

    private fun closeLocked() {
        runCatching { service?.stop() }
        if (Shizuku.pingBinder()) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
        connection = null
        displayId = null
        owner = null
    }

    private fun unavailable(status: MobileUseAvailability): MobileUseResult = when (status) {
        MobileUseAvailability.SHIZUKU_MISSING -> MobileUseResult(
            "shizuku_missing", "Install Shizuku and start it with wireless debugging or ADB.",
        )
        MobileUseAvailability.SHIZUKU_STOPPED -> MobileUseResult(
            "shizuku_stopped", "Start Shizuku in ADB mode and retry.",
        )
        MobileUseAvailability.PERMISSION_REQUIRED -> MobileUseResult(
            "permission_required", "Open Gimi Settings > Shizuku and grant access, then retry.",
        )
        MobileUseAvailability.PERMISSION_DENIED -> MobileUseResult(
            "permission_denied", "Enable Gimi in Shizuku's authorized apps, then retry.",
        )
        MobileUseAvailability.ROOT_UNSUPPORTED -> MobileUseResult(
            "root_unsupported", "Restart Shizuku using ADB shell mode; root mode is unsupported.",
        )
        MobileUseAvailability.BUSY -> MobileUseResult(
            "busy", "Another Agent task owns the virtual display.",
        )
        else -> MobileUseResult("unsupported", "Virtual display is unsupported on this device.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MobileUseModule {
    @Binds
    abstract fun bindMobileUseRepository(
        implementation: ShizukuMobileUseRepository,
    ): MobileUseRepository
}
