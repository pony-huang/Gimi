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
}
