package github.ponyhuang.asssistantai.voice

import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConfirmationTest {
    @Test
    fun confirmationOnlyAcceptsExplicitAllowWords() {
        val (confirm, reject) = WakeModelCatalog.Chinese.let { it.confirmWords to it.rejectWords }
        assertTrue(isVoiceConfirmationApproved("确认执行", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("允许", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("执行", confirm, reject))
    }

    @Test
    fun rejectionWordsTakePriorityAndAmbiguousSpeechFailsClosed() {
        val (confirm, reject) = WakeModelCatalog.Chinese.let { it.confirmWords to it.rejectWords }
        assertEquals(false, isVoiceConfirmationApproved("不要执行", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("取消", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("好的", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("", confirm, reject))
    }

    @Test
    fun englishConfirmationUsesEnglishWordLists() {
        val (confirm, reject) = WakeModelCatalog.English.let { it.confirmWords to it.rejectWords }
        assertTrue(isVoiceConfirmationApproved("confirm", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("yes, proceed", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("cancel", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("don't do it", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("maybe", confirm, reject))
    }

}
