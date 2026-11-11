package github.ponyhuang.gimi.data.mobileuse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.DeadObjectException
import android.os.RemoteException
import android.os.SystemClock
import android.view.Surface
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseController
import github.ponyhuang.gimi.domain.mobileuse.MobileApp
import github.ponyhuang.gimi.domain.mobileuse.MobileKey
import github.ponyhuang.gimi.domain.mobileuse.MobileScreenCoordinates
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import github.ponyhuang.gimi.domain.mobileuse.MobileShellResult
import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileObservation
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySessions
import github.ponyhuang.gimi.domain.mobileuse.MobileExecutionAccess
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.milliseconds

/** 客户端只接受 shell UID；全局互斥锁使多个 Agent 会话不能共享同一副屏。 */
@Singleton
class ShizukuMobileUseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MobileUsePreferences,
) : MobileUseRepository, MobileUseController, MobileDisplayPreviewGateway {
    override val enabled: StateFlow<Boolean> = preferences.enabled
    override val smallWindowEnabled: StateFlow<Boolean> = preferences.smallWindow
    private val sessions = MobileDisplaySessions()
    override val displaySession: StateFlow<MobileDisplaySession?> = sessions.session
    private val mutex = Mutex()
    private val inputMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellMobileUseService::class.java.name),
        ).tag("gimi.mobileuse.shell").version(11).daemon(false).processNameSuffix("mobileuse")
    }
    private val guard = MobileObservationGuard()
    // 连续 text_focus_unavailable 次数；仅在互斥锁内变更。到 2 时确定性硬停，不依赖模型自觉。
    @Volatile private var consecutiveTextFocusUnavailable = 0
    @Volatile private var physicalSessionId: String? = null
    @Volatile private var displayId: Int? = null
    @Volatile private var service: IMobileUseService? = null
    @Volatile private var connection: ServiceConnection? = null

    init {
        // 本循环只做断连探测：UserService 可能在 Shizuku 主进程退出后继续存活，
        // 不能只依赖它的断连回调；几何刷新已移除，尺寸只在建屏和显式旋转时更新，
        // 不能让预览或异常远端尺寸改写坐标基准。
        scope.launch {
            while (true) {
                delay(400)
                val current = displaySession.value ?: continue
                if (!Shizuku.pingBinder()) {
                    mutex.withLock {
                        if (displaySession.value?.id == current.id && !Shizuku.pingBinder()) closeLocked()
                    }
                }
            }
        }
    }

    override suspend fun registerExecution(owner: String, chatId: String) { sessions.register(owner, chatId) }
    override suspend fun setSmallWindowEnabled(enabled: Boolean) { preferences.setSmallWindow(enabled) }

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
                    if (displaySession.value == null) MobileUseAvailability.READY else MobileUseAvailability.BUSY
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

    override suspend fun takeScreenshot(owner: String): MobileUseResult = perform(owner) { remote, id, delivery ->
        observeResult(remote, id, owner, delivery, "observed", "Screen observed.")
    }

    override suspend fun openApp(owner: String, appName: String): MobileUseResult =
        perform(owner, action = true) { remote, id, delivery ->
            val apps = launchableApps()
            val requested = appName.trim()
            if (requested.isEmpty()) return@perform invalid("invalid_argument", "app_name must not be blank.")
            val candidates = apps.filter { it.packageName == requested }.ifEmpty {
                apps.filter { it.name.equals(requested, ignoreCase = true) }
            }
            if (candidates.size != 1) return@perform MobileUseResult(
                if (candidates.isEmpty()) "app_unavailable" else "ambiguous_app",
                "Use list_apps and supply an exact name or package name; ambiguous names are not opened.",
                apps = candidates,
            )
            val app = candidates.single()
            val launcher = context.packageManager.getLaunchIntentForPackage(app.packageName)?.component
                ?: return@perform invalid("app_unavailable", "No launchable activity for ${app.packageName}.")
            var launchResult = MobileAppLaunchPolicy.START_REJECTED
            val accepted = inputMutex.withLock {
                attempt(delivery) {
                    launchResult = remote.launch(launcher.flattenToString(), id)
                    launchResult == MobileAppLaunchPolicy.STARTED
                }
            }
            if (!accepted) return@perform invalid(
                when (launchResult) {
                    MobileAppLaunchPolicy.MAIN_SCREEN_BUSY -> "app_in_use_on_main"
                    MobileAppLaunchPolicy.OTHER_DISPLAY_BUSY -> "app_in_use_on_other_display"
                    MobileAppLaunchPolicy.INSPECTION_UNAVAILABLE -> "app_state_unavailable"
                    else -> "app_launch_rejected"
                }, "App was not launched. Main-screen and other-display tasks are left with their current owner.",
            )
            displaySession.value?.let { current -> sessions.update(current.id, current.width, current.height, app.name) }
            // 换了应用等于换了屏幕，上一屏的输入连败不再累计。
            consecutiveTextFocusUnavailable = 0
            observeResult(remote, id, owner, delivery, "launched", "App launched; inspect the screenshot.")
        }

    override suspend fun listApps(owner: String): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            executionRejection(owner)?.let { return@withLock it }
            try {
                MobileUseResult("apps_listed", "Launchable apps on this device.", apps = launchableApps())
            } catch (_: RuntimeException) {
                invalid("app_query_failed", "Unable to read launchable apps.")
            }
        }
    }

    override suspend fun wait(owner: String, seconds: Int): MobileUseResult {
        mutex.withLock { executionRejection(owner) }?.let { return it }
        if (seconds !in 1..60) return invalid("invalid_argument", "seconds must be 1..60.")
        delay(seconds * 1_000L)
        return mutex.withLock {
            // 醒来后除归属/占用复查外，还要求显示会话仍存活（与 perform 的守卫一致），
            // 避免用户在等待期间关闭副屏后 Agent 仍按 waited 继续发动作。
            executionRejection(owner) ?: run {
                if (physicalSessionId != null && displaySession.value == null) {
                    invalid("session_closed", "Background app is closing.")
                } else {
                    MobileUseResult("waited", "Waited ${seconds}s; take_screenshot to inspect progress.")
                }
            }
        }
    }

    override suspend fun click(owner: String, x: Int, y: Int): MobileUseResult =
        coordinateAction(owner, x, y) { remote, id, px, py, _, delivery ->
            val accepted = inputMutex.withLock { attempt(delivery) { remote.tap(id, px, py) } }
            observeResult(remote, id, owner, delivery, if (accepted) "clicked" else "action_rejected", "Click attempted once.")
        }

    override suspend fun dragAndDrop(
        owner: String, startX: Int, startY: Int, endX: Int, endY: Int, holdMs: Int,
    ): MobileUseResult = coordinateAction(owner, startX, startY, swipe = holdMs == 0) { remote, id, px, py, screen, delivery ->
        if (endX !in 0..999 || endY !in 0..999 || holdMs !in 0..5_000) {
            return@coordinateAction invalid("invalid_argument", "Coordinates must be 0..999; hold_ms must be 0..5000.")
        }
        val accepted = inputMutex.withLock {
            attempt(delivery) {
                remote.gesture(id, px, py, MobileScreenCoordinates.pixel(endX, screen.frame.width),
                    MobileScreenCoordinates.pixel(endY, screen.frame.height), holdMs, 450)
            }
        }
        observeResult(remote, id, owner, delivery, if (accepted) "dragged" else "action_rejected", "Gesture attempted once.")
    }

    override suspend fun longPress(owner: String, x: Int, y: Int, seconds: Int): MobileUseResult =
        coordinateAction(owner, x, y) { remote, id, px, py, _, delivery ->
            if (seconds !in 1..5) return@coordinateAction invalid("invalid_argument", "seconds must be 1..5.")
            val accepted = inputMutex.withLock { attempt(delivery) { remote.gesture(id, px, py, px, py, seconds * 1_000, 0) } }
            observeResult(remote, id, owner, delivery, if (accepted) "long_pressed" else "action_rejected", "Long press attempted once.")
        }

    override suspend fun goBack(owner: String): MobileUseResult = pressKey(owner, MobileKey.BACK)

    override suspend fun runShell(owner: String, command: String): MobileUseResult =
        perform(owner, action = true, allowCreate = false) { remote, id, delivery ->
            if (command.isBlank() || command.length > 4_000 || '\u0000' in command) {
                return@perform invalid("invalid_argument", "command must contain 1..4000 characters without NUL.")
            }
            // input 默认写主屏且对非 ASCII 载荷抛 NPE：定向与校验必须在命令进入 shell 前完成。
            val prepared = prepareShellInput(command, id) ?: return@perform invalid(
                "invalid_argument",
                "input text accepts ASCII only and cannot enter Chinese: use mobile_type for non-ASCII text, or type a short ASCII prefix and tap the app's own suggestion.",
            )
            var shell: MobileShellResult? = null
            inputMutex.withLock {
                attempt(delivery) {
                    val payload = remote.runShell(id, prepared)
                    shell = MobileShellResult(
                        if (payload.containsKey("exitCode")) payload.getInt("exitCode") else null,
                        payload.getString("output").orEmpty(), payload.getBoolean("timedOut"),
                        payload.getBoolean("outputTruncated"),
                    )
                    // 命令非零退出或超时也可能已改变界面，不能当作未投递。
                    true
                }
            }
            val outcome = checkNotNull(shell)
            val status = when {
                outcome.timedOut -> "shell_timeout"
                outcome.exitCode == 0 -> "shell_completed"
                else -> "shell_failed"
            }
            // 命令结果不能因随后截图失败而丢失，否则模型可能重复执行有副作用的命令。
            val observed = try {
                observeResult(remote, id, owner, delivery, status, "Shell command attempted once; inspect output and screen before continuing.")
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: RemoteException) {
                MobileUseResult(status, "Shell returned; screenshot unavailable. Do not automatically repeat the command.")
            } catch (_: RuntimeException) {
                MobileUseResult(status, "Shell returned; screenshot unavailable. Do not automatically repeat the command.")
            }
            observed.copy(shellResult = outcome)
        }

    /** 匹配以 input 开头的命令；input 的显示参数必须紧跟在 input 之后，不能落在子命令后面。 */
    private val shellInputStart = Regex("""^\s*input\b""")
    private val shellInputWithDisplay = Regex("""^\s*input\s+(?:-d|--display)\b""")
    private val shellInputText = Regex("""^\s*input(?:\s+(?:-d|--display)\s+\S+)?\s+text\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * input 命令不带显示参数时默认作用于主屏：自动补上受控副屏的 -d，避免静默写错屏幕。
     * input text 的非 ASCII 载荷会让平台命令抛 NPE，必须提前拒绝并指路 mobile_type；返回 null 表示拒绝。
     */
    private fun prepareShellInput(command: String, displayId: Int): String? {
        val payload = shellInputText.find(command)?.groupValues?.get(1)
        if (payload != null && payload.any { it.code !in 0x20..0x7E }) return null
        return when {
            shellInputWithDisplay.containsMatchIn(command) -> command
            // 替换串不带尾随空格：原命令 input 后的空白原样保留，避免拼出双空格。
            shellInputStart.containsMatchIn(command) -> shellInputStart.replaceFirst(command, "input -d $displayId")
            else -> command
        }
    }

    override suspend fun pressKey(owner: String, key: MobileKey): MobileUseResult =
        onObserved(owner, navigation = key == MobileKey.BACK) { remote, id, _, delivery ->
            val accepted = inputMutex.withLock { attempt(delivery) { remote.pressKey(id, key.name) } }
            observeResult(remote, id, owner, delivery, if (accepted) "key_pressed" else "action_rejected", "${key.name} attempted once.")
        }

    override suspend fun type(owner: String, text: String, pressEnter: Boolean): MobileUseResult =
        onObserved(owner, textInput = true) { remote, id, screen, delivery ->
            // 走到这里说明焦点复核已通过，连败计数属于旧屏幕，作废。
            consecutiveTextFocusUnavailable = 0
            val target = checkNotNull(screen.native.focusedTextTarget())
            val accessibility = MobileTextAccessibilityService.current()
                ?: return@onObserved invalid("accessibility_required", "Enable Gimi Text Input in Accessibility settings.")
            var outcome = MobileTextInputOutcome.REJECTED
            var enterAccepted: Boolean? = null
            inputMutex.withLock {
                attempt(delivery) {
                    outcome = if (accessibility.requiresPaste(id, target)) {
                        accessibility.pasteText(id, text, { remote.preparePaste(id, text) }, { remote.restoreClipboard() }, target)
                    } else accessibility.insertText(id, text, target)
                    outcome != MobileTextInputOutcome.REJECTED
                }
                // 只有文本和光标均明确成功才发送 Enter；失败或结果未知时绝不补发提交。
                if (outcome == MobileTextInputOutcome.INSERTED && pressEnter) {
                    enterAccepted = attempt(delivery) { remote.pressKey(id, MobileKey.ENTER.name) }
                }
            }
            observeResult(remote, id, owner, delivery,
                when (outcome) {
                    MobileTextInputOutcome.INSERTED if enterAccepted == false -> "text_typed_enter_rejected"
                    MobileTextInputOutcome.INSERTED -> "text_typed"
                    MobileTextInputOutcome.CURSOR_UNAVAILABLE -> "text_inserted_cursor_unavailable"
                    MobileTextInputOutcome.REJECTED -> "text_target_unavailable"
                    MobileTextInputOutcome.UNCONFIRMED -> "text_paste_unconfirmed"
                },
                when (outcome) {
                    MobileTextInputOutcome.INSERTED if enterAccepted == false ->
                        "Text inserted, but Enter was rejected; inspect before continuing and do not repeat the text."
                    MobileTextInputOutcome.INSERTED -> "Text inserted at the focused selection."
                    MobileTextInputOutcome.CURSOR_UNAVAILABLE -> "Text inserted but cursor restoration failed; Enter was not sent. Do not repeat text."
                    MobileTextInputOutcome.REJECTED -> "No confirmed focused editable target accepted text; no tap or retry was sent."
                    MobileTextInputOutcome.UNCONFIRMED -> "Paste was sent once but text could not be confirmed; Enter was not sent. Inspect and do not repeat text."
                })
        }

    private suspend fun coordinateAction(
        owner: String, x: Int, y: Int, swipe: Boolean = false,
        operation: suspend (IMobileUseService, Int, Int, Int, MobileObservedScreen, MobileActionDelivery) -> MobileUseResult,
    ): MobileUseResult = onObserved(owner, coordinateBounds = { screen ->
        if (x !in 0..999 || y !in 0..999) null else tapBounds(
            MobileScreenCoordinates.pixel(x, screen.frame.width), MobileScreenCoordinates.pixel(y, screen.frame.height))
    }, swipe = swipe) { remote, id, screen, delivery ->
        if (x !in 0..999 || y !in 0..999) return@onObserved invalid("invalid_argument", "Coordinates must be 0..999.")
        operation(remote, id, MobileScreenCoordinates.pixel(x, screen.frame.width),
            MobileScreenCoordinates.pixel(y, screen.frame.height), screen, delivery)
    }

    private fun launchableApps(): List<MobileApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.ResolveInfoFlags.of(0),
    ).map { info -> MobileApp(info.loadLabel(context.packageManager).toString(), info.activityInfo.packageName) }
        .distinctBy(MobileApp::packageName)
        .sortedWith(compareBy<MobileApp, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.packageName })

    override suspend fun finishExecution(owner: String): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!sessions.finish(owner)) {
                return@withLock invalid("not_active", "This execution no longer owns the display.")
            }
            guard.reset()
            consecutiveTextFocusUnavailable = 0
            MobileUseResult("execution_finished", "Execution released. Background app and preview windows remain available for this chat.", displayId,
                actionStatus = MobileUseResult.ACTION_DELIVERED)
        }
    }

    override suspend fun stopExecution(owner: String): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (displaySession.value == null) return@withLock invalid("not_active", "Display session already closed.")
            // 在同一临界区验证执行归属并关闭，不能读取会话 ID 后跨锁误关其他轮次的副屏。
            when (sessions.claim(owner)) {
                MobileExecutionAccess.ALLOWED -> Unit
                MobileExecutionAccess.CLOSED -> return@withLock invalid("session_closed", "This execution's display was closed.")
                MobileExecutionAccess.UNREGISTERED -> return@withLock invalid("execution_finished", "This execution ended; it cannot close the display.")
                MobileExecutionAccess.OTHER_CHAT -> return@withLock invalid("busy", "Another chat has an open background app.")
                MobileExecutionAccess.BUSY -> return@withLock invalid("busy", "Another execution is using the display.")
            }
            inputMutex.withLock { closeLocked() }
            MobileUseResult("closed", "Background display and preview windows closed.", actionStatus = MobileUseResult.ACTION_DELIVERED)
        }
    }

    override suspend fun closeSession(sessionId: String): MobileUseResult = withContext(Dispatchers.IO) {
        if (!sessions.close(sessionId)) return@withContext invalid("not_active", "Display session already closed.")
        mutex.withLock {
            if (physicalSessionId == sessionId) inputMutex.withLock { closeLocked() }
        }
        MobileUseResult("closed", "Background app closed.", actionStatus = MobileUseResult.ACTION_DELIVERED)
    }

    override suspend fun manualTouch(sessionId: String, touch: MobileTouch): MobileUseResult = manual(sessionId) { remote, id ->
        if (remote.touch(id, touch.action.ordinal, touch.x, touch.y, touch.gestureId, touch.eventTimeMs)) {
            MobileUseResult("delivered", "", actionStatus = MobileUseResult.ACTION_DELIVERED)
        } else invalid(if (touch.action == MobileTouchAction.DOWN) "input_unavailable" else "input_rejected", "Touch was not accepted.")
    }

    override suspend fun manualBack(sessionId: String): MobileUseResult = manual(sessionId) { remote, id ->
        if (remote.back(id)) MobileUseResult("delivered", "", actionStatus = MobileUseResult.ACTION_DELIVERED)
        else invalid("input_rejected", "Back was not accepted.")
    }

    override suspend fun manualRotate(sessionId: String): MobileUseResult {
        val result = manual(sessionId) { remote, id ->
            val geometry = remote.rotateDisplay(id)
            require(geometry.size >= 2 && geometry[0] > 0 && geometry[1] > 0)
            sessions.update(sessionId, geometry[0], geometry[1])
            MobileUseResult("delivered", "", displayId = id, width = geometry[0], height = geometry[1], actionStatus = MobileUseResult.ACTION_DELIVERED)
        }
        return if (result.status == "input_unavailable") invalid("rotation_unavailable", "Display orientation could not be changed.") else result
    }

    override suspend fun attachPreview(sessionId: String, bindingId: String, surface: Surface, width: Int, height: Int, interactive: Boolean): MobileUseResult =
        manual(sessionId) { remote, id ->
            remote.setPreview(id, bindingId, surface, width, height, interactive)
            MobileUseResult("preview_attached", "", actionStatus = MobileUseResult.ACTION_DELIVERED)
        }

    override suspend fun detachPreview(sessionId: String, bindingId: String) {
        manual(sessionId) { remote, id ->
            remote.clearPreview(id, bindingId)
            MobileUseResult("preview_detached", "")
        }
    }

    private suspend fun manual(sessionId: String, action: (IMobileUseService, Int) -> MobileUseResult): MobileUseResult =
        withContext(Dispatchers.IO) {
            inputMutex.withLock {
                val current = displaySession.value?.takeIf { it.id == sessionId }
                    ?: return@withLock invalid("session_lost", "Background app is no longer available.")
                val remote = service ?: return@withLock invalid("session_lost", "Connection lost.")
                try {
                    action(remote, current.displayId)
                } catch (_: DeadObjectException) {
                    sessions.clear(blockOwner = true)
                    scope.launch { mutex.withLock { if (service === remote) closeLocked() } }
                    invalid("session_lost", "Connection lost.")
                } catch (_: RemoteException) {
                    invalid("input_unavailable", "Input unavailable.")
                } catch (_: RuntimeException) {
                    invalid("input_unavailable", "Input unavailable.")
                }
            }
        }

    private suspend fun onObserved(
        owner: String, navigation: Boolean = false,
        coordinateBounds: ((MobileObservedScreen) -> MobileBounds?)? = null, swipe: Boolean = false, textInput: Boolean = false,
        operation: suspend (IMobileUseService, Int, MobileObservedScreen, MobileActionDelivery) -> MobileUseResult,
    ): MobileUseResult {
        // 排队动作只能使用调用时已看到的观察，不能借用前一个并行动作刚生成的新截图。
        val expectedObservation = guard.latest?.id
        return perform(owner, action = true, allowCreate = false) { remote, id, delivery ->
            val previous = guard.latest ?: return@perform invalid("stale_observation", "Take a screenshot before acting.")
            if (previous.id != expectedObservation) return@perform invalid("stale_observation", "Observation changed while action was queued; inspect a new screenshot.")
            val reading = readFrame(remote)
            if (reading.error != null) return@perform invalid("stale_observation", "Capture error; observe again before acting.")
            val frame = reading.frame
            val native = nativeSnapshot(id, frame)
            val bounds = guard.latest?.let { coordinateBounds?.invoke(it) }
            val rejection = guard.rejection(owner, id, previous.id, now(), frame, native, navigation = navigation, coordinateBounds = bounds, swipe = swipe, textInput = textInput)
            if (rejection != null) {
                guard.clear()
                // 焦点类失败靠重新截图救不了：必须返回指向"先点击字段"的结果码，
                // 否则模型会陷入"截图→原样重试"的死循环（TapTap 搜索页实测出现过 6 次浪费）。
                // 连续两次 text_focus_unavailable 说明该输入路径整体不可输入，直接硬停换路，
                // 提示词约束对弱模型不可靠（魅族商店搜索页实测连试 3 次输入）。
                // 无障碍服务未连接时快照为空，必须报真实原因，否则服务问题和假输入框混在同一结果码。
                return@perform if (textInput) {
                    val failures = if (rejection == "text_focus_unavailable" && previous.native.status == "available") {
                        consecutiveTextFocusUnavailable += 1
                        consecutiveTextFocusUnavailable
                    } else 0
                    textRejection(rejection, previous.native, failures)
                } else invalid("stale_observation", "$rejection; call mobile_take_screenshot before acting.")
            }
            operation(remote, id, checkNotNull(guard.latest), delivery)
        }
    }

    /**
     * 文本输入复核失败的结果码：首次失败指向"先点击字段"但带退出条件；
     * 连续两次无焦点则硬停为 text_entry_unsupported，杜绝"点击→输入"死循环。
     * 可编辑节点诊断帮助模型区分"忘了先点击"与"该屏根本没有可输入控件"。
     */
    private fun textRejection(
        rejection: String, native: MobileAccessibilitySnapshot, consecutiveFocusFailures: Int,
    ) = when {
        // 真实原因优先：服务未连接或快照失败时，焦点类文案只会误导模型反复点击重试。
        rejection == "text_focus_unavailable" && native.status == "accessibility_unavailable" ->
            invalid("accessibility_required",
                "The Gimi Text Input accessibility service is not connected, so editable fields cannot be found " +
                    "or filled on this display and mobile_type always fails. Enable Gimi Text Input in the system " +
                    "Accessibility settings, then take a screenshot and retry. Do not tap or retype before it is enabled.")
        rejection == "text_focus_unavailable" && native.status == "accessibility_error" ->
            invalid("text_observation_failed",
                "The accessibility snapshot failed while reading this display, so text input cannot be validated. " +
                    "Take a new screenshot and retry once; if it fails again, report the blocker instead of repeating.")
        rejection == "text_focus_unavailable" && consecutiveFocusFailures >= 2 -> invalid("text_entry_unsupported",
            "Two consecutive mobile_type attempts on this input path found no editable focus even after tapping " +
                "the intended field. ${editableNodeReport(native)}Text entry cannot work here. " +
                "Do not call mobile_type or chase focus with keys again. Switch to a tap-only route " +
                "(browse lists, categories or suggestions and tap the target directly), ask the user to type " +
                "on the controlled screen, or report the blocker to the user.")
        rejection == "text_focus_unavailable" -> invalid("text_focus_unavailable",
            "No focused editable field. First attempt only: tap the field with mobile_click, then call mobile_type. " +
                "If this call already followed a tap on the intended field, the tapped element is likely a fake input " +
                "(for example a rotating hot-search placeholder bar): do not tap the same coordinates or retype again. " +
                "Verify with one screenshot, then switch to a real input page or another input path, or report the " +
                "blocker to the user. ${editableNodeReport(native)}")
        rejection == "text_focus_changed" -> invalid("text_focus_changed",
            "Text focus changed since the screenshot: take a screenshot, tap the field again, then type once.")
        rejection == "text_target_changed" -> invalid("text_focus_changed",
            "The focused field or its selection changed: take a screenshot, tap the field again, then type once.")
        else -> invalid("stale_observation", "$rejection; call mobile_take_screenshot before acting.")
    }

    /** 当前无障碍快照完全不含可编辑节点时明确告知：在本屏点多少次都造不出输入焦点。 */
    private fun editableNodeReport(native: MobileAccessibilitySnapshot): String {
        val editable = native.targets.values.count { it.ownElement.editable && it.ownElement.enabled }
        return if (editable == 0) {
            "The accessibility snapshot exposes no editable node on this screen; tapping cannot create one, " +
                "and a retry only works if the tap navigates to a different screen. "
        } else ""
    }

    private suspend fun perform(
        owner: String, action: Boolean = false, allowCreate: Boolean = true,
        operation: suspend (IMobileUseService, Int, MobileActionDelivery) -> MobileUseResult,
    ): MobileUseResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val delivery = MobileActionDelivery(action)
            executionRejection(owner)?.let { return@withLock it.copy(actionStatus = delivery.status) }
            if (physicalSessionId != null && displaySession.value == null) return@withLock invalid("session_closed", "Background app is closing.")
            if (!allowCreate && (displayId == null || service == null)) {
                return@withLock invalid("stale_observation", "Display session ended; take a screenshot first.")
            }
            try {
                val remote = service ?: bind().also { service = it }
                if (remote.uid() != 2000) {
                    closeLocked()
                    return@withLock invalid("root_unsupported", "Only Shizuku ADB shell mode is supported.")
                }
                val id = displayId ?: remote.createDisplay().also {
                    displayId = it
                    val geometry = remote.geometry(it)
                    val sessionId = UUID.randomUUID().toString()
                    physicalSessionId = sessionId
                    sessions.open(owner, sessionId, it, geometry[0], geometry[1])
                    Log.i("GimiMobileUse", "Display session created: $it")
                }
                // 观察最多等待六秒；不能在等待截图期间锁住人工触摸和预览输入。
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

    private fun executionRejection(owner: String): MobileUseResult? {
        if (!enabled.value) return unavailable(MobileUseAvailability.DISABLED)
        when (sessions.claim(owner)) {
            MobileExecutionAccess.ALLOWED -> Unit
            MobileExecutionAccess.CLOSED -> return invalid("session_closed", "User closed the background app. Do not recreate it during this execution.")
            MobileExecutionAccess.UNREGISTERED -> return invalid("execution_finished", "This execution ended; a new turn can open the app.")
            MobileExecutionAccess.OTHER_CHAT -> return invalid("busy", "Another chat has an open background app. Ask the user to close it first.")
            MobileExecutionAccess.BUSY -> return invalid("busy", "Another execution in this chat is using the display.")
        }
        val available = availability()
        return if (available == MobileUseAvailability.READY || available == MobileUseAvailability.BUSY) null else unavailable(available)
    }

    private fun operationFailure(failure: Exception, delivery: MobileActionDelivery): MobileUseResult {
        guard.clear()
        if (displayId == null) {
            closeLocked()
            return MobileUseResult("unsupported", "Unable to create a background display; check device support.", actionStatus = delivery.status)
        }
        Log.w("GimiMobileUse", "Operation failed; display retained; actionStatus=${delivery.status}", failure)
        return MobileUseResult("operation_failed",
            "Operation or observation failed. Call mobile_take_screenshot; do not automatically repeat an action.",
            displayId = displayId, actionStatus = delivery.status)
    }

    private suspend fun observeResult(
        remote: IMobileUseService, id: Int, owner: String, delivery: MobileActionDelivery,
        status: String, message: String,
    ): MobileUseResult {
        guard.clear()
        val startedAt = now()
        val requiredFrameAfterMs = guard.requiredFrameAfterMs
        // 动作后的新帧要求跨 observe 调用保留；超时后的旧缓存不能在下一次观察中重新变成 settled。
        val stability = MobileFrameStability(startedAt, requiredFrameAfterMs)
        var frame: CapturedMobileFrame? = null
        var native = MobileAccessibilitySnapshot("not_captured")
        var state = "timeout"
        var reason = "no_frame"
        var captureError: String? = null
        while (now() - startedAt < OBSERVATION_TIMEOUT_MS) {
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
            val hasPostActionFrame = requiredFrameAfterMs == null ||
                frame?.capturedAtMs?.let { it > requiredFrameAfterMs } == true
            if (reading.error == null && frame != null && hasPostActionFrame &&
                now() - startedAt >= CHANGING_OBSERVATION_GRACE_MS
            ) {
                native = nativeSnapshot(id, frame)
                if (native.consistent) {
                    // 轮播、广告和计时器可以长期变化；一帧动作后的画面已足够交给模型判断，
                    // 后续动作会在发送前再次复核目标局部，而不是重复等待整屏静止。
                    state = "changing"
                    reason = stability.timeoutReason()
                    break
                }
            }
            reason = if (captureError == null) stability.timeoutReason() else "capture_error"
            delay(if (frame == null) 300 else 150)
        }
        var elements = if (state == "settled") native.targets.values.map { it.element } else emptyList()
        var nodesStatus = if (state == "settled") native.status else "not_captured"
        var truncated = native.truncated
        val observationId = UUID.randomUUID().toString()
        val observedAt = now()
        // 静止是观察信息，不是所有动作的前提；动态页面在动作前按目标局部复核。
        val pixelActionsAllowed = state == "settled" || state == "changing" ||
            reason == "changing" || reason == "changed_during_element_capture"
        if (state == "timeout" && frame != null && captureError == null) {
            native = nativeSnapshot(id, frame)
            elements = if (native.consistent) native.targets.values.map { it.element } else emptyList()
            nodesStatus = native.status
            truncated = native.truncated
        }
        val actionable = frame != null && captureError == null && native.consistent
        if (actionable) {
            guard.confirmFrame(frame)
            guard.record(MobileObservedScreen(observationId, owner, id, observedAt, frame, native, elements,
                actionsAllowed = pixelActionsAllowed))
        }
        if (frame == null) state = "frame_unavailable"
        val observation = MobileObservation(
            observationId, state, reason, frame?.let { (observedAt - it.capturedAtMs).coerceAtLeast(0) },
            frame?.sequence, nodesStatus, elements, truncated,
            actionModes = if (!actionable) emptyList() else buildList {
                if (pixelActionsAllowed) {
                    add("pixel_coordinates")
                    add("swipe_coordinates")
                }
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
        val expected = displaySession.value
        // AI 只接收完整副屏帧；缩略图或旋转前的缓存不能进入上下文，更不能建立点击依据。
        if (frame != null && (expected == null || frame.width != expected.width || frame.height != expected.height)) {
            return MobileFrameReading(null, "frame_geometry_mismatch")
        }
        return MobileFrameReading(frame, payload.getString("error"))
    }

    /** 点按周围留出采样余量，拒绝目标附近的移动而允许屏幕其他区域动画。 */
    private fun tapBounds(x: Int, y: Int) = MobileBounds(x - 48, y - 48, x + 49, y + 49)

    private fun attempt(delivery: MobileActionDelivery, send: () -> Boolean): Boolean {
        val previousRequirement = guard.requiredFrameAfterMs
        val accepted = delivery.attempt(::now, { guard.clear(); guard.awaitFrameAfter(now()) }, send)
        // 目标应用可能在 shell 命令退出前完成绘制，应等待发送开始后的帧，而不是命令返回后的帧。
        // 明确拒绝的动作不产生新绘制要求；未知异常则保留要求，供下一次 observe 继续检查。
        if (!accepted) guard.awaitFrameAfter(previousRequirement)
        return accepted
    }

    private fun invalid(status: String, message: String) =
        MobileUseResult(status, message, actionStatus = MobileUseResult.ACTION_NOT_SENT)
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
                sessions.clear(blockOwner = true)
                displayId?.let { MobileTextAccessibilityService.current()?.forgetDisplay(it) }
                service = null
                displayId = null
                physicalSessionId = null
                connection = null
                scope.launch { mutex.withLock { if (connection == null) guard.reset() } }
                if (!deferred.isCompleted) deferred.completeExceptionally(
                    IllegalStateException("Shizuku UserService disconnected"),
                )
            }
        }
        connection = candidate
        try {
            Shizuku.bindUserService(args, candidate)
            return withTimeout(10_000.milliseconds) { deferred.await() }
        } catch (failure: TimeoutCancellationException) {
            // bind 超时不是协程取消；转成普通失败，perform 才能把错误结果交还工具调用，
            // 否则 TimeoutCancellationException 会伪装成取消让这一轮静默中断。
            closeLocked()
            throw IllegalStateException("Shizuku service bind timed out", failure)
        } catch (failure: CancellationException) {
            closeLocked()
            throw failure
        } catch (failure: RuntimeException) {
            closeLocked()
            throw failure
        }
    }

    private fun closeLocked() {
        sessions.clear(blockOwner = true)
        guard.reset()
        displayId?.let { MobileTextAccessibilityService.current()?.forgetDisplay(it) }
        runCatching { service?.stop() }
        if (connection != null && Shizuku.pingBinder()) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
        connection = null
        displayId = null
        physicalSessionId = null
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

    private companion object {
        /** 始终变化的页面也只需取得动作后的新帧；给首帧编码留出短暂时间。 */
        const val CHANGING_OBSERVATION_GRACE_MS = 1_000L
        const val OBSERVATION_TIMEOUT_MS = 6_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MobileUseModule {
    @Binds
    abstract fun bindMobileDisplayPreviewGateway(implementation: ShizukuMobileUseRepository): MobileDisplayPreviewGateway
    @Binds
    abstract fun bindMobileUseController(implementation: ShizukuMobileUseRepository): MobileUseController
    @Binds
    abstract fun bindMobileUseRepository(
        implementation: ShizukuMobileUseRepository,
    ): MobileUseRepository
}
