package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import kotlin.math.roundToInt

/** 天气现象的标准码值；未知供应商文案保留在 [WeatherCurrent.conditionText] 中作为兜底。 */
internal enum class WeatherConditionCode {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    THUNDERSTORM,
    SNOW,
    FOG,
    WIND,
    OTHER,
    ;

    companion object {
        /**
         * 兼容常见供应商码值和少量中英文文案。
         *
         * 组件只依赖稳定码值做图标与本地化，供应商原始文案仅用于未知天气的最后兜底。
         */
        fun fromWire(value: String?): WeatherConditionCode = when (value?.trim()?.lowercase()) {
            "clear", "sunny", "晴" -> CLEAR
            "partly_cloudy", "partly-cloudy", "partly cloudy", "晴间多云" -> PARTLY_CLOUDY
            "cloudy", "overcast", "多云", "阴" -> CLOUDY
            "rain", "shower", "阵雨", "小雨", "中雨", "大雨" -> RAIN
            "thunderstorm", "storm", "雷阵雨", "雷雨" -> THUNDERSTORM
            "snow", "sleet", "小雪", "中雪", "大雪" -> SNOW
            "fog", "mist", "haze", "雾", "霾" -> FOG
            "wind", "windy", "大风" -> WIND
            else -> OTHER
        }
    }
}

/**
 * 当前天气观测值。
 *
 * @property conditionCode 标准天气码值，驱动图标和本地化文案。
 * @property conditionText 供应商返回的天气文案，仅在码值未知时展示。
 * @property temperatureC 当前温度，单位摄氏度。
 * @property humidityPercent 相对湿度，范围 0..100。
 * @property windSpeedKph 风速，单位千米每小时。
 * @property precipitationPercent 当前降水概率，范围 0..100。
 */
internal data class WeatherCurrent(
    val conditionCode: WeatherConditionCode,
    val conditionText: String?,
    val temperatureC: Int,
    val humidityPercent: Int?,
    val windSpeedKph: Int?,
    val precipitationPercent: Int?,
) {
    companion object {
        fun fromA2ui(value: Any?): WeatherCurrent? {
            val map = value.asStringMap() ?: return null
            val temperatureC = map.int("temperatureC") ?: return null
            val rawCondition = map.string("conditionCode") ?: map.string("conditionText")
            return WeatherCurrent(
                conditionCode = WeatherConditionCode.fromWire(rawCondition),
                conditionText = map.string("conditionText"),
                temperatureC = temperatureC,
                humidityPercent = map.int("humidityPercent")?.clampPercent(),
                windSpeedKph = map.int("windSpeedKph")?.coerceAtLeast(0),
                precipitationPercent = map.int("precipitationPercent")?.clampPercent(),
            )
        }
    }
}

/**
 * 单日天气预报。
 *
 * @property date 供应商当地的当天日期，例如 `2026-10-25`。
 * @property conditionCode 标准天气码值。
 * @property conditionText 供应商天气文案，仅在码值未知时展示。
 * @property highC 最高温度，单位摄氏度。
 * @property lowC 最低温度，单位摄氏度。
 * @property precipitationPercent 降水概率，范围 0..100。
 */
internal data class WeatherDay(
    val date: String?,
    val conditionCode: WeatherConditionCode,
    val conditionText: String?,
    val highC: Int,
    val lowC: Int,
    val precipitationPercent: Int?,
) {
    companion object {
        fun fromA2ui(value: Any?): WeatherDay? {
            val map = value.asStringMap() ?: return null
            val highC = map.int("highC") ?: return null
            val lowC = map.int("lowC") ?: return null
            return WeatherDay(
                date = map.string("date"),
                conditionCode = WeatherConditionCode.fromWire(
                    map.string("conditionCode") ?: map.string("conditionText"),
                ),
                conditionText = map.string("conditionText"),
                highC = highC,
                lowC = lowC,
                precipitationPercent = map.int("precipitationPercent")?.clampPercent(),
            )
        }
    }
}

/**
 * 当天天气组件的输入；不存储逐时或多日预报。
 *
 * @property location 城市或地点名称。
 * @property updatedAt 观测更新时间；为空时界面隐藏更新时间。
 * @property current 当前天气观测。
 * @property daily 当天高低温，恰好一项；首项不合法时拒绝整个输入，避免误用后续日期。
 */
