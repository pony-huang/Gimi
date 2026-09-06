package github.ponyhuang.gimi.data.agent.debug

/**
 * ADK Development WebServer 的启动契约。
 *
 * debug 变体绑定真实实现（Ktor/Netty + Dev UI），PC 浏览器可通过局域网访问
 * `http://<手机IP>:<DEFAULT_PORT>/dev-ui` 观察 agent 会话；release 变体绑定空实现，
 * 保证正式打包不携带任何 webserver 代码。
 */
interface AgentDebugWebServer {

    /**
     * 启动 webserver，幂等；已在启动中或运行中时为空操作。
     * Agent 构建与服务监听均在后台完成，失败仅记录日志，不影响宿主进程。
     */
    fun start()

    /** 停止 webserver 并释放监听端口，未启动时为空操作。 */
    fun stop()

    companion object {
        /** Dev UI 监听端口。 */
        const val DEFAULT_PORT: Int = 8080
    }
}
