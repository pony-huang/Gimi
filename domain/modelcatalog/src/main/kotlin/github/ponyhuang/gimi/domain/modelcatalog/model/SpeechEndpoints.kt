package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 语音服务的接入端点。
 *
 * 端点由厂商自有、不可自定义：数据层（请求构造）与表现层（固定地址提示）共用同一份
 * 常量与构建函数，两边各写一份迟早会漂移。千问支持可选业务空间：ID 留空走官方默认
 * 域名（Key 所属的默认业务空间），填写后改写为业务空间专属域名（官方现行机制，最新
 * 型号强制要求），并由数据层额外携带 `X-DashScope-WorkSpace` 请求头兜底。
 */
object SpeechEndpoints {
    /** 阿里云百炼华北2（北京）默认端点（级联识别/合成 run-task 协议）。 */
    const val QWEN_AUDIO_CN = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"

    /** 阿里云百炼 Qwen-Audio Realtime 华北2（北京）默认端点；默认业务空间的 API Key 可免业务空间 ID 直连。 */
    const val QWEN_AUDIO_REALTIME_CN = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"

    /** OpenAI Realtime 默认端点；GA 直连，模型以查询参数携带，不需要 Beta 头。 */
    const val OPENAI_REALTIME_DEFAULT = "wss://api.openai.com/v1/realtime"

    /** OpenAI 语音（识别/合成）HTTP 基址；与聊天服务共用官方域名，无官方替代端点。 */
    const val OPENAI_SPEECH_BASE_URL = "https://api.openai.com/v1"

    /** 百炼业务空间专属域名（华北2）的主机后缀。 */
    private const val QWEN_WORKSPACE_HOST_SUFFIX = ".cn-beijing.maas.aliyuncs.com"

    /**
     * 级联识别/合成（run-task 协议）的接入地址。
     *
     * @param workspaceId 百炼业务空间 ID；空白视为未填写，走官方默认域名。
     */
    fun qwenInferenceUrl(workspaceId: String?): String = qwenWorkspaceUrl(QWEN_AUDIO_CN, workspaceId)

    /**
     * Qwen-Audio Realtime 的接入地址；业务空间规则同 [qwenInferenceUrl]。
     *
     * @param workspaceId 百炼业务空间 ID；空白视为未填写，走官方默认域名。
     */
    fun qwenRealtimeUrl(workspaceId: String?): String = qwenWorkspaceUrl(QWEN_AUDIO_REALTIME_CN, workspaceId)

    /** 业务空间 ID 非空时把默认域名主机改写为业务空间专属主机，路径保持不变。 */
    private fun qwenWorkspaceUrl(defaultUrl: String, workspaceId: String?): String {
        val id = workspaceId?.trim().orEmpty()
        if (id.isEmpty()) return defaultUrl
        val rest = defaultUrl.removePrefix("wss://")
        return "wss://" + id + QWEN_WORKSPACE_HOST_SUFFIX + "/" + rest.substringAfter('/')
    }
}