internal data class WeatherWidgetData(
    val location: String,
    val updatedAt: String?,
    val current: WeatherCurrent,
    val daily: List<WeatherDay>,
) {
    companion object {
        const val MAX_FORECAST_DAYS = 1

        /**
         * 将 A2UI 动态对象解析为强类型天气数据，并在入口处执行数量上限。
         *
         * 当天预报缺少最高/最低温度时返回 `null`，不回退到后续日期，
         * 让 A2UI 运行时向模型报告结构错误并保留现有错误恢复链路。
         */
        fun fromA2ui(value: Any): WeatherWidgetData? {
            if (value is WeatherWidgetData) return value
            val map = value.asStringMap() ?: return null
            val location = map.string("location") ?: return null
            val current = WeatherCurrent.fromA2ui(map["current"]) ?: return null
            val daily = map.list("daily")
                .take(MAX_FORECAST_DAYS)
                .mapNotNull { WeatherDay.fromA2ui(it) }
            if (daily.isEmpty()) return null
            return WeatherWidgetData(
                location = location,
                updatedAt = map.string("updatedAt"),
                current = current,
                daily = daily,
            )
        }
    }
}

private val conditionCodeSchema = A2uiStringSchema(
    "Standard code: clear, partly_cloudy, cloudy, rain, thunderstorm, snow, fog, or wind.",
)

private val currentWeatherSchema = A2uiObjectSchema(
    description = "Current observation. Temperature is required.",
    properties = mapOf(
        "conditionCode" to conditionCodeSchema,
        "conditionText" to A2uiStringSchema("Provider condition text used only when code is unknown."),
        "temperatureC" to A2uiNumberSchema("Current temperature in Celsius."),
        "humidityPercent" to A2uiNumberSchema("Relative humidity from 0 to 100."),
        "windSpeedKph" to A2uiNumberSchema("Wind speed in kilometres per hour."),
        "precipitationPercent" to A2uiNumberSchema("Precipitation probability from 0 to 100."),
    ),
    required = setOf("temperatureC"),
)

private val dailyWeatherSchema = A2uiObjectSchema(
    description = "One daily forecast item.",
    properties = mapOf(
        "date" to A2uiStringSchema("ISO local date, for example 2026-10-18."),
        "conditionCode" to conditionCodeSchema,
        "conditionText" to A2uiStringSchema("Provider condition text used only when code is unknown."),
        "highC" to A2uiNumberSchema("Daily high temperature in Celsius."),
        "lowC" to A2uiNumberSchema("Daily low temperature in Celsius."),
        "precipitationPercent" to A2uiNumberSchema("Precipitation probability from 0 to 100."),
    ),
    required = setOf("highC", "lowC"),
)

private val weatherPayloadSchema = A2uiObjectSchema(
    description = "Today-only weather payload.",
    properties = mapOf(
        "location" to A2uiStringSchema("City or place name."),
        "updatedAt" to A2uiStringSchema("Local observation time, preferably HH:mm."),
        "current" to currentWeatherSchema,
        "daily" to A2uiArraySchema(
            description = "Exactly one item containing today's high and low temperatures.",
            items = dailyWeatherSchema,
            minItems = 1,
            maxItems = WeatherWidgetData.MAX_FORECAST_DAYS,
        ),
    ),
    required = setOf("location", "current", "daily"),
)

/**
 * 当天天气组件的 A2UI 属性定义。
 *
 * schema 明确要求 `location`、`current`、`daily`，并在数组层限制仅一天；
 * 运行时只读取首项，避免把后续日期当作当天。
 * 属性本身同时允许内联天气对象、数据模型绑定和函数调用三种动态值形态。
 */
internal val WeatherA2uiProperty = A2uiProperty.dynamicCustom(
    key = "weather",
    schema = A2uiAnySchema(
        description = "Today-only weather object or a dynamic binding such as " +
            "{\"path\":\"/weather\"}. Used by WeatherToday only.",
        keywords = listOf(
            A2uiSchemaKeyword.OneOf(
                listOf(
                    weatherPayloadSchema,
                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                    A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                ),
            ),
        ),
    ),
    safeCast = { WeatherWidgetData.fromA2ui(it) },
    required = true,
)

private fun Any?.asStringMap(): Map<*, *>? = this as? Map<*, *>

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)

private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
    is Number -> value.toDouble().roundToInt()
    is String -> value.trim().toDoubleOrNull()?.roundToInt()
    else -> null
}

private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any?>()

private fun Int.clampPercent(): Int = coerceIn(0, 100)
