package com.visionmate.pro.emergency

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationServicesManager(
    private val context: Context
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(Pair(location.latitude, location.longitude))
                        } else {
                            // Fallback default coordinates if GPS unprimed
                            continuation.resume(Pair(13.0827, 80.2707)) // Chennai default coordinates
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(Pair(13.0827, 80.2707))
                    }
            } catch (e: Exception) {
                continuation.resume(Pair(13.0827, 80.2707))
            }
        }
    }
}
