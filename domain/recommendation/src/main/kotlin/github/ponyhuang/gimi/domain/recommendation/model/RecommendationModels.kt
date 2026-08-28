package github.ponyhuang.gimi.domain.recommendation.model

/** 推荐任务的能力分类，用于把稳定类别映射为本地化标签与图标。 */
enum class RecommendationCategory {
    REASONING,
    VISION,
    RESEARCH,
    WRITING,
    DEVICE,
    PRODUCTIVITY,
    GENERAL,
}

/**
 * 一条可直接作为用户消息发送的全局推荐。
 *
 * @property id 当前快照内的稳定标识。
 * @property prompt 展示并发送给 Agent 的完整任务文案。
 * @property category 受控能力分类。
 */
data class AgentRecommendation(
    val id: String,
    val prompt: String,
    val category: RecommendationCategory,
)

/**
 * 最近一次成功生成的全局推荐快照。
 *
 * @property items 固定五条、文案唯一且非空的推荐。
 * @property generatedAtEpochMillis 成功提交快照的 Unix 毫秒时间。
 */
data class RecommendationSnapshot(
    val items: List<AgentRecommendation>,
    val generatedAtEpochMillis: Long,
) {
    init {
        require(items.size == RECOMMENDATION_COUNT) { "A snapshot must contain exactly five recommendations." }
        val normalizedPrompts = items.map { item -> item.prompt.trim() }
        require(normalizedPrompts.all(String::isNotEmpty)) { "Recommendation prompts must not be blank." }
        require(normalizedPrompts.distinct().size == normalizedPrompts.size) {
            "Recommendation prompts must be unique."
        }
    }

    companion object {
        const val RECOMMENDATION_COUNT: Int = 5
    }
}

/**
 * 全局推荐更新设置。
 *
 * @property enabled 是否展示并安排更新推荐。
 * @property intervalHours WorkManager 周期的大致小时数。
 */
data class RecommendationSettings(
    val enabled: Boolean = true,
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
) {
    init {
        require(intervalHours in SUPPORTED_INTERVAL_HOURS) { "Unsupported recommendation interval: $intervalHours" }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS: Int = 2
        val SUPPORTED_INTERVAL_HOURS: List<Int> = listOf(1, 2, 6, 12, 24)
    }
}

/** 推荐刷新生命周期。 */
sealed interface RecommendationRefreshStatus {
    data object Idle : RecommendationRefreshStatus
    data object Scheduled : RecommendationRefreshStatus
    data object Refreshing : RecommendationRefreshStatus
}

/**
 * 推荐功能面向 UI 的完整全局状态。
 *
 * @property settings 当前持久化设置。
 * @property snapshot 最近一次成功快照；尚未生成时为空。
 * @property refreshStatus 当前刷新生命周期。
 * @property lastError 最近一次失败的安全展示信息。
 */
data class RecommendationState(
    val settings: RecommendationSettings = RecommendationSettings(),
    val snapshot: RecommendationSnapshot? = null,
    val refreshStatus: RecommendationRefreshStatus = RecommendationRefreshStatus.Idle,
    val lastError: String? = null,
)

/**
 * 输入推荐模型的单个工具能力摘要。
 *
 * @property id 工具稳定名称。
 * @property source 工具来源，例如 local、MCP 或 plugin。
 * @property description 不含凭据和参数值的简短能力说明。
 */
data class RecommendationCapability(
    val id: String,
    val source: String,
    val description: String,
)

/**
 * 单次生成使用的临时只读上下文。
 *
 * @property values 经过最小化的键值对；该对象不得持久化。
 */
data class RecommendationContext(
    val values: Map<String, String>,
)

/**
 * 专用推荐生成器输入。
 *
 * @property systemInstruction 当前助手系统指令。
 * @property capabilities 全局当前启用工具能力摘要。
 * @property context 当前已授权的临时设备上下文。
 */
data class RecommendationGenerationInput(
    val systemInstruction: String,
    val capabilities: List<RecommendationCapability>,
    val context: RecommendationContext,
)

