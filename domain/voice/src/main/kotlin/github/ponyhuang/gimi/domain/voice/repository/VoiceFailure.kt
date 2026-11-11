package github.ponyhuang.gimi.domain.voice.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 语音能力失败的分类。
 *
 * 数据层只报告"哪一类失败"，文案由界面按分类本地化。这样同一类失败在合成、识别、
 * 连接测试三个入口显示同一套措辞，也不会把厂商返回的原始错误直接抛给用户。
 */
enum class VoiceFailureKind {
    /** 未配置可用服务或模型。 */
    NotConfigured,

    /** 该服务未接入所请求的能力。 */
    CapabilityNotSupported,

    /** 密钥无效或权限不足。 */
    Authentication,

    /** 网络不可达、连接中断或超时。 */
    Network,

    /** 服务返回了业务级错误（限流、配额、参数错误等）。 */
    Service,

    /** 其它未分类失败。 */
    Unknown,
}

/**
 * 语音失败的类型化描述。
 *
 * @property kind 失败分类。
 * @property detail 失败细节（厂商原始信息）；仅用于日志与排查，不直接展示给用户。
 */
data class VoiceFailure(
    val kind: VoiceFailureKind,
    val detail: String? = null,
)

/**
 * 语音调用失败的类型化异常。
 *
 * 继承 [IOException] 以保持既有调用方的 `catch (IOException)` 语义不变，
 * 同时把失败分类固化在异常上：界面据此选择文案，不必解析错误字符串。
 *
 * @property kind 失败分类。
 */
open class VoiceException(
    val kind: VoiceFailureKind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** 密钥无效或权限不足。 */
class VoiceAuthenticationException(message: String, cause: Throwable? = null) :
    VoiceException(VoiceFailureKind.Authentication, message, cause)

/** 网络不可达、连接中断或超时。 */
class VoiceNetworkException(message: String, cause: Throwable? = null) :
    VoiceException(VoiceFailureKind.Network, message, cause)

/** 服务返回了业务级错误。 */
class VoiceServiceException(message: String, cause: Throwable? = null) :
    VoiceException(VoiceFailureKind.Service, message, cause)

/**
 * 语音服务握手阶段失败。
 *
 * 语音服务通常在 WebSocket 握手时就校验密钥：HTTP 401/403 表示密钥无效或权限不足，
 * 与网络不可达是两类完全不同的用户问题，必须区分开。
 *
 * @property statusCode 握手被拒时的 HTTP 状态码。
 */
class VoiceHandshakeException(
    val statusCode: Int,
    message: String,
) : VoiceException(
    if (statusCode == 401 || statusCode == 403) VoiceFailureKind.Authentication else VoiceFailureKind.Service,
    message,
)

/**
 * 语音服务握手失败的统一分类映射。
 *
 * 传输内核只上报 HTTP 状态码与服务端原始响应详情（headers/body），不知道领域语义；
 * 各语音模块构造内核时统一传入本函数，保证握手失败在识别、合成、连接测试三个入口
 * 归为同一类失败。服务端详情只进日志，不改变分类。
 */
fun voiceHandshakeFailure(statusCode: Int, cause: Throwable, detail: String? = null): Throwable =
    VoiceHandshakeException(
        statusCode,
        buildString {
            append("语音服务握手失败（HTTP $statusCode）")
            if (!detail.isNullOrBlank()) append("：$detail")
        },
    )

/**
 * 把底层异常归类为语音失败。
 *
 * 未被识别的异常归入 [VoiceFailureKind.Unknown]：宁可显示通用提示，
 * 也不把厂商原始错误直接展示给用户。
 */
/**
 * 把任意异常翻译成 [VoiceFailure]。
 *
 * 未识别异常一律归为 [VoiceFailureKind.Unknown]，包括 CancellationException——
 * 调用方必须在捕获层先重抛取消异常再调用本函数，否则取消会被吞成业务失败。
 */
fun Throwable.toVoiceFailure(): VoiceFailure = when (this) {
    is VoiceCapabilityNotSupported -> VoiceFailure(VoiceFailureKind.CapabilityNotSupported, message)
    is VoiceException -> VoiceFailure(kind, message)
    is SocketTimeoutException, is UnknownHostException, is ConnectException ->
        VoiceFailure(VoiceFailureKind.Network, message)
    else -> VoiceFailure(VoiceFailureKind.Unknown, message)
}
