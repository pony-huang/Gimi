package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/**
 * 厂商级联 TTS 内置音色目录的解析接口。
 *
 * 实时语音的内置音色不走这里——它由 [RealtimeVoiceSupport] 按厂商自报；
 * 级联 TTS 的音色优先来自厂商音色列表接口（[TtsVoiceProvider]），本目录只承载
 * 没有列表接口或列表不可用时的静态内置表。
 * 厂商表实现在 `:data:modelaccess`，随厂商适配器同目录维护。
 */
interface VoiceCatalogResolver {
    /** 按服务的级联 TTS 内置音色表；未收录服务返回空表。 */
    fun builtInVoices(serviceId: String?): List<TtsVoice>

    /**
     * 未配置音色时的默认音色：该服务（按模型收窄）内置表的第一个音色。
     * 千问的音色表按模型组织，服务级内置表为空，因此默认音色必须带上模型 ID；
     * 服务无内置表返回 null，调用方按空值交给服务端默认。
     */
    fun builtInDefaultVoice(serviceId: String?, modelId: String?): TtsVoice?

    /**
     * 内置表中包含该音色 ID 的服务 ID；所有服务的内置表都不含时返回 null。
     *
     * 与 [builtInVoices] 不同，这里按「音色 ID 的厂商命名空间」语义跨服务查询
     * （千问也取全部模型的并集）。只用于判定音色是否确定属于其他厂商——命中
     * 其他厂商封闭内置表的音色必被该厂商拒绝，可安全判失效；复刻音色等开放集
     * ID 落在 null 分支，调用方按无法核验处理。
     */
    fun builtInVoiceOwner(voiceId: String): String?
}
