package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "LocationGeofenceMgr"

data class GeofenceNode(
    val id: String,
    val title: String,
    val zoneType: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val distanceMeters: Int,
    val bearingDegrees: Float,
    val isInsideZone: Boolean
)

data class LocationBlockState(
    val hasLocationFix: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val streetBlockLabel: String = "",
    val neighborhoodName: String = "",
    val cityRegion: String = "",
    val accuracyMeters: Float = 0f,
    val speedMetersPerSec: Float = 0f,
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "",
    val predictedNextIntersectionLat: Double = 0.0,
    val predictedNextIntersectionLng: Double = 0.0,
    val predictedNextBlockHint: String = "",
    val isLiveGpsActive: Boolean = false,
    val isForegroundServiceActive: Boolean = false,
    val isFieldStepperMode: Boolean = false,
    val distanceFromLastBroadcastMeters: Int = 0,
    val triggerThresholdMeters: Int = 85,
    val blocksExploredCount: Int = 0,
    val totalWalkedMeters: Int = 0,
    val activeGeofenceNodes: List<GeofenceNode> = emptyList()
)

class LocationGeofenceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onGeofenceBlockEntered: (LocationBlockState) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val batteryPowerProfileManager = BatteryPowerProfileManager(
        context = context,
        onBatterySaverProfileChanged = { newProfile ->
            reconfigureActiveGpsPollingForPowerProfile(newProfile)
        }
    )

    val batteryPowerProfileState: StateFlow<BatteryPowerProfileState> =
        batteryPowerProfileManager.powerProfileState

    private val _locationState = MutableStateFlow(LocationBlockState())
    val locationState: StateFlow<LocationBlockState> = _locationState.asStateFlow()

    private var lastBroadcastLat: Double = 0.0
    private var lastBroadcastLng: Double = 0.0
    private var previousLat: Double = 0.0
    private var previousLng: Double = 0.0
    private var hasReceivedInitialFix = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // If the Foreground LocationService is active, it already emits fixes through LocationServiceBus
            if (LocationServiceBus.isForegroundServiceRunning.value) return
            val location: Location = result.lastLocation ?: return
            handleNewCoordinates(
                lat = location.latitude,
                lng = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else 10f,
                speed = if (location.hasSpeed()) location.speed else 1.3f,
                fromLiveGps = true
            )
        }
    }

    init {
        // Observe Foreground LocationService running status
        scope.launch {
            LocationServiceBus.isForegroundServiceRunning.collect { running ->
                _locationState.value = _locationState.value.copy(
                    isForegroundServiceActive = running,
                    isLiveGpsActive = running || _locationState.value.isLiveGpsActive
                )
            }
        }

        // Observe background location fixes emitted by Foreground LocationService
        scope.launch {
            LocationServiceBus.latestLocationFix.collect { fix ->
                handleNewCoordinates(
                    lat = fix.latitude,
                    lng = fix.longitude,
                    accuracy = fix.accuracyMeters,
                    speed = fix.speedMetersPerSec,
                    fromLiveGps = true
                )
            }
        }

        // Observe manual broadcast action button pressed in the Foreground Notification
        scope.launch {
            LocationServiceBus.notificationBroadcastTrigger.collect {
                markCurrentBlockBroadcasted()
                onGeofenceBlockEntered(_locationState.value)
            }
        }
    }

    fun startForegroundServiceTracking() {
        try {
            LocationService.start(context)
            _locationState.value = _locationState.value.copy(
                isForegroundServiceActive = true,
                isLiveGpsActive = true,
                isFieldStepperMode = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Foreground LocationService", e)
        }
    }

    fun stopForegroundServiceTracking() {
        try {
            LocationService.stop(context)
            _locationState.value = _locationState.value.copy(
                isForegroundServiceActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Foreground LocationService", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveGpsUpdates() {
        try {
            val profile = batteryPowerProfileState.value
            val request = LocationRequest.Builder(profile.gpsPriority, profile.gpsPollingIntervalMs)
                .setMinUpdateIntervalMillis(profile.gpsFastestIntervalMs)
                .setMinUpdateDistanceMeters(profile.gpsMinDisplacementMeters)
                .build()

            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    handleNewCoordinates(
                        lat = lastLoc.latitude,
                        lng = lastLoc.longitude,
                        accuracy = if (lastLoc.hasAccuracy()) lastLoc.accuracy else 10f,
                        speed = if (lastLoc.hasSpeed()) lastLoc.speed else 1.2f,
                        fromLiveGps = true
                    )
                }
            }

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            _locationState.value = _locationState.value.copy(
                isLiveGpsActive = true,
                isFieldStepperMode = false
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing when starting GPS updates", e)
            _locationState.value = _locationState.value.copy(isLiveGpsActive = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconfigureActiveGpsPollingForPowerProfile(profile: BatteryPowerProfileState) {
        if (_locationState.value.isLiveGpsActive && !LocationServiceBus.isForegroundServiceRunning.value) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                val request = LocationRequest.Builder(profile.gpsPriority, profile.gpsPollingIntervalMs)
                    .setMinUpdateIntervalMillis(profile.gpsFastestIntervalMs)
                    .setMinUpdateDistanceMeters(profile.gpsMinDisplacementMeters)
                    .build()
                fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to reconfigure live GPS polling for battery saver", e)
            }
        }
        if (LocationServiceBus.isForegroundServiceRunning.value) {
            LocationService.notifyPowerProfileChanged(context)
        }
    }

    fun setBatteryPowerOverrideMode(mode: BatteryPowerSavingOverride) {
        batteryPowerProfileManager.setOverrideMode(mode)
    }

    fun setSimulatedBatteryLevel(simulatedPercentage: Int?) {
        batteryPowerProfileManager.setSimulatedBatteryPercentage(simulatedPercentage)
    }

    fun stopLiveGpsUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates", e)
        }
        _locationState.value = _locationState.value.copy(isLiveGpsActive = false)
    }

    fun cleanup() {
        stopLiveGpsUpdates()
        batteryPowerProfileManager.unregister()
    }

    fun setTriggerThresholdMeters(meters: Int) {
        val clamped = meters.coerceIn(45, 200)
        _locationState.value = _locationState.value.copy(triggerThresholdMeters = clamped)
    }

    fun markCurrentBlockBroadcasted() {
        val current = _locationState.value
        lastBroadcastLat = current.latitude
        lastBroadcastLng = current.longitude
        _locationState.value = current.copy(distanceFromLastBroadcastMeters = 0)
    }

    /**
     * Advances the walker ~95 meters in the specified compass direction so the user can test
     * crossing into a new block / geofence even when indoors or using an emulator.
     * Requires an active location fix or tuned coordinates first.
     */
    fun stepOneBlock(deltaLat: Double, deltaLng: Double): Boolean {
        val current = _locationState.value
        if (!current.hasLocationFix) return false
        val newLat = (current.latitude + deltaLat).coerceIn(-89.9, 89.9)
        val newLng = ((current.longitude + deltaLng + 540.0) % 360.0) - 180.0
        handleNewCoordinates(
            lat = newLat,
            lng = newLng,
            accuracy = 6f,
            speed = 1.4f,
            fromLiveGps = false
        )
        return true
    }

    fun jumpToCustomLocation(customQueryLabel: String, lat: Double, lng: Double, triggerEpisode: Boolean = true) {
        val safeLat = lat.coerceIn(-90.0, 90.0)
        val safeLng = lng.coerceIn(-180.0, 180.0)
        lastBroadcastLat = safeLat
        lastBroadcastLng = safeLng
        previousLat = safeLat
        previousLng = safeLng
        hasReceivedInitialFix = true

        val (predLat, predLng) = projectPointAhead(safeLat, safeLng, 0f, 95.0)
        val updated = _locationState.value.copy(
            hasLocationFix = true,
            latitude = safeLat,
            longitude = safeLng,
            streetBlockLabel = customQueryLabel,
            neighborhoodName = customQueryLabel,
            cityRegion = customQueryLabel,
            headingDegrees = 0f,
            headingCardinal = "N",
            predictedNextIntersectionLat = predLat,
            predictedNextIntersectionLng = predLng,
            predictedNextBlockHint = "$customQueryLabel • +95m N",
            isFieldStepperMode = true,
            distanceFromLastBroadcastMeters = 0,
            blocksExploredCount = _locationState.value.blocksExploredCount + 1
        )
        _locationState.value = updated
        if (triggerEpisode) {
            onGeofenceBlockEntered(updated)
        }
    }

    private fun handleNewCoordinates(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float,
        fromLiveGps: Boolean
    ) {
        scope.launch(Dispatchers.IO) {
            val current = _locationState.value
            val isFirstFix = !hasReceivedInitialFix
            if (isFirstFix) {
                hasReceivedInitialFix = true
                lastBroadcastLat = lat
                lastBroadcastLng = lng
                previousLat = lat
                previousLng = lng
            }

            val stepDist = if (isFirstFix) 0 else haversineMeters(previousLat, previousLng, lat, lng).roundToInt()
            val computedBearing = if (stepDist >= 3) {
                bearingDegrees(previousLat, previousLng, lat, lng)
            } else {
                current.headingDegrees
            }
            val cardinal = cardinalFromBearing(computedBearing)
            val (predLat, predLng) = projectPointAhead(lat, lng, computedBearing, 95.0)
            previousLat = lat
            previousLng = lng

            val distFromLastBroadcast = haversineMeters(lastBroadcastLat, lastBroadcastLng, lat, lng).roundToInt()
            val geocoded = reverseGeocodeBlock(lat, lng)
            val blockTitle = geocoded.first ?: formatCoordFallbackBlock(lat, lng)
            val neighborhood = geocoded.second ?: current.neighborhoodName.ifBlank { blockTitle }
            val city = geocoded.third ?: current.cityRegion.ifBlank { neighborhood }

            val crossedBlockThreshold = !isFirstFix && distFromLastBroadcast >= current.triggerThresholdMeters
            val newBlocksCount = when {
                isFirstFix -> current.blocksExploredCount.coerceAtLeast(1)
                crossedBlockThreshold -> current.blocksExploredCount + 1
                else -> current.blocksExploredCount
            }

            val predictedHint = "$blockTitle • +95m $cardinal"
            val newState = current.copy(
                hasLocationFix = true,
                latitude = lat,
                longitude = lng,
                streetBlockLabel = blockTitle,
                neighborhoodName = neighborhood,
                cityRegion = city,
                accuracyMeters = accuracy,
                speedMetersPerSec = speed,
                headingDegrees = computedBearing,
                headingCardinal = cardinal,
                predictedNextIntersectionLat = predLat,
                predictedNextIntersectionLng = predLng,
                predictedNextBlockHint = predictedHint,
                isLiveGpsActive = fromLiveGps || current.isLiveGpsActive,
                isFieldStepperMode = !fromLiveGps,
                distanceFromLastBroadcastMeters = if (crossedBlockThreshold) 0 else distFromLastBroadcast,
                blocksExploredCount = newBlocksCount,
                totalWalkedMeters = current.totalWalkedMeters + stepDist.coerceAtMost(350)
            )

            if (crossedBlockThreshold) {
                lastBroadcastLat = lat
                lastBroadcastLng = lng
            }

            _locationState.value = newState

            if (newState.isForegroundServiceActive) {
                val remainingMeters = (newState.triggerThresholdMeters - newState.distanceFromLastBroadcastMeters).coerceAtLeast(0)
                val statusMsg = if (crossedBlockThreshold) {
                    context.getString(R.string.notif_entered_block_format, neighborhood)
                } else {
                    context.getString(R.string.notif_next_trigger_format, remainingMeters, neighborhood)
                }
                LocationService.updateNotificationStatus(
                    context = context,
                    blockTitle = blockTitle,
                    statusSubtitle = statusMsg
                )
            }

            if (crossedBlockThreshold) {
                onGeofenceBlockEntered(newState)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeBlock(lat: Double, lng: Double): Triple<String?, String?, String?> {
        if (!Geocoder.isPresent()) return Triple(null, null, null)
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(3_000L) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            lat,
                            lng,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) {
                                        cont.resume(addresses.firstOrNull())
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    Log.w(TAG, "Geocoder onError for ($lat, $lng): $errorMessage")
                                    if (cont.isActive) {
                                        cont.resume(null)
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                withTimeoutOrNull(3_000L) {
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
            }

            if (address != null) {
                val thoroughfare = address.thoroughfare
                val subThoroughfare = address.subThoroughfare
                val subLocality = address.subLocality
                val locality = address.locality ?: address.subAdminArea
                val adminArea = address.adminArea

                val blockLabel = when {
                    !thoroughfare.isNullOrBlank() && !subThoroughfare.isNullOrBlank() ->
                        "$subThoroughfare $thoroughfare Block"
                    !thoroughfare.isNullOrBlank() ->
                        "$thoroughfare Corridor"
                    !address.featureName.isNullOrBlank() ->
                        "${address.featureName} Block"
                    else -> null
                }

                val neighborhoodLabel = listOfNotNull(
                    subLocality?.takeIf { it.isNotBlank() },
                    locality?.takeIf { it.isNotBlank() },
                    adminArea?.takeIf { it.isNotBlank() }
                ).distinct().joinToString(", ").ifBlank { null }

                val cityLabel = listOfNotNull(
                    locality?.takeIf { it.isNotBlank() },
                    adminArea?.takeIf { it.isNotBlank() }
                ).joinToString(", ").ifBlank { null }

                Triple(blockLabel, neighborhoodLabel, cityLabel)
            } else {
                Triple(null, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding unavailable for ($lat, $lng): ${e.message}")
            Triple(null, null, null)
        }
    }

    private fun formatCoordFallbackBlock(lat: Double, lng: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lng >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f°%s, %.4f°%s", kotlin.math.abs(lat), ns, kotlin.math.abs(lng), ew)
    }

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
            val brng = Math.toDegrees(atan2(y, x))
            return ((brng + 360.0) % 360.0).toFloat()
        }

        fun cardinalFromBearing(bearing: Float): String {
            val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = (((bearing + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, 15)
            return dirs[index]
        }

        fun projectPointAhead(
            lat: Double,
            lng: Double,
            bearingDeg: Float,
            distanceMeters: Double
        ): Pair<Double, Double> {
            val radBearing = Math.toRadians(bearingDeg.toDouble())
            val deltaLat = (distanceMeters * cos(radBearing)) / 111320.0
            val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.1)
            val deltaLng = (distanceMeters * sin(radBearing)) / (111320.0 * cosLat)
            return Pair(lat + deltaLat, lng + deltaLng)
        }
    }
}
