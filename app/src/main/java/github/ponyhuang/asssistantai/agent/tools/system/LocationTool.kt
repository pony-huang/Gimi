package github.ponyhuang.asssistantai.agent.tools.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.Settings
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.permission.LocationPermissionActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Reads the device's geographic location. */
@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @Tool(
        name = "get_current_location",
        description = "Returns the device's current geographic location, including latitude, longitude, and accuracy. Requires location permission, enabled location services, and user confirmation.",
        requireConfirmation = true,
    )
    suspend fun getCurrentLocation(
        @Param("If true, requests a fresh location update; otherwise returns the cached value. Defaults to true.")
        preferFreshFix: Boolean? = true,
    ): Map<String, Any> {
        if (!hasLocationPermission()) return locationPermissionError()
        val manager = locationManager ?: return serviceUnavailableError()
        val enabledProviders = tryGetEnabledProviders(manager)
        if (enabledProviders.isEmpty()) return noProviderEnabledError()

        val freshFix = if (preferFreshFix != false) {
            tryFreshFix(manager, enabledProviders)
        } else {
            null
        }
        val location = freshFix ?: enabledProviders
            .mapNotNull { provider -> safelyGetLastKnownLocation(manager, provider) }
            .maxByOrNull { it.time }
            ?: return mapOf(
                "success" to false,
                "error" to "No location fix is available yet. Move to an open area or enable GPS and try again.",
            )
        return locationState(location, freshFix != null)
    }

    @Tool(
        name = "request_location_permissions",
        description = "Prompts the user to grant location permission, required to read the device location.",
    )
    fun requestLocationPermissions(): Map<String, Any> = queue.request(
        "Grant location access",
        "Allow access to device location for current-location queries.",
        Intent(context, LocationPermissionActivity::class.java),
    )

    @Tool(
        name = "is_location_enabled",
        description = "Returns whether any location service is currently enabled.",
    )
    fun isLocationEnabled(): Map<String, Any> {
        val manager = locationManager ?: return mapOf(
            "success" to false,
            "error" to "Location service is unavailable on this device.",
        )
        val enabled = tryGetEnabledProviders(manager)
        return mapOf(
            "success" to true,
            "enabled" to enabled.isNotEmpty(),
            "enabledProviders" to enabled,
        )
    }

    @Tool(
        name = "open_location_settings",
        description = "Opens the system Location settings page so the user can turn on location services.",
    )
    fun openLocationSettings(): Map<String, Any> = queue.request(
        "Open Location settings",
        "Open system Location settings to enable location services.",
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun hasLocationPermission(): Boolean {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun tryGetEnabledProviders(manager: LocationManager): List<String> = try {
        manager.getProviders(true).toList()
    } catch (_: SecurityException) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryFreshFix(
        manager: LocationManager,
        enabledProviders: List<String>,
    ): Location? {
        if (!hasLocationPermission()) return null
        val preferred = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter { it in enabledProviders }
        for (provider in preferred) {
            val fix = awaitSingleLocationUpdate(manager, provider)
            if (fix != null) return fix
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleLocationUpdate(
        manager: LocationManager,
        provider: String,
    ): Location? = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    context.mainExecutor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: SecurityException) {
                continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                continuation.resume(null)
            }
        }
    }

    private fun safelyGetLastKnownLocation(manager: LocationManager, provider: String): Location? = try {
        manager.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
        null
    }

    private fun locationState(location: Location, freshFix: Boolean): Map<String, Any> = mapOf(
        "success" to true,
        "latitude" to location.latitude,
        "longitude" to location.longitude,
        "accuracyMeters" to location.accuracy.toDouble(),
        "altitudeMeters" to location.altitude,
        "bearingDegrees" to location.bearing.toDouble(),
        "speedMetersPerSecond" to location.speed.toDouble(),
        "timeMillis" to location.time,
        "provider" to (location.provider ?: ""),
        "freshFix" to freshFix,
        "hasAltitude" to location.hasAltitude(),
        "hasBearing" to location.hasBearing(),
        "hasSpeed" to location.hasSpeed(),
    )

    private fun locationPermissionError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "Location permission is required. Call request_location_permissions and grant location access.",
    )

    private fun serviceUnavailableError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "Location service is unavailable on this device.",
    )

    private fun noProviderEnabledError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "No location provider is enabled. Call open_location_settings and turn on Location in system settings.",
    )

    private companion object {
        const val FRESH_FIX_TIMEOUT_MS = 1500L
    }
}
