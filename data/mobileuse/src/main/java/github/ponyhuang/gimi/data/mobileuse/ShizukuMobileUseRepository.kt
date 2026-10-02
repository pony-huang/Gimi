package github.ponyhuang.gimi.data.mobileuse

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.DeadObjectException
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import github.ponyhuang.gimi.domain.mobileuse.MobileObservation
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
    private val textRecognizer: MobileScreenshotTextRecognizer,
    private val preferences: MobileUsePreferences,
) : MobileUseRepository {
    override val enabled: StateFlow<Boolean> = preferences.enabled
    private val mutex = Mutex()
    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellMobileUseService::class.java.name),
        ).tag("gimi.mobileuse.shell").version(3).daemon(false).processNameSuffix("mobileuse")
    }
    private val guard = MobileObservationGuard()
    @Volatile private var owner: String? = null
    @Volatile private var displayId: Int? = null
    @Volatile private var service: IMobileUseService? = null
    @Volatile private var connection: ServiceConnection? = null

    override suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // 与操作使用同一把锁，关闭完成后任何排队操作都不能重新创建后台画面。
            preferences.setEnabled(enabled)
            if (!enabled) closeLocked()
        }
    }

    override fun availability(): MobileUseAvailability {
        if (!enabled.value) return MobileUseAvailability.DISABLED
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

    override suspend fun observe(owner: String): MobileUseResult = perform(owner) { remote, id, delivery ->
        observeResult(remote, id, owner, delivery, "observed", "Display observed.")
    }

    override suspend fun launch(owner: String, packageName: String): MobileUseResult =
        perform(owner, action = true) { remote, id, delivery ->
            val launcher = context.packageManager.getLaunchIntentForPackage(packageName)?.component
                ?: return@perform invalid("app_unavailable", "No launchable activity for package $packageName.")
            attempt(delivery) { remote.launch(launcher.flattenToString(), id); true }
            observeResult(remote, id, owner, delivery, "launched", "App launched; inspect the observation.")
        }

    override suspend fun click(owner: String, observationId: String, elementId: String): MobileUseResult =
        onObserved(owner, observationId, elementId = elementId) { remote, id, screen, delivery ->
            val element = screen.elements.firstOrNull { it.id == elementId }
                ?: return@onObserved invalid("invalid_argument", "Unknown elementId in this observation.")
            val accepted = when {
                element.source == "ocr" && "tap" in element.actions ->
                    attempt(delivery) {
                        remote.tap(id, element.bounds.centerX, element.bounds.centerY); true
                    }
                "click" in element.actions -> {
                    val target = screen.native.targets[elementId]
                        ?: return@onObserved invalid("stale_observation", "Node no longer available; observe again.")
                    val accessibility = MobileTextAccessibilityService.current()
                        ?: return@onObserved invalid("accessibility_required", "Accessibility service disconnected.")
                    attempt(delivery) {
                        accessibility.performNodeAction(id, screen.frame.width, screen.frame.height, target, "click")
                    }
                }
                else -> return@onObserved invalid("unsupported_action", "Element does not support click.")
            }
            observeResult(remote, id, owner, delivery, if (accepted) "clicked" else "action_rejected",
                if (accepted) "Action sent once. OCR targets use a text-box center, not a verified button."
                else "Node action rejected; observe again before choosing another action.")
        }

    override suspend fun setText(owner: String, observationId: String, elementId: String, text: String): MobileUseResult =
        onObserved(owner, observationId, elementId = elementId) { remote, id, screen, delivery ->
            val target = screen.native.targets[elementId]?.takeIf { "set_text" in it.element.actions }
                ?: return@onObserved invalid("text_target_unavailable", "Element does not support native text replacement.")
            val accessibility = MobileTextAccessibilityService.current()
                ?: return@onObserved invalid("accessibility_required", "Accessibility service disconnected.")
            val accepted = attempt(delivery) {
                accessibility.performNodeAction(id, screen.frame.width, screen.frame.height, target, "set_text", text)
            }
            observeResult(remote, id, owner, delivery, if (accepted) "text_set" else "action_rejected",
                if (accepted) "Text replaced." else "Text replacement rejected; no automatic action retry.")
        }

    override suspend fun tap(owner: String, observationId: String, x: Int, y: Int): MobileUseResult =
        onObserved(owner, observationId) { remote, id, screen, delivery ->
            if (!inside(screen.frame, x, y)) return@onObserved invalid("invalid_argument", "Point outside screenshot.")
            attempt(delivery) { remote.tap(id, x, y); true }
            observeResult(remote, id, owner, delivery, "tapped", "Tap sent once.")
        }

    override suspend fun tapRelative(owner: String, observationId: String, xPermille: Int, yPermille: Int): MobileUseResult =
        onObserved(owner, observationId) { remote, id, screen, delivery ->
            if (xPermille !in 0..1000 || yPermille !in 0..1000) {
                return@onObserved invalid("invalid_argument", "Relative coordinates must be 0..1000.")
            }
            val geometry = MobileDisplayGeometry(screen.frame.width, screen.frame.height, 1)
            attempt(delivery) {
                remote.tap(id, geometry.xAtPermille(xPermille), geometry.yAtPermille(yPermille)); true
            }
            observeResult(remote, id, owner, delivery, "tapped", "Relative tap sent once.")
        }

    override suspend fun swipe(
        owner: String, observationId: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int,
    ): MobileUseResult = onObserved(owner, observationId) { remote, id, screen, delivery ->
        if (!inside(screen.frame, x1, y1) || !inside(screen.frame, x2, y2) || durationMs !in 100..5000) {
            return@onObserved invalid("invalid_argument", "Invalid swipe coordinates or duration.")
        }
        attempt(delivery) { remote.swipe(id, x1, y1, x2, y2, durationMs); true }
        observeResult(remote, id, owner, delivery, "swiped", "Swipe sent once.")
    }

    override suspend fun back(owner: String, observationId: String): MobileUseResult =
        onObserved(owner, observationId, navigation = true) { remote, id, _, delivery ->
            attempt(delivery) { remote.back(id); true }
            observeResult(remote, id, owner, delivery, "back", "Back sent once.")
        }

    override suspend fun typeText(owner: String, observationId: String, x: Int, y: Int, text: String): MobileUseResult =
        onObserved(owner, observationId) { remote, id, screen, delivery ->
            if (!inside(screen.frame, x, y)) return@onObserved invalid("invalid_argument", "Point outside screenshot.")
            val accessibility = MobileTextAccessibilityService.current()
                ?: return@onObserved invalid("accessibility_required",
                    "Enable Gimi Background App Control · Text Input in Accessibility settings.")
            fun replace() = attempt(delivery) {
                accessibility.replaceText(id, screen.frame.width, screen.frame.height, x, y, text)
            }
            var replaced = replace()
            if (!replaced) {
                // 只在明确拒绝写入时允许一次获焦；异常时结果未知，不能继续补发动作。
                attempt(delivery) { remote.tap(id, x, y); true }
                delay(200)
                replaced = replace()
            }
            observeResult(remote, id, owner, delivery,
                if (replaced) "text_set" else "text_target_unavailable",
                if (replaced) "Text replaced." else "Focus tap sent, but no editable node accepted text.")
        }

    override suspend fun stop(owner: String): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (this@ShizukuMobileUseRepository.owner != owner) {
                return@withLock invalid("not_active", "This Agent task does not own a display.")
            }
            closeLocked()
            MobileUseResult("stopped", "Display stopped.", actionStatus = "delivered")
        }
    }

    private suspend fun onObserved(
        owner: String, observationId: String, elementId: String? = null, navigation: Boolean = false,
        operation: suspend (IMobileUseService, Int, MobileObservedScreen, MobileActionDelivery) -> MobileUseResult,
    ): MobileUseResult = perform(owner, action = true, allowCreate = false) { remote, id, delivery ->
        val reading = readFrame(remote)
        if (reading.error != null) return@perform invalid("stale_observation", "Capture error; observe again before acting.")
        val frame = reading.frame
        val native = nativeSnapshot(id, frame)
        val nativeElementId = elementId?.takeIf { guard.latest?.native?.targets?.containsKey(it) == true }
        val rejection = guard.rejection(owner, id, observationId, now(), frame, native, nativeElementId, navigation)
        if (rejection != null) {
            guard.clear()
            return@perform invalid("stale_observation", "$rejection; call mobile_observe before acting.")
        }
        operation(remote, id, checkNotNull(guard.latest), delivery)
    }

    private suspend fun perform(
        owner: String, action: Boolean = false, allowCreate: Boolean = true,
        operation: suspend (IMobileUseService, Int, MobileActionDelivery) -> MobileUseResult,
    ): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val delivery = MobileActionDelivery(action)
            if (!enabled.value) {
                return@withLock unavailable(MobileUseAvailability.DISABLED).copy(actionStatus = delivery.status)
            }
            if (this@ShizukuMobileUseRepository.owner != null && this@ShizukuMobileUseRepository.owner != owner) {
                return@withLock invalid("busy", "Another Agent task owns the display.")
            }
            if (!allowCreate && (displayId == null || service == null)) {
                return@withLock invalid("stale_observation", "Display session ended; observe again.")
            }
            val available = availability()
            if (available != MobileUseAvailability.READY &&
                !(available == MobileUseAvailability.BUSY && this@ShizukuMobileUseRepository.owner == owner)
            ) return@withLock unavailable(available).copy(actionStatus = delivery.status)
            try {
                val remote = service ?: bind().also { service = it }
                if (remote.uid() != 2000) {
                    closeLocked()
                    return@withLock invalid("root_unsupported", "Only Shizuku ADB shell mode is supported.")
                }
                val id = displayId ?: remote.createDisplay().also {
                    displayId = it
                    Log.i("GimiMobileUse", "Display session created: $it")
                }
                this@ShizukuMobileUseRepository.owner = owner
                operation(remote, id, delivery).copy(displayId = id, actionStatus = delivery.status)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: DeadObjectException) {
                Log.w("GimiMobileUse", "Display session lost; actionStatus=${delivery.status}", failure)
                closeLocked()
                MobileUseResult("session_lost", "Service disconnected. Action may have executed; observe, do not repeat it.",
                    actionStatus = delivery.status)
            } catch (failure: RemoteException) {
                operationFailure(failure, delivery)
            } catch (failure: RuntimeException) {
                operationFailure(failure, delivery)
            }
        }
    }

    private fun operationFailure(failure: Exception, delivery: MobileActionDelivery): MobileUseResult {
        guard.clear()
        if (displayId == null) {
            closeLocked()
            return MobileUseResult("unsupported", "Unable to create a background display; check device support.", actionStatus = delivery.status)
        }
        Log.w("GimiMobileUse", "Operation failed; display retained; actionStatus=${delivery.status}", failure)
        return MobileUseResult("operation_failed",
            "Operation or observation failed. Call mobile_observe; do not automatically repeat an action.",
            displayId = displayId, actionStatus = delivery.status)
    }

    private suspend fun observeResult(
        remote: IMobileUseService, id: Int, owner: String, delivery: MobileActionDelivery,
        status: String, message: String,
    ): MobileUseResult {
        guard.clear()
        val startedAt = now()
        // 动作后的新帧要求跨 observe 调用保留；超时后的旧缓存不能在下一次观察中重新变成 settled。
        val stability = MobileFrameStability(startedAt, guard.requiredFrameAfterMs)
        var frame: CapturedMobileFrame? = null
        var native = MobileAccessibilitySnapshot("not_captured")
        var state = "timeout"
        var reason = "no_frame"
        var captureError: String? = null
        while (now() - startedAt < 6_000) {
            val reading = readFrame(remote)
            frame = reading.frame
            captureError = reading.error
            val accessibility = MobileTextAccessibilityService.current()
            if (reading.error == null && stability.update(frame, now(), accessibility?.lastChangeMs(id) ?: 0)) {
                native = nativeSnapshot(id, frame)
                val checked = readFrame(remote)
                if (native.consistent && checked.error == null && frame != null && checked.frame != null &&
                    frame.jpeg.contentEquals(checked.frame.jpeg) && frame.generation == checked.frame.generation &&
                    (accessibility?.lastChangeMs(id) ?: 0) <= native.lastChangeMs
                ) {
                    frame = checked.frame
                    state = "settled"
                    reason = "quiet_pixels_and_target_window_events"
                    break
                }
            }
            reason = if (captureError == null) stability.timeoutReason() else "capture_error"
            delay(if (frame == null) 300 else 150)
        }
        val image = frame
        var elements = if (state == "settled") native.targets.values.map { it.element } else emptyList()
        var nodesStatus = if (state == "settled") native.status else "not_captured"
        var truncated = native.truncated
        if (state == "settled" && image != null && elements.count { it.text != null || it.description != null } < 4) {
            val ocr = textRecognizer.recognize(image)
            val checked = readFrame(remote)
            captureError = checked.error
            val checkedNative = nativeSnapshot(id, checked.frame)
            // OCR 和节点遍历均非原子采集；采集后再次复核，变化时丢弃元素而不返回错位坐标。
            if (checked.error == null && checked.frame != null &&
                image.jpeg.contentEquals(checked.frame.jpeg) &&
                image.generation == checked.frame.generation && checkedNative == native
            ) {
                val additional = ocr.elements.filter { box ->
                    elements.none { it.text == box.text && it.bounds.contains(box.bounds.centerX, box.bounds.centerY) }
                }
                truncated = truncated || ocr.truncated || elements.size + additional.size > 200
                elements = (elements + additional).take(200)
                nodesStatus = "${native.status}+${ocr.status}"
                frame = checked.frame
            } else {
                state = "timeout"
                reason = "changed_during_element_capture"
                frame = checked.frame
                elements = emptyList()
                nodesStatus = "discarded_changed_elements"
            }
        }
        val observationId = UUID.randomUUID().toString()
        val observedAt = now()
        val pixelActionsAllowed = state == "settled"
        val nativeActionsAllowed = pixelActionsAllowed || reason == "changing" || reason == "changed_during_element_capture"
        if (state != "settled" && frame != null && captureError == null) {
            native = nativeSnapshot(id, frame)
            elements = if (native.consistent) native.targets.values.map { it.element } else emptyList()
            nodesStatus = native.status
            truncated = native.truncated
        }
        val actionable = frame != null && captureError == null && native.consistent
        if (actionable && frame != null) {
            guard.confirmFrame(frame)
            guard.record(MobileObservedScreen(observationId, owner, id, observedAt, frame, native, elements,
                pixelActionsAllowed, nativeActionsAllowed))
        }
        if (frame == null) state = "frame_unavailable"
        val observation = MobileObservation(
            observationId, state, reason, frame?.let { (observedAt - it.capturedAtMs).coerceAtLeast(0) },
            frame?.sequence, nodesStatus, elements, truncated,
            actionModes = if (!actionable) emptyList() else buildList {
                if (nativeActionsAllowed) add("native_elements")
                if (pixelActionsAllowed) add("pixel_coordinates")
                add("back")
            },
        )
        Log.i("GimiMobileUse", "Observation: display=$id state=$state reason=$reason action=${delivery.status} nodes=${elements.size}")
        return MobileUseResult(status,
            "$message Observation=$state ($reason). A quiet frame does not prove business loading is complete.",
            id, frame?.jpeg, frame?.width, frame?.height, delivery.status, observation)
    }

    private fun nativeSnapshot(id: Int, frame: CapturedMobileFrame?): MobileAccessibilitySnapshot =
        if (frame == null) MobileAccessibilitySnapshot("no_frame")
        else MobileTextAccessibilityService.current()?.snapshot(id, frame.width, frame.height)
            ?: MobileAccessibilitySnapshot("accessibility_unavailable")

    private fun readFrame(remote: IMobileUseService): MobileFrameReading {
        val payload = remote.capture()
        val jpeg = payload.getByteArray("jpeg")
        val samples = payload.getIntArray("samples")
        val frame = if (jpeg == null || samples == null) null else CapturedMobileFrame(
            payload.getInt("width"), payload.getInt("height"), payload.getLong("capturedAtMs"),
            payload.getLong("sequence"), payload.getLong("generation"), jpeg, samples,
        )
        return MobileFrameReading(frame, payload.getString("error"))
    }

    private fun inside(frame: CapturedMobileFrame, x: Int, y: Int): Boolean =
        x in 0 until frame.width && y in 0 until frame.height

    private fun attempt(delivery: MobileActionDelivery, send: () -> Boolean): Boolean {
        val previousRequirement = guard.requiredFrameAfterMs
        val accepted = delivery.attempt(::now, { guard.clear(); guard.awaitFrameAfter(now()) }, send)
        // 目标应用可能在 shell 命令退出前完成绘制，应等待发送开始后的帧，而不是命令返回后的帧。
        // 明确拒绝的动作不产生新绘制要求；未知异常则保留要求，供下一次 observe 继续检查。
        if (!accepted) guard.awaitFrameAfter(previousRequirement)
        return accepted
    }

    private fun invalid(status: String, message: String) = MobileUseResult(status, message, actionStatus = "not_sent")
    private fun now(): Long = SystemClock.elapsedRealtime()

    /** 一次采集读取的可用帧和独立错误码，缓存画面不能掩盖采集错误。 */
    private data class MobileFrameReading(val frame: CapturedMobileFrame?, val error: String?)

    private suspend fun bind(): IMobileUseService {
        val deferred = CompletableDeferred<IMobileUseService>()
        val candidate = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) return
                deferred.complete(IMobileUseService.Stub.asInterface(binder))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (connection !== this) return
                guard.reset()
                displayId?.let { MobileTextAccessibilityService.current()?.forgetDisplay(it) }
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
        } catch (failure: CancellationException) {
            closeLocked()
            throw failure
        } catch (failure: RuntimeException) {
            closeLocked()
            throw failure
        }
    }

    private fun closeLocked() {
        guard.reset()
        displayId?.let { MobileTextAccessibilityService.current()?.forgetDisplay(it) }
        runCatching { service?.stop() }
        if (connection != null && Shizuku.pingBinder()) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
        connection = null
        displayId = null
        owner = null
    }

    private fun unavailable(status: MobileUseAvailability): MobileUseResult = when (status) {
        MobileUseAvailability.DISABLED -> MobileUseResult(
            "disabled", "Background app control is off. Enable it in Gimi Settings > Shizuku before retrying.",
        )
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
