package github.ponyhuang.gimi.core.network

import javax.inject.Qualifier

/**
 * 语音服务 WebSocket 专用客户端的限定符。
 *
 * 它与共享的 HTTP 客户端类型相同但参数不同（没有读超时、带心跳），
 * 必须用限定符区分，否则 Hilt 会判定为重复绑定。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpeechWebSocketClient
