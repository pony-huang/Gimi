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
 * @property feelsLikeC 体感温度，单位摄氏度；缺失时由界面回退到当前温度。
 * @property humidityPercent 相对湿度，范围 0..100。
 * @property windDirection 风向短文本。
 * @property windSpeedKph 风速，单位千米每小时。
 * @property precipitationPercent 当前降水概率，范围 0..100。
 * @property uvIndex 紫外线指数。
 * @property airQuality 空气质量短文本。
 */
internal data class WeatherCurrent(
    val conditionCode: WeatherConditionCode,
    val conditionText: String?,
    val temperatureC: Int,
    val feelsLikeC: Int?,
    val humidityPercent: Int?,
    val windDirection: String?,
    val windSpeedKph: Int?,
    val precipitationPercent: Int?,
    val uvIndex: Int?,
    val airQuality: String?,
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
                feelsLikeC = map.int("feelsLikeC"),
                humidityPercent = map.int("humidityPercent")?.clampPercent(),
                windDirection = map.string("windDirection"),
                windSpeedKph = map.int("windSpeedKph")?.coerceAtLeast(0),
                precipitationPercent = map.int("precipitationPercent")?.clampPercent(),
                uvIndex = map.int("uvIndex")?.coerceAtLeast(0),
                airQuality = map.string("airQuality"),
            )
        }
    }
}

/**
 * 小时级天气项。
 *
 * @property timeLabel 本地时间短标签，例如 `15时` 或 `15:00`。
 * @property conditionCode 标准天气码值。
 * @property conditionText 供应商天气文案，仅在码值未知时展示。
 * @property temperatureC 温度，单位摄氏度。
 * @property precipitationPercent 降水概率，范围 0..100。
 */
internal data class WeatherHour(
    val timeLabel: String,
    val conditionCode: WeatherConditionCode,
    val conditionText: String?,
    val temperatureC: Int,
    val precipitationPercent: Int?,
) {
    companion object {
        fun fromA2ui(value: Any?): WeatherHour? {
            val map = value.asStringMap() ?: return null
            return WeatherHour(
                timeLabel = map.string("timeLabel") ?: return null,
                conditionCode = WeatherConditionCode.fromWire(
                    map.string("conditionCode") ?: map.string("conditionText"),
                ),
                conditionText = map.string("conditionText"),
                temperatureC = map.int("temperatureC") ?: return null,
                precipitationPercent = map.int("precipitationPercent")?.clampPercent(),
            )
        }
    }
}

/**
 * 单日天气预报。
 *
 * @property date ISO 本地日期，例如 `2026-10-18`；无法解析时界面使用序号兜底。
 * @property conditionCode 标准天气码值。
 * @property conditionText 供应商天气文案，仅在码值未知时展示。
 * @property highC 最高温度，单位摄氏度。
 * @property lowC 最低温度，单位摄氏度。
 * @property precipitationPercent 降水概率，范围 0..100。
 * @property sunrise 日出本地时间，格式 HH:mm。
 * @property sunset 日落本地时间，格式 HH:mm。
 */
internal data class WeatherDay(
    val date: String?,
    val conditionCode: WeatherConditionCode,
    val conditionText: String?,
    val highC: Int,
    val lowC: Int,
    val precipitationPercent: Int?,
    val sunrise: String?,
    val sunset: String?,
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
                sunrise = map.string("sunrise"),
                sunset = map.string("sunset"),
            )
        }
    }
}

/**
 * 一天与 N 天天气组件共用的唯一输入。
 *
 * @property location 城市或地点名称。
 * @property updatedAt 观测更新时间；为空时界面隐藏更新时间。
 * @property current 当前天气观测。
 * @property hourly 小时级预报，最多消费 24 项。
 * @property daily 逐日预报，从今天开始，最多消费 15 项。
 */
