package com.apache.mobile.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.SavedLocation
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ubicación del móvil (GPS o red), sin Google Play Services. Cada vez que se
 * consigue se guarda en Ajustes para usarla después en segundo plano (el
 * resumen de buenos días no puede pedir el GPS con la app cerrada).
 */
object DeviceLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Ubicación actual (o la última conocida). null sin permiso o si el móvil no la sabe. */
    suspend fun current(context: Context = ApacheApp.get()): SavedLocation? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext ApacheApp.get().settings.lastLocation
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            providers.firstNotNullOfOrNull { provider -> withTimeoutOrNull(6_000) { request(manager, provider) } }
        } else null

        val location = fresh ?: providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        if (location == null) return@withContext ApacheApp.get().settings.lastLocation

        val saved = SavedLocation(location.latitude, location.longitude, placeName(context, location.latitude, location.longitude))
        ApacheApp.get().settings.lastLocation = saved
        saved
    }

    @Suppress("MissingPermission")
    private suspend fun request(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                manager.getCurrentLocation(provider, null, Executors.newSingleThreadExecutor()) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    /** Nombre del pueblo o ciudad (Geocoder de Android). null si no se puede. */
    @Suppress("DEPRECATION")
    fun placeName(context: Context, latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val address = Geocoder(context, Locale.forLanguageTag("es-ES")).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            ?: return null
        listOfNotNull(address.locality ?: address.subAdminArea, address.adminArea?.takeIf { it != address.locality })
            .distinct().joinToString(", ").ifBlank { null }
    }.getOrNull()
}
