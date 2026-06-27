package github.ponyhuang.asssistantai.agent.tools.systems

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.permission.LocationPermissionActivity
import github.ponyhuang.asssistantai.agent.tools.intents.IntentActionQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the device's geographic location from the system LocationManager. */
@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @Tool(
        name = "get_current_location",
        description = "Returns the device's current geographic location (latitude, longitude, accuracy, and other available fields) read from enabled system location providers (fused, GPS, network, passive). Requires the ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION runtime permission, and the device's location services to be enabled.",
    )
    fun getCurrentLocation(
        @Param("If true, attempt to acquire a fresh location fix from the highest-accuracy enabled provider and fall back to the most recent cached fix if no fresh fix arrives within a short timeout. If false, return the most recent cached fix without requesting an update. Defaults to true.")
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
        description = "Launches the runtime prompt for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION, which are required to read the device location.",
    )
    fun requestLocationPermissions(): Map<String, Any> = queue.request(
        "Grant location access",
        "Allow access to device location for current-location queries.",
        Intent(context, LocationPermissionActivity::class.java),
    )

    @Tool(
        name = "is_location_enabled",
        description = "Returns whether at least one system location provider (fused, GPS, network) is currently enabled on the device.",
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
        Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
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
    private fun tryFreshFix(manager: LocationManager, enabledProviders: List<String>): Location? {
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
    private fun awaitSingleLocationUpdate(manager: LocationManager, provider: String): Location? {
        val looper = Looper.myLooper() ?: Looper.getMainLooper()
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Location>(1)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                holder[0] = location
                latch.countDown()
            }

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {
                latch.countDown()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val acquired = try {
            manager.requestSingleUpdate(provider, listener, looper)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!acquired) return null
        latch.await(FRESH_FIX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try {
            manager.removeUpdates(listener)
        } catch (_: Exception) {
            // Listener may already be detached — ignore.
        }
        return holder[0]
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