internal data class WeatherCardData(
    val location: String,
    val updatedAt: String?,
    val current: WeatherCurrent,
    val hourly: List<WeatherHour>,
    val daily: List<WeatherDay>,
) {
    companion object {
        const val MAX_FORECAST_DAYS = 15
        const val MAX_HOURLY_ITEMS = 24

        /**
         * 将 A2UI 动态对象解析为强类型天气数据，并在入口处执行数量上限。
         *
         * 任一日预报缺少最高/最低温度时会被跳过；若最终没有可用日预报则返回 `null`，
         * 让 A2UI 运行时向模型报告结构错误并保留现有错误恢复链路。
         */
        fun fromA2ui(value: Any): WeatherCardData? {
            if (value is WeatherCardData) return value
            val map = value.asStringMap() ?: return null
            val location = map.string("location") ?: return null
            val current = WeatherCurrent.fromA2ui(map["current"]) ?: return null
            val daily = map.list("daily")
                .mapNotNull { WeatherDay.fromA2ui(it) }
                .take(MAX_FORECAST_DAYS)
            if (daily.isEmpty()) return null
            val hourly = map.list("hourly")
                .mapNotNull { WeatherHour.fromA2ui(it) }
                .take(MAX_HOURLY_ITEMS)
            return WeatherCardData(
                location = location,
                updatedAt = map.string("updatedAt"),
                current = current,
                hourly = hourly,
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
        "feelsLikeC" to A2uiNumberSchema("Feels-like temperature in Celsius."),
        "humidityPercent" to A2uiNumberSchema("Relative humidity from 0 to 100."),
        "windDirection" to A2uiStringSchema("Short wind direction label."),
        "windSpeedKph" to A2uiNumberSchema("Wind speed in kilometres per hour."),
        "precipitationPercent" to A2uiNumberSchema("Precipitation probability from 0 to 100."),
        "uvIndex" to A2uiNumberSchema("UV index."),
        "airQuality" to A2uiStringSchema("Short air-quality label."),
    ),
    required = setOf("temperatureC"),
)

private val hourlyWeatherSchema = A2uiObjectSchema(
    description = "One hourly forecast item.",
    properties = mapOf(
        "timeLabel" to A2uiStringSchema("Local time label, for example 15:00."),
        "conditionCode" to conditionCodeSchema,
        "conditionText" to A2uiStringSchema("Provider condition text used only when code is unknown."),
        "temperatureC" to A2uiNumberSchema("Temperature in Celsius."),
        "precipitationPercent" to A2uiNumberSchema("Precipitation probability from 0 to 100."),
    ),
    required = setOf("timeLabel", "temperatureC"),
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
        "sunrise" to A2uiStringSchema("Local sunrise time, format HH:mm."),
        "sunset" to A2uiStringSchema("Local sunset time, format HH:mm."),
    ),
    required = setOf("highC", "lowC"),
)

private val weatherPayloadSchema = A2uiObjectSchema(
    description = "Unified weather payload shared by WeatherToday and WeatherForecast.",
    properties = mapOf(
        "location" to A2uiStringSchema("City or place name."),
        "updatedAt" to A2uiStringSchema("Local observation time, preferably HH:mm."),
        "current" to currentWeatherSchema,
        "hourly" to A2uiArraySchema(
            description = "Hourly forecasts in chronological order.",
            items = hourlyWeatherSchema,
            maxItems = WeatherCardData.MAX_HOURLY_ITEMS,
        ),
        "daily" to A2uiArraySchema(
            description = "Daily forecasts starting with today, at most 15 items.",
            items = dailyWeatherSchema,
            minItems = 1,
            maxItems = WeatherCardData.MAX_FORECAST_DAYS,
        ),
    ),
    required = setOf("location", "current", "daily"),
)

/**
 * 两个天气组件共用的 A2UI 属性定义。
 *
 * schema 明确要求 `location`、`current`、`daily`，并在数组层限制逐日最多 15 项、
 * 小时最多 24 项；运行时解析会再次执行同样的上限，防止模型绕过 schema 传入超量数据。
 * 属性本身同时允许内联天气对象、数据模型绑定和函数调用三种动态值形态。
 */
internal val WeatherA2uiProperty = A2uiProperty.dynamicCustom(
    key = "weather",
    schema = A2uiAnySchema(
        description = "Complete unified weather object or a dynamic binding such as " +
            "{\"path\":\"/weather\"}. Both WeatherToday and WeatherForecast use this same property.",
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
    safeCast = { WeatherCardData.fromA2ui(it) },
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
