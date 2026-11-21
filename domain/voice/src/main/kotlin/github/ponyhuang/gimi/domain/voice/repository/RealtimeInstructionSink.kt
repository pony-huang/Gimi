package github.ponyhuang.gimi.domain.voice.repository

/** 原生实时连接的人设更新能力，保留服务端会话历史并用于后续回复。 */
interface RealtimeInstructionSink {
    /** 仅更新指令，保持音色、音频配置和现有会话内容。 */
    suspend fun updateInstructions(instructions: String)
}
