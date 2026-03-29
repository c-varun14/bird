package com.example.projectbird.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FusedLocationProvider(
    context: Context,
) : LocationProvider {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    @Volatile
    private var latestSnapshot: LocationSnapshot? = null

    private var isUpdating: Boolean = false

    private val request: LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UPDATE_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { latestSnapshot = it.toSnapshot() }
        }
    }

    override suspend fun start() {
        if (isUpdating || !hasLocationPermission()) return
        startUpdatesInternal()
    }

    override suspend fun stop() {
        if (!isUpdating) return
        isUpdating = false
        fusedClient.removeLocationUpdates(callback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLatestLocation(): LocationSnapshot? {
        if (!hasLocationPermission()) return null

        latestSnapshot?.let { return it }
        val location = fusedClient.lastLocation.awaitOrNull() ?: return null
        return location.toSnapshot().also { latestSnapshot = it }
    }

    @SuppressLint("MissingPermission")
    private fun startUpdatesInternal() {
        isUpdating = true
        fusedClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper(),
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun Location.toSnapshot(): LocationSnapshot {
        return LocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            timestampMillis = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_UPDATE_INTERVAL_MS = 4_000L
    }
}
