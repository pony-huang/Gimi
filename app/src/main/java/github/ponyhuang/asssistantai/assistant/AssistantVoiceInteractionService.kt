package github.ponyhuang.asssistantai.assistant

import android.service.voice.VoiceInteractionService

/**
 * 默认数字助理入口服务。系统组件只负责入口：会话被唤起后立即转交
 * [AssistantOverlayActivity]，不读取 Assist 截图或内容。
 */
class AssistantVoiceInteractionService : VoiceInteractionService()
