package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult

/**
 * 语音服务的连通性探测能力。
 *
 * 语音服务没有模型目录接口，连接测试只能建立一次协议握手。厂商在握手阶段即校验密钥，
 * 因此探测不会产生调用费用，也不会创建计费任务。
 *
 * 与其它语音能力一样按服务标识注册：某个服务没有实现探测时，测试连接应明确报告
 * "该服务不支持连接测试"，而不是拿别的厂商的协议去握手。
 */
interface SpeechConnectionProbe : CapabilityAdapter {
    /** 探测是服务级握手（校验密钥），与具体能力无关；实现按服务注册，不限定 capabilities。 */

    /**
     * 探测语音服务是否可用。
     *
     * @param service 当前服务配置；端点由厂商自有的服务据此构造请求地址（含业务空间），
     *   端点可配置的服务使用其配置的基址。
     */
    suspend fun probe(service: LLMModelSetting): ServiceConnectionResult
}
