package github.ponyhuang.gimi.data.voice.remote

/** 百炼 WebSocket 事件的手写样本；协议文本来自官方文档，测试里刻意不依赖生产代码的构造逻辑。 */
internal object DashScopeTestEvents {
    fun taskStarted(): String = """
        {"header":{"task_id":"t-1","event":"task-started","attributes":{}},"payload":{}}
    """.trimIndent()

    fun taskFinished(): String = """
        {"header":{"task_id":"t-1","event":"task-finished","attributes":{}},"payload":{"usage":{"characters":2}}}
    """.trimIndent()

    fun taskFailed(code: String, message: String): String = """
        {"header":{"task_id":"t-1","event":"task-failed","error_code":"$code","error_message":"$message"},"payload":{}}
    """.trimIndent()

    fun resultGenerated(text: String, sentenceId: Int = 1, sentenceEnd: Boolean = true): String = """
        {"header":{"task_id":"t-1","event":"result-generated","attributes":{}},"payload":{"output":{"sentence":{
          "sentence_id":$sentenceId,"begin_time":0,"end_time":800,"text":"$text",
          "sentence_end":$sentenceEnd,"sentence_begin":false,"words":[]}}}}
    """.trimIndent()

    /** 语音合成的子事件：服务端在它之后紧跟一个二进制音频帧。 */
    fun sentenceSynthesis(): String = """
        {"header":{"task_id":"t-1","event":"result-generated","attributes":{}},"payload":{"output":{
          "sentence":{"index":0,"words":[]},"type":"sentence-synthesis"}}}
    """.trimIndent()
}
