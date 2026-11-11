package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Bundle
import android.os.SystemClock
import android.os.RemoteException
import android.os.DeadObjectException
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import io.mockk.every
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.cancelAndJoin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rikka.shizuku.Shizuku

class ShizukuMobileUseRepositoryTest {
    private val connections = mutableListOf<ServiceConnection>()
    private val enabled = MutableStateFlow(false)
    private val preferences = mockk<MobileUsePreferences> {
        every { enabled } returns this@ShizukuMobileUseRepositoryTest.enabled
        every { smallWindow } returns MutableStateFlow(false)
        every { setEnabled(any()) } answers { this@ShizukuMobileUseRepositoryTest.enabled.value = firstArg() }
    }
    private val context = mockk<Context>()
    private val repository = ShizukuMobileUseRepository(context, preferences)

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun explicitStopClosesDisplayAndBlocksReopeningUntilANewExecution() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.takeScreenshot("first")
        val id = repository.displaySession.value!!.id
        assertEquals("closed", repository.stopExecution("first").status)
        assertEquals(null, repository.displaySession.value)
        assertEquals("session_lost", repository.manualBack(id).status)
        assertEquals("session_closed", repository.takeScreenshot("first").status)
        assertEquals("not_active", repository.stopExecution("first").status)
        repository.finishExecution("first")
        verify(exactly = 1) { remote.stop() }
        repository.registerExecution("next", "chat")
        repository.takeScreenshot("next")
        assertFalse(id == repository.displaySession.value!!.id)
        repository.setEnabled(false)
    }

    @Test
    fun explicitStopRejectsOtherChatsUnregisteredAndBusyExecutions() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.takeScreenshot("first")
        val before = repository.displaySession.value
        assertEquals("execution_finished", repository.stopExecution("unknown").status)
        repository.registerExecution("other", "other-chat")
        assertEquals("busy", repository.stopExecution("other").status)
        repository.registerExecution("parallel", "chat")
        assertEquals("busy", repository.stopExecution("parallel").status)
        assertEquals(before, repository.displaySession.value)
        verify(exactly = 0) { remote.stop() }
        repository.finishExecution("first")
        assertEquals("execution_finished", repository.stopExecution("first").status)
        assertEquals(before?.id, repository.displaySession.value?.id)
        assertEquals("closed", repository.stopExecution("parallel").status)
        assertEquals(null, repository.displaySession.value)
        verify(exactly = 1) { remote.stop() }
        repository.setEnabled(false)
    }

    @Test
    fun disabledFeatureRejectsEveryDisplayActionWithoutAccessingShizuku() = runBlocking {
        assertEquals(MobileUseAvailability.DISABLED, repository.availability())
        assertEquals("disabled", repository.takeScreenshot("task").status)
        assertEquals("disabled", repository.openApp("task", "com.example.app").status)
        assertEquals("disabled", repository.click("task", 1, 1).status)
        assertEquals("disabled", repository.listApps("task").status)
        assertEquals("disabled", repository.wait("task").status)
        assertEquals("disabled", repository.dragAndDrop("task", 1, 1, 2, 2).status)
        assertEquals("disabled", repository.longPress("task", 1, 1).status)
        assertEquals("disabled", repository.goBack("task").status)
        assertEquals("disabled", repository.type("task", "hello").status)
        assertEquals("disabled", repository.runShell("task", "echo test").status)
        assertEquals("not_sent", repository.click("task", 1, 1).actionStatus)
        repository.requestPermission(1)
        confirmVerified(context)
    }

    @Test
    fun disablingStopsActiveDisplayAndReenablingAllowsANewOwner() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first-task", "chat1")
        assertTrue(repository.enabled.value)
        assertEquals("operation_failed", repository.takeScreenshot("first-task").status)
        assertEquals(MobileUseAvailability.BUSY, repository.availability())
        repository.setEnabled(false)
        assertFalse(repository.enabled.value)
        assertEquals("disabled", repository.takeScreenshot("first-task").status)
        verify(exactly = 1) { remote.stop() }
        verify(exactly = 1) { Shizuku.unbindUserService(any(), any(), true) }

        repository.setEnabled(true)
        repository.registerExecution("second-task", "chat2")
        assertEquals(MobileUseAvailability.READY, repository.availability())
        assertEquals("operation_failed", repository.takeScreenshot("second-task").status)
        verify(exactly = 2) { remote.createDisplay() }
        repository.setEnabled(false)
    }

    @Test
    fun displaySurvivesTurnCompletionAndManualInputDoesNotRequireAnExecution() = runBlocking {
        val remote = connectRemote()
        every { remote.touch(any(), any(), any(), any(), any(), any()) } returns true
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.takeScreenshot("first")
        val id = repository.displaySession.value!!.id
        assertEquals("execution_finished", repository.finishExecution("first").status)
        assertEquals(1L, repository.displaySession.value!!.completionVersion)
        assertEquals("not_active", repository.finishExecution("first").status)
        assertEquals(1L, repository.displaySession.value!!.completionVersion)
        assertEquals(id, repository.displaySession.value!!.id)
        val touch = MobileTouch(MobileTouchAction.DOWN, 10f, 20f, 100, 100)
        assertEquals("delivered", repository.manualTouch(id, touch).status)
        assertEquals("delivered", repository.manualBack(id).status)
        repository.registerExecution("other", "other-chat")
        assertEquals("busy", repository.takeScreenshot("other").status)
        repository.finishExecution("other")
        repository.registerExecution("next", "chat")
        repository.takeScreenshot("next")
        assertEquals(id, repository.displaySession.value!!.id)
        verify(exactly = 1) { remote.createDisplay() }
        verify(exactly = 0) { remote.stop() }
        repository.closeSession(id)
        assertEquals("session_closed", repository.takeScreenshot("next").status)
        assertEquals("session_lost", repository.manualTouch(id, touch).status)
        repository.finishExecution("next")
        repository.registerExecution("new", "chat")
        repository.takeScreenshot("new")
        assertFalse(id == repository.displaySession.value!!.id)
        assertEquals("not_active", repository.closeSession(id).status)
        verify(exactly = 2) { remote.createDisplay() }
        verify(exactly = 1) { remote.stop() }
        repository.setEnabled(false)
    }

    @Test
    fun manualRotationUpdatesGeometryWithoutRecreatingTheSessionOrClaimingExecution() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.takeScreenshot("first")
        repository.finishExecution("first")
        val before = repository.displaySession.value!!
        every { remote.rotateDisplay(10) } returns intArrayOf(2400, 1080, 320)
        every { remote.geometry(10) } returns intArrayOf(2400, 1080, 320)
        val result = repository.manualRotate(before.id)
        assertEquals("delivered", result.status)
        assertEquals("delivered", result.actionStatus)
        assertEquals(2400, result.width)
        assertEquals(1080, result.height)
        assertEquals(before.copy(width = 2400, height = 1080), repository.displaySession.value)
        every { remote.rotateDisplay(10) } returns intArrayOf(1080, 2400, 320)
        every { remote.geometry(10) } returns intArrayOf(1080, 2400, 320)
        assertEquals("delivered", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        verify(exactly = 1) { remote.createDisplay() }
        verify(exactly = 0) { remote.stop() }
        repository.setEnabled(false)
    }

    @Test
    fun rotationRejectsOldWindowsAndRetainsSessionWhenUnsupported() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.takeScreenshot("first")
        val before = repository.displaySession.value!!
        assertEquals("session_lost", repository.manualRotate("old-window").status)
        verify(exactly = 0) { remote.rotateDisplay(any()) }
        every { remote.rotateDisplay(10) } throws IllegalStateException("Unsupported")
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        every { remote.rotateDisplay(10) } throws RemoteException()
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        every { remote.rotateDisplay(10) } returns intArrayOf(0, 1080, 320)
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        repository.setEnabled(false)
    }

    @Test
    fun shellRequiresAnExistingOwnedSessionAndCannotReopenAClosedOne() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("task", "chat")
        assertEquals("stale_observation", repository.runShell("task", "echo test").status)
        verify(exactly = 0) { remote.createDisplay() }
        repository.takeScreenshot("task")
        repository.registerExecution("other", "other-chat")
        assertEquals("busy", repository.runShell("other", "echo test").status)
        assertEquals("execution_finished", repository.runShell("unknown", "echo test").status)
        repository.stopExecution("task")
        assertEquals("session_closed", repository.runShell("task", "echo test").status)
        verify(exactly = 0) { remote.runShell(any(), any()) }
        repository.setEnabled(false)
    }

    @Test
    fun shellResultSurvivesScreenshotFailureWithoutRetryingTheCommand() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("task", "chat")
        repository.takeScreenshot("task")
        every { remote.runShell(10, "some-command") } returns mockk {
            every { containsKey("exitCode") } returns true
            every { getInt("exitCode") } returns 7
            every { getString("output") } returns "partial changes"
            every { getBoolean("timedOut") } returns false
            every { getBoolean("outputTruncated") } returns true
        }
        val result = repository.runShell("task", "some-command")
        assertEquals("shell_failed", result.status)
        assertEquals("delivered", result.actionStatus)
        assertEquals(7, result.shellResult?.exitCode)
        assertEquals("partial changes", result.shellResult?.output)
        assertTrue(result.shellResult!!.outputTruncated)
        verify(exactly = 1) { remote.runShell(10, "some-command") }
        repository.setEnabled(false)
    }

    @Test
    fun shellBinderFailureIsUnknownAndNeverAutomaticallyRetried() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("task", "chat")
        repository.takeScreenshot("task")
        every { remote.runShell(10, "some-command") } throws RemoteException("connection lost")
        assertEquals("unknown", repository.runShell("task", "some-command").actionStatus)
        verify(exactly = 1) { remote.runShell(10, "some-command") }
        repository.setEnabled(false)
    }

    @Test
    fun shellInputTextRejectsNonAsciiPayloadBeforeReachingTheBackend() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("task", "chat")
        repository.takeScreenshot("task")
        // shell 无法输入中文（平台 input text 抛 NPE）：必须在进入 shell 前拒绝并指路 mobile_type。
        for (command in listOf("input text 'QQ飞车'", "input -d 10 text QQ飞车")) {
            val result = repository.runShell("task", command)
            assertEquals("invalid_argument", result.status)
            assertEquals("not_sent", result.actionStatus)
        }
        verify(exactly = 0) { remote.runShell(any(), any()) }
        repository.setEnabled(false)
    }

    @Test
    fun shellInputCommandsTargetTheControlledDisplayUnlessADisplayFlagIsGiven() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("task", "chat")
        repository.takeScreenshot("task")
        every { remote.runShell(10, "input -d 10 text QQ") } returns shellPayload()
        every { remote.runShell(10, "input -d 3 tap 100 200") } returns shellPayload()
        every { remote.runShell(10, "echo input text QQ") } returns shellPayload()
        assertEquals("shell_completed", repository.runShell("task", "input text QQ").status)
        assertEquals("shell_completed", repository.runShell("task", "input -d 3 tap 100 200").status)
        // 非 input 开头的命令不改写，避免误改复合命令或 echo 内容。
        assertEquals("shell_completed", repository.runShell("task", "echo input text QQ").status)
        verify(exactly = 1) { remote.runShell(10, "input -d 10 text QQ") }
        verify(exactly = 1) { remote.runShell(10, "input -d 3 tap 100 200") }
        verify(exactly = 1) { remote.runShell(10, "echo input text QQ") }
        repository.setEnabled(false)
    }

    private fun shellPayload(exitCode: Int = 0, output: String = "ok"): Bundle = mockk {
        every { containsKey("exitCode") } returns true
        every { getInt("exitCode") } returns exitCode
        every { getString("output") } returns output
        every { getBoolean("timedOut") } returns false
        every { getBoolean("outputTruncated") } returns false
    }

    private fun connectRemote(): IMobileUseService {
        mockkStatic(Shizuku::class)
        val packageManager = mockk<PackageManager> {
            every { getPackageInfo("moe.shizuku.privileged.api", 0) } returns mockk<PackageInfo>()
        }
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "github.ponyhuang.gimi"
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.getUid() } returns 2000
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_GRANTED
        val remote = mockk<IMobileUseService>(relaxed = true) {
            every { tap(any(), any(), any()) } returns true
            every { back(any()) } returns true
            every { uid() } returns 2000
            every { createDisplay() } returns 10
            // 截图不可用时仍保留后台任务；关闭开关必须清理该任务，而非等待下一次观察。
            every { capture() } throws IllegalStateException("Frame temporarily unavailable")
            every { geometry(any()) } returns intArrayOf(1080, 2400, 320)
        }
        val binder = mockk<IBinder> {
            every { queryLocalInterface(any()) } returns remote
        }
        every { Shizuku.bindUserService(any(), any()) } answers {
            val candidate = secondArg<ServiceConnection>()
            connections += candidate
            candidate.onServiceConnected(mockk(), binder)
        }
        every { Shizuku.unbindUserService(any(), any(), true) } returns Unit

        return remote
    }

    @Test
    fun animatedFeedAdvertisesSwipeAndSendsItOnceWithoutSettledPixels() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("feed", "chat")
        val observation = repository.takeScreenshot("feed").observation!!
        assertEquals("changing", observation.reason)
        assertEquals("changing", observation.state)
        assertTrue("swipe_coordinates" in observation.actionModes)
        val result = repository.dragAndDrop("feed", 500, 500, 500, 166)
        assertEquals("dragged", result.status)
        assertEquals("delivered", result.actionStatus)
        assertEquals("changing", result.observation!!.reason)
        assertEquals("changing", result.observation!!.state)
        verify(exactly = 1) { remote.gesture(10, 540, 1200, 540, 398, 0, 450) }
        repository.setEnabled(false)
    }

    @Test
    fun animatedFeedAllowsTapWhenOnlyUnrelatedPixelsChange() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("feed", "chat")
        val observation = repository.takeScreenshot("feed").observation!!
        assertEquals("changing", observation.reason)
        assertTrue("pixel_coordinates" in observation.actionModes)
        val result = repository.click("feed", 500, 500)
        assertEquals("clicked", result.status)
        assertEquals("delivered", result.actionStatus)
        verify(exactly = 1) { remote.tap(10, 540, 1200) }
        repository.setEnabled(false)
    }

    @Test
    fun waitIsCancellableAndDoesNotCreateOrCaptureADisplay() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        val job = launch { repository.wait("turn", 60) }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        verify(exactly = 0) { remote.createDisplay() }
        verify(exactly = 0) { remote.capture() }
        assertEquals("execution_finished", repository.finishExecution("turn").status)
        repository.setEnabled(false)
    }

    @Test
    fun appDiscoveryDoesNotCreateDisplayAndAmbiguousNamesNeverLaunch() = runBlocking {
        val remote = connectRemote()
        val pm = context.packageManager
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addCategory(Intent.CATEGORY_LAUNCHER) } answers { self as Intent }
        mockkStatic(PackageManager.ResolveInfoFlags::class)
        every { PackageManager.ResolveInfoFlags.of(0) } returns mockk()
        fun app(name: String, id: String): ResolveInfo = mockk<ResolveInfo> {
            every { loadLabel(pm) } returns name
        }.also { it.activityInfo = ActivityInfo().apply { packageName = id } }
        every { pm.queryIntentActivities(any<Intent>(), any<PackageManager.ResolveInfoFlags>()) } returns listOf(
            app("Same", "com.example.two"), app("Same", "com.example.one"), app("Same", "com.example.one"),
        )
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        val listed = repository.listApps("turn")
        assertEquals(listOf("com.example.one", "com.example.two"), listed.apps.map { it.packageName })
        verify(exactly = 0) { remote.createDisplay() }
        val ambiguous = repository.openApp("turn", "Same")
        assertEquals("ambiguous_app", ambiguous.status)
        assertEquals(2, ambiguous.apps.size)
        verify(exactly = 0) { remote.launch(any(), any()) }
        val launcher = mockk<Intent> { every { component } returns android.content.ComponentName("com.example.one", "Main") }
        every { pm.getLaunchIntentForPackage("com.example.one") } returns launcher
        // JVM 的 ComponentName 方法也需明确模拟；不依赖 Android 桩方法的默认返回值。
        every { launcher.component } returns mockk { every { flattenToString() } returns "com.example.one/Main" }
        val opened = repository.openApp("turn", "com.example.one")
        assertEquals("delivered", opened.actionStatus)
        verify(exactly = 1) { remote.launch("com.example.one/Main", 10) }
        repository.setEnabled(false)
    }

    @Test
    fun unknownGestureResultIsNotRetriedAndConsumesTheObservation() = runBlocking {
        val remote = connectAnimatedRemote()
        every { remote.gesture(any(), any(), any(), any(), any(), any(), any()) } throws RemoteException()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val result = repository.dragAndDrop("turn", 500, 500, 500, 166, 600)
        assertEquals("operation_failed", result.status)
        assertEquals("unknown", result.actionStatus)
        assertEquals("stale_observation", repository.dragAndDrop("turn", 500, 500, 500, 166, 600).status)
        verify(exactly = 1) { remote.gesture(10, 540, 1200, 540, 398, 600, 450) }
        repository.setEnabled(false)
    }

    @Test
    fun longPressUsesOneContinuousStationaryGesture() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        assertEquals("long_pressed", repository.longPress("turn", 999, 999).status)
        verify(exactly = 1) { remote.gesture(10, 1079, 2399, 1079, 2399, 2000, 0) }
        repository.setEnabled(false)
    }

    @Test
    fun enterIsOnlySentAfterConfirmedInsertionAndCursorRestoration() = runBlocking {
        val remote = connectAnimatedRemote()
        // 使用持续变化的画面覆盖真实输入入口，不能用稳定 JPEG 掩盖整屏校验回归。
        val field = MobileElement(
            "field", "accessibility", MobileBounds(10, 20, 300, 80),
            editable = true, enabled = true, focused = true, text = "", selectionStart = 0, selectionEnd = 0,
        )
        val target = MobileNodeTarget(field, emptyList(), field, null, null)
        mockkObject(MobileTextAccessibilityService.Companion)
        val accessibility = mockk<MobileTextAccessibilityService> {
            every { requiresPaste(10, target) } returns false
            every { lastChangeMs(10) } returns 0
            every { snapshot(10, 1080, 2400) } returns MobileAccessibilitySnapshot("available", targets = mapOf("field" to target))
        }
        every { MobileTextAccessibilityService.current() } returns accessibility
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        for (outcome in MobileTextInputOutcome.entries) {
            repository.takeScreenshot("turn")
            every { accessibility.insertText(10, "hello", target) } returns outcome
            val result = repository.type("turn", "hello", true)
            assertEquals(if (outcome == MobileTextInputOutcome.REJECTED) "rejected" else "delivered", result.actionStatus)
        }
        every { remote.pressKey(10, "ENTER") } returns false
        repository.takeScreenshot("turn")
        every { accessibility.insertText(10, "hello", target) } returns MobileTextInputOutcome.INSERTED
        assertEquals("text_typed_enter_rejected", repository.type("turn", "hello", true).status)
        verify(exactly = 5) { accessibility.insertText(10, "hello", target) }
        verify(exactly = 2) { remote.pressKey(10, "ENTER") }
        verify(exactly = 0) { remote.tap(any(), any(), any()) }
        every { accessibility.forgetDisplay(10) } returns Unit
        repository.setEnabled(false)
    }

    @Test
    fun textFocusRejectionReportsTheFocusProblemInsteadOfStaleObservation() = runBlocking {
        val remote = connectAnimatedRemote()
        // 无可编辑焦点节点：正是 TapTap 这类 WebView 搜索框触发过的形态。
        val idle = MobileElement("idle", "accessibility", MobileBounds(10, 20, 300, 80), enabled = true)
        val target = MobileNodeTarget(idle, emptyList(), idle, null, null)
        mockkObject(MobileTextAccessibilityService.Companion)
        val accessibility = mockk<MobileTextAccessibilityService> {
            every { lastChangeMs(10) } returns 0
            every { snapshot(10, 1080, 2400) } returns MobileAccessibilitySnapshot("available", targets = mapOf("idle" to target))
        }
        every { MobileTextAccessibilityService.current() } returns accessibility
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val result = repository.type("turn", "QQ飞车")
        assertEquals("text_focus_unavailable", result.status)
        assertEquals("not_sent", result.actionStatus)
        assertTrue(result.message.contains("mobile_click"))
        // 首次失败指路"先点击"；已点击后仍无焦点必须给出退出条件，禁止同坐标"点击→输入"死循环。
        assertTrue(result.message.contains("First attempt only"))
        assertTrue(result.message.contains("fake input"))
        assertTrue(result.message.contains("do not tap the same coordinates"))
        // 夹具整屏无 editable 节点：诊断必须直接说明"点不出焦点"，魅族商店搜索页即此形态。
        assertTrue(result.message.contains("no editable node"))
        verify(exactly = 0) { accessibility.insertText(any(), any(), any()) }
        // 连续第二次无焦点：确定性硬停为 text_entry_unsupported，不再依赖模型自觉换路。
        repository.takeScreenshot("turn")
        val hardStop = repository.type("turn", "QQ飞车")
        assertEquals("text_entry_unsupported", hardStop.status)
        assertTrue(hardStop.message.contains("Do not call mobile_type"))
        verify(exactly = 0) { accessibility.insertText(any(), any(), any()) }
        // 成功输入后计数必须归零：同一执行内换到有焦点的屏幕再失败，仍是首次文案而非硬停。
        val field = MobileElement(
            "field", "accessibility", MobileBounds(10, 20, 300, 80),
            editable = true, enabled = true, focused = true, text = "", selectionStart = 0, selectionEnd = 0,
        )
        val fieldTarget = MobileNodeTarget(field, emptyList(), field, null, null)
        every { accessibility.requiresPaste(10, fieldTarget) } returns false
        every { accessibility.insertText(10, "QQ飞车", fieldTarget) } returns MobileTextInputOutcome.INSERTED
        every { accessibility.snapshot(10, 1080, 2400) } returns
            MobileAccessibilitySnapshot("available", targets = mapOf("field" to fieldTarget))
        repository.takeScreenshot("turn")
        assertEquals("text_typed", repository.type("turn", "QQ飞车").status)
        every { accessibility.snapshot(10, 1080, 2400) } returns MobileAccessibilitySnapshot("available", targets = mapOf("idle" to target))
        repository.takeScreenshot("turn")
        assertEquals("text_focus_unavailable", repository.type("turn", "QQ飞车").status)
        every { accessibility.forgetDisplay(10) } returns Unit
        repository.setEnabled(false)
    }

    @Test
    fun textRejectionReportsMissingAccessibilityServiceInsteadOfFocusProblem() = runBlocking {
        val remote = connectAnimatedRemote()
        mockkObject(MobileTextAccessibilityService.Companion)
        // 无障碍服务未连接：快照降级为 accessibility_unavailable，必须报真实原因，
        // 否则"服务没开"与"假输入框"混成同一个 text_focus_unavailable（魅族商店实测误判过）。
        every { MobileTextAccessibilityService.current() } returns null
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val result = repository.type("turn", "QQ飞车")
        assertEquals("accessibility_required", result.status)
        assertEquals("not_sent", result.actionStatus)
        assertTrue(result.message.contains("Accessibility settings"))
        repository.setEnabled(false)
    }

    private fun connectAnimatedRemote(frameWidth: () -> Int = { 1080 }, frameHeight: () -> Int = { 2400 }): IMobileUseService {
        val remote = connectRemote()
        every { remote.gesture(any(), any(), any(), any(), any(), any(), any()) } returns true
        mockkStatic(SystemClock::class)
        var clockMs = 10_000L
        var sequence = 0L
        // 注入单调时钟与持续变化的横幅，不依赖真机或六秒实际超时。
        every { SystemClock.elapsedRealtime() } answers { clockMs += 1_000; clockMs }
        every { remote.capture() } answers {
            val capturedAt = clockMs
            val currentSequence = ++sequence
            val pixels = IntArray(64 * 96) { 100 }.apply {
                for (index in 0..15) this[index] = if (currentSequence % 2 == 0L) 200 else 100
            }
            mockk<Bundle> {
                every { getByteArray("jpeg") } returns byteArrayOf(currentSequence.toByte())
                every { getIntArray("samples") } returns pixels
                every { getInt("width") } answers { frameWidth() }
                every { getInt("height") } answers { frameHeight() }
                every { getLong("capturedAtMs") } returns capturedAt
                every { getLong("sequence") } returns currentSequence
                every { getLong("generation") } returns 1
                every { getString("error") } returns null
            }
        }
        return remote
    }

    @Test
    fun staleDisconnectCannotClearReplacementButCurrentDisconnectClearsSession() = runBlocking {
        connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("old", "chat")
        repository.takeScreenshot("old")
        val oldConnection = connections.single()
        repository.closeSession(repository.displaySession.value!!.id)
        repository.finishExecution("old")
        repository.registerExecution("new", "chat")
        repository.takeScreenshot("new")
        val currentId = repository.displaySession.value!!.id
        oldConnection.onServiceDisconnected(mockk())
        assertEquals(currentId, repository.displaySession.value!!.id)
        connections.last().onServiceDisconnected(mockk())
        assertEquals(null, repository.displaySession.value)
        assertEquals("session_closed", repository.takeScreenshot("new").status)
        repository.setEnabled(false)
    }

    @Test
    fun rejectedAndUnavailableInputReportFailureWhileDeadBinderClearsSession() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val id = repository.displaySession.value!!.id
        val down = MobileTouch(MobileTouchAction.DOWN, 10f, 20f, 100, 100)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } returns false
        assertEquals("input_unavailable", repository.manualTouch(id, down).status)
        assertEquals("input_rejected", repository.manualTouch(id, down.copy(action = MobileTouchAction.CANCEL)).status)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } throws RemoteException()
        assertEquals("input_unavailable", repository.manualTouch(id, down).status)
        assertEquals(id, repository.displaySession.value!!.id)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } throws DeadObjectException()
        assertEquals("session_lost", repository.manualTouch(id, down).status)
        assertEquals(null, repository.displaySession.value)
        assertEquals("session_closed", repository.takeScreenshot("turn").status)
        repository.setEnabled(false)
    }

    @Test
    fun previewInteractivityIsForwardedOnlyForTheCurrentDisplaySession() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val id = repository.displaySession.value!!.id
        val surface = mockk<android.view.Surface>()
        assertEquals("preview_attached", repository.attachPreview(id, "compact", surface, 112, 236, false).status)
        assertEquals("preview_attached", repository.attachPreview(id, "expanded", surface, 264, 540, true).status)
        assertEquals("session_lost", repository.attachPreview("stale", "old", surface, 264, 540, true).status)
        verify(exactly = 1) { remote.setPreview(10, "compact", surface, 112, 236, false) }
        verify(exactly = 1) { remote.setPreview(10, "expanded", surface, 264, 540, true) }
        verify(exactly = 0) { remote.setPreview(any(), "old", any(), any(), any(), any()) }
        repository.setEnabled(false)
    }

    @Test
    fun previewScalingKeepsFullDisplayCoordinatesAndRejectsUnexpectedThumbnailFrames() = runBlocking {
        var width = 1080
        var height = 2400
        val remote = connectAnimatedRemote({ width }, { height })
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.takeScreenshot("turn")
        val session = repository.displaySession.value!!
        repository.attachPreview(session.id, "compact", mockk(), 112, 236, false)
        val full = repository.takeScreenshot("turn")
        assertEquals(1080, full.width)
        assertEquals(2400, full.height)
        assertEquals(session, repository.displaySession.value)
        assertEquals("clicked", repository.click("turn", 500, 500).status)

        width = 112
        height = 236
        val thumbnail = repository.takeScreenshot("turn")
        assertEquals(null, thumbnail.imageJpeg)
        assertTrue(thumbnail.observation!!.actionModes.isEmpty())
        assertEquals("stale_observation", repository.click("turn", 500, 500).status)
        assertEquals(session, repository.displaySession.value)

        width = 1080
        height = 2400
        assertEquals(1080, repository.takeScreenshot("turn").width)
        assertEquals("clicked", repository.click("turn", 500, 500).status)
        verify(exactly = 2) { remote.tap(10, 540, 1200) }
        repository.setEnabled(false)
    }
}
