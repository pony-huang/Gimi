package github.ponyhuang.asssistantai.domain.conversation.model

data class ConversationToolConfiguration(
    val enabledLocalToolIds: Set<String> = emptySet(),
    val enabledMcpServerIds: Set<String> = emptySet(),
    val enabledOfficialToolIdsByService: Map<String, Set<String>> = emptyMap(),
) {
    fun enabledOfficialToolIds(serviceId: String): Set<String> =
        enabledOfficialToolIdsByService[serviceId].orEmpty()

    fun initializeOfficialTools(
        serviceId: String,
        supportedToolIds: Set<String>,
    ): ConversationToolConfiguration {
        if (serviceId in enabledOfficialToolIdsByService) return this
        return copy(
            enabledOfficialToolIdsByService =
                enabledOfficialToolIdsByService + (serviceId to supportedToolIds),
        )
    }

    fun setOfficialToolEnabled(
        serviceId: String,
        toolId: String,
        enabled: Boolean,
    ): ConversationToolConfiguration {
        val current = enabledOfficialToolIds(serviceId)
        val updated = if (enabled) current + toolId else current - toolId
        return copy(
            enabledOfficialToolIdsByService =
                enabledOfficialToolIdsByService + (serviceId to updated),
        )
    }

    fun sanitize(
        availableLocalToolIds: Set<String>,
        availableMcpServerIds: Set<String>,
    ): ConversationToolConfiguration = copy(
        enabledLocalToolIds = enabledLocalToolIds intersect availableLocalToolIds,
        enabledMcpServerIds = enabledMcpServerIds intersect availableMcpServerIds,
    )
}
