package github.ponyhuang.gimi.data.recommendation

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationContextSource
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 单个只读上下文来源；失败由聚合器隔离。 */
fun interface RecommendationContextContributor {
    suspend fun read(): Map<String, String>
}

/** 将独立上下文来源合并为单次内存快照。 */
@Singleton
class RecommendationContextCollector @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards RecommendationContextContributor>,
) : RecommendationContextSource {
    override suspend fun currentContext(): RecommendationContext {
        val values = linkedMapOf<String, String>()
        contributors.forEach { contributor ->
            runCatching { contributor.read() }.getOrNull()?.let(values::putAll)
        }
        return RecommendationContext(values)
    }
}

/** 读取无需交互的 Android 状态；敏感字段仅在已有授权时加入。 */
@Singleton
class AndroidRecommendationContextContributor @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecommendationContextContributor {
    override suspend fun read(): Map<String, String> = buildMap {
        val now = ZonedDateTime.now()
        put("localDateTime", now.toString())
        put("timeZone", now.zone.id)
        put("locale", Locale.getDefault().toLanguageTag())
        addPowerState(this)
        addNetworkState(this)
        addCachedLocation(this)
        addRecentForegroundApp(this)
    }

    private fun addPowerState(target: MutableMap<String, String>) {
        val battery = context.getSystemService(BatteryManager::class.java) ?: return
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (capacity in 0..100) target["batteryPercent"] = capacity.toString()
        val power = context.getSystemService(PowerManager::class.java)
        target["powerSaveMode"] = (power?.isPowerSaveMode == true).toString()
    }

    private fun addNetworkState(target: MutableMap<String, String>) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return
        target["network"] = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        target["networkMetered"] = manager.isActiveNetworkMetered.toString()
    }

    private fun addCachedLocation(target: MutableMap<String, String>) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val location = runCatching {
            manager.getProviders(true).mapNotNull(manager::getLastKnownLocation)
                .maxByOrNull { it.time }
        }.getOrNull() ?: return
        target["cachedLocation"] = "${location.latitude},${location.longitude}; accuracy=${location.accuracy}m"
    }

    private fun addRecentForegroundApp(target: MutableMap<String, String>) {
        if (!hasUsageStatsAccess(context)) return
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(end - APP_LOOKBACK_MILLIS, end)
        val event = UsageEvents.Event()
        var packageName: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (
                event.packageName != context.packageName &&
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                packageName = event.packageName
            }
        }
        val recentPackage = packageName ?: return
        val label = runCatching {
            val info = context.packageManager.getApplicationInfo(recentPackage, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrNull() ?: recentPackage
        target["recentForegroundApp"] = label
    }

    private companion object {
        const val APP_LOOKBACK_MILLIS: Long = 30 * 60 * 1_000L
    }
}

/** 检查系统特殊授权，不触发任何 UI。 */
fun hasUsageStatsAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        context.applicationInfo.uid,
        context.packageName,
    ) == AppOpsManager.MODE_ALLOWED
}
