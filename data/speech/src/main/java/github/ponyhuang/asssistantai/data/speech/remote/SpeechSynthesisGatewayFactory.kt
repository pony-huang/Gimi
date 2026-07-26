package github.ponyhuang.asssistantai.data.speech.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the right [SpeechSynthesisGateway] for a given [SpeechSynthesisConfig].
 *
 * Routing by `baseUrl` host matches the network-side deployment: Minimax traffic
 * is served from `api.minimaxi.com`, everything else falls through to MiMo.
 * The factory is called once per `synthesize()` request so the active model
 * can change between calls without re-binding.
 */
@Singleton
class SpeechSynthesisGatewayFactory @Inject constructor(
    private val minimaxTtsGateway: MinimaxTtsGateway,
    private val miMoSpeechSynthesisGateway: MiMoSpeechSynthesisGateway,
) {
    fun create(config: SpeechSynthesisConfig): SpeechSynthesisGateway =
        if (config.baseUrl.contains(MINIMAX_HOST, ignoreCase = true)) {
            minimaxTtsGateway
        } else {
            miMoSpeechSynthesisGateway
        }

    private companion object {
        const val MINIMAX_HOST = "minimaxi.com"
    }
}