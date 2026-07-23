package github.ponyhuang.asssistantai.assistant

import android.content.Intent
import android.speech.RecognitionService

/**
 * VoiceInteractionService metadata 要求声明 recognitionService；
 * 本应用使用云端 STT，这里提供满足系统约束的最小实现。
 */
class AssistantStubRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) = Unit

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
