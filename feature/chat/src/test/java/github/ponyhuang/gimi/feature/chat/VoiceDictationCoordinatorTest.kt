package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceDictationCoordinatorTest {
    private val audio = byteArrayOf(1, 2)
    private val attachment = DraftAttachment("/draft/image.png", "image.png", "image/png", 1L, AttachmentCategory.IMAGE)

    @Test
    fun transcribeKeepsExistingDraftAndAttachmentsForEditing() = runTest {
        var mode: VoiceInputMode? = null
        var result: VoiceDictationResult? = null
        val coordinator = VoiceDictationCoordinator(this, { " 识别文字 " }, { mode = it })
        coordinator.start(audio, VoiceInputMode.TRANSCRIBE, MessageData("原草稿", listOf(attachment))) {
            result = it.getOrThrow()
        }
        assertEquals(VoiceInputMode.TRANSCRIBE, mode)
        runCurrent()
        assertEquals(MessageData("原草稿 识别文字", listOf(attachment)), result?.message)
        assertEquals(false, result?.sendImmediately)
        assertNull(mode)
    }

    @Test
    fun directSendProducesOneSubmissionWithTheMergedDraft() = runTest {
        val results = mutableListOf<VoiceDictationResult>()
        val transcript = CompletableDeferred<String>()
        val coordinator = VoiceDictationCoordinator(this, { transcript.await() }, {})
        coordinator.start(audio, VoiceInputMode.SEND, MessageData("原草稿", listOf(attachment))) {
            results += it.getOrThrow()
        }
        coordinator.start(audio, VoiceInputMode.SEND, MessageData("重复")) { error("Duplicate callback") }
        runCurrent()
        transcript.complete("识别文字")
        runCurrent()
        assertEquals(1, results.size)
        assertTrue(results.single().sendImmediately)
        assertEquals(MessageData("原草稿 识别文字", listOf(attachment)), results.single().message)
    }

    @Test
    fun emptyTranscriptDoesNotSubmitAnExistingDraftOrAttachment() = runTest {
        var result: Result<VoiceDictationResult>? = null
        val coordinator = VoiceDictationCoordinator(this, { " \n " }, {})
        coordinator.start(audio, VoiceInputMode.SEND, MessageData("不能误发", listOf(attachment))) { result = it }
        runCurrent()
        assertTrue(result?.exceptionOrNull() is EmptyVoiceTranscriptException)
    }

    @Test
    fun recognitionFailureIsReportedAndReleasesBusyStateForRetry() = runTest {
        var mode: VoiceInputMode? = null
        var result: Result<VoiceDictationResult>? = null
        var attempts = 0
        val failure = IllegalStateException("offline")
        val coordinator = VoiceDictationCoordinator(this, {
            if (++attempts == 1) throw failure else "成功"
        }, { mode = it })
        coordinator.start(audio, VoiceInputMode.SEND, MessageData()) { result = it }
        runCurrent()
        assertEquals(failure, result?.exceptionOrNull())
        assertNull(mode)
        coordinator.start(audio, VoiceInputMode.TRANSCRIBE, MessageData()) { result = it }
        runCurrent()
        assertEquals("成功", result?.getOrThrow()?.message?.text)
    }

    @Test
    fun cancelBeforeRecognitionCompletesDropsEvenANonCooperativeLateResult() = runTest {
        var mode: VoiceInputMode? = null
        var deliveries = 0
        val transcript = CompletableDeferred<String>()
        val coordinator = VoiceDictationCoordinator(this, {
            withContext(NonCancellable) { transcript.await() }
        }, { mode = it })
        coordinator.start(audio, VoiceInputMode.SEND, MessageData()) { deliveries++ }
        runCurrent()
        coordinator.cancel()
        assertNull(mode)
        transcript.complete("不应发送")
        runCurrent()
        assertEquals(0, deliveries)
    }

    @Test
    fun oldCancelledOperationCannotClearTheNextOperationState() = runTest {
        var mode: VoiceInputMode? = null
        val old = CompletableDeferred<String>()
        val next = CompletableDeferred<String>()
        var attempts = 0
        val coordinator = VoiceDictationCoordinator(this, {
            if (++attempts == 1) withContext(NonCancellable) { old.await() } else next.await()
        }, { mode = it })
        coordinator.start(audio, VoiceInputMode.SEND, MessageData()) { error("Cancelled callback") }
        runCurrent()
        coordinator.cancel()
        coordinator.start(audio, VoiceInputMode.TRANSCRIBE, MessageData()) {}
        runCurrent()
        old.complete("旧结果")
        runCurrent()
        assertEquals(VoiceInputMode.TRANSCRIBE, mode)
        next.complete("新结果")
        runCurrent()
        assertNull(mode)
    }

    @Test
    fun gatewayCancellationPropagatesWithoutReportingAnErrorAndAllowsRetry() = runTest {
        var attempts = 0
        var deliveries = 0
        var mode: VoiceInputMode? = null
        val coordinator = VoiceDictationCoordinator(this, {
            if (++attempts == 1) throw CancellationException("cancelled") else "重试成功"
        }, { mode = it })
        coordinator.start(audio, VoiceInputMode.SEND, MessageData()) { deliveries++ }
        runCurrent()
        assertEquals(0, deliveries)
        assertNull(mode)
        coordinator.start(audio, VoiceInputMode.TRANSCRIBE, MessageData()) { deliveries++ }
        runCurrent()
        assertEquals(1, deliveries)
        assertNull(mode)
    }
}
