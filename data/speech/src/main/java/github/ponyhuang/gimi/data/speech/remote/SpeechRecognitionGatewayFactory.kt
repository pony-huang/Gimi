package github.ponyhuang.gimi.data.speech.remote

/**
 * Selects the speech-recognition wire protocol for the configured model service.
 */
class SpeechRecognitionGatewayFactory(
    private val openAiCompatibleGateway: OpenAiCompatibleSpeechRecognitionGateway,
    private val minimaxGateway: MinimaxSpeechRecognitionGateway,
) {
    fun create(config: SpeechRecognitionConfig): SpeechRecognitionGateway =
        if (config.serviceId == MINIMAX_SERVICE_ID) minimaxGateway else openAiCompatibleGateway

    private companion object {
        const val MINIMAX_SERVICE_ID = "minimax"
    }
}
