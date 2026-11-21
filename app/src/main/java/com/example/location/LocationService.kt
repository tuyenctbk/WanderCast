package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "WanderCastLocService"

data class LocationFixEvent(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSec: Float,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Shared reactive bus connecting the Foreground [LocationService] with [LocationGeofenceManager]
 * and [com.example.viewmodel.WanderCastViewModel] so background GPS movement and notification
 * actions seamlessly trigger localized Gemini audio documentaries.
 */
object LocationServiceBus {
    private val _isForegroundServiceRunning = MutableStateFlow(false)
    val isForegroundServiceRunning: StateFlow<Boolean> = _isForegroundServiceRunning.asStateFlow()

    private val _latestLocationFix = MutableSharedFlow<LocationFixEvent>(extraBufferCapacity = 16)
    val latestLocationFix: SharedFlow<LocationFixEvent> = _latestLocationFix.asSharedFlow()

    private val _notificationBroadcastTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val notificationBroadcastTrigger: SharedFlow<Unit> = _notificationBroadcastTrigger.asSharedFlow()

    private val _batteryPowerProfile = MutableStateFlow(BatteryPowerProfileState())
    val batteryPowerProfile: StateFlow<BatteryPowerProfileState> = _batteryPowerProfile.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isForegroundServiceRunning.value = running
    }

    fun emitLocationFix(fix: LocationFixEvent) {
        _latestLocationFix.tryEmit(fix)
    }

    fun emitManualBroadcastRequest() {
        _notificationBroadcastTrigger.tryEmit(Unit)
    }

    fun updateBatteryPowerProfile(profile: BatteryPowerProfileState) {
        _batteryPowerProfile.value = profile
    }
}

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager

    private var currentBlockTitle: String = ""
    private var currentStatusSubtitle: String = ""

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return
            val fix = LocationFixEvent(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 10f,
                speedMetersPerSec = if (location.hasSpeed()) location.speed else 1.2f
            )
            LocationServiceBus.emitLocationFix(fix)

            val coordSummary = String.format(
                Locale.US,
                "%.4f, %.4f (±%dm)",
                fix.latitude,
                fix.longitude,
                fix.accuracyMeters.toInt()
            )
            updateForegroundNotification(
                title = currentBlockTitle.ifBlank { getString(R.string.notif_default_title) },
                subtitle = "${currentStatusSubtitle.ifBlank { getString(R.string.notif_default_subtitle) }} • $coordSummary"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        currentBlockTitle = getString(R.string.notif_default_title)
        currentStatusSubtitle = getString(R.string.notif_default_subtitle)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundTracking()
                return START_NOT_STICKY
            }

            ACTION_TRIGGER_NOW -> {
                LocationServiceBus.emitManualBroadcastRequest()
                return START_STICKY
            }

            ACTION_UPDATE_NOTIFICATION -> {
                val block = intent.getStringExtra(EXTRA_BLOCK_TITLE)
                val status = intent.getStringExtra(EXTRA_STATUS_SUBTITLE)
                if (!block.isNullOrBlank()) currentBlockTitle = block
                if (!status.isNullOrBlank()) currentStatusSubtitle = status
                if (LocationServiceBus.isForegroundServiceRunning.value) {
                    updateForegroundNotification(currentBlockTitle, currentStatusSubtitle)
                }
                return START_STICKY
            }

            ACTION_UPDATE_POWER_PROFILE -> {
                if (LocationServiceBus.isForegroundServiceRunning.value) {
                    reconfigureGpsPollingForCurrentPowerProfile()
                }
                return START_STICKY
            }

            else -> {
                startForegroundTracking()
                return START_STICKY
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundTracking() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start LocationService: location permission not granted.")
            LocationServiceBus.setServiceRunning(false)
            stopSelf()
            return
        }

        try {
            val notification = buildForegroundNotification(
                title = currentBlockTitle,
                subtitle = currentStatusSubtitle
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            LocationServiceBus.setServiceRunning(true)

            val profile = LocationServiceBus.batteryPowerProfile.value
            val locationRequest = LocationRequest.Builder(
                profile.gpsPriority,
                profile.gpsPollingIntervalMs
            )
                .setMinUpdateIntervalMillis(profile.gpsFastestIntervalMs)
                .setMinUpdateDistanceMeters(profile.gpsMinDisplacementMeters)
                .build()

            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    LocationServiceBus.emitLocationFix(
                        LocationFixEvent(
                            latitude = lastLoc.latitude,
                            longitude = lastLoc.longitude,
                            accuracyMeters = if (lastLoc.hasAccuracy()) lastLoc.accuracy else 10f,
                            speedMetersPerSec = if (lastLoc.hasSpeed()) lastLoc.speed else 1.2f
                        )
                    )
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(
                TAG,
                "Foreground LocationService started (interval=${profile.gpsPollingIntervalMs}ms, batterySaver=${profile.isBatterySaverActive})."
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground LocationService", e)
            LocationServiceBus.setServiceRunning(false)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground LocationService", e)
            LocationServiceBus.setServiceRunning(false)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconfigureGpsPollingForCurrentPowerProfile() {
        if (!hasLocationPermission()) return
        try {
            val profile = LocationServiceBus.batteryPowerProfile.value
            fusedLocationClient.removeLocationUpdates(locationCallback)
            val locationRequest = LocationRequest.Builder(
                profile.gpsPriority,
                profile.gpsPollingIntervalMs
            )
                .setMinUpdateIntervalMillis(profile.gpsFastestIntervalMs)
                .setMinUpdateDistanceMeters(profile.gpsMinDisplacementMeters)
                .build()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            val modeBadge = if (profile.isBatterySaverActive) {
                getString(R.string.notif_mode_eco_format, (profile.gpsPollingIntervalMs / 1000).toInt())
            } else {
                getString(R.string.notif_mode_std_format, (profile.gpsPollingIntervalMs / 1000).toInt())
            }
            updateForegroundNotification(
                title = currentBlockTitle.ifBlank { getString(R.string.notif_default_title) },
                subtitle = "${currentStatusSubtitle.ifBlank { getString(R.string.notif_default_subtitle) }} • $modeBadge"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not reconfigure foreground GPS polling interval", e)
        }
    }

    private fun stopForegroundTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates in LocationService", e)
        }
        LocationServiceBus.setServiceRunning(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Foreground LocationService stopped.")
    }

    override fun onDestroy() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates onDestroy", e)
        }
        LocationServiceBus.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateForegroundNotification(title: String, subtitle: String) {
        try {
            val notification = buildForegroundNotification(title, subtitle)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not update foreground notification", e)
        }
    }

    private fun buildForegroundNotification(title: String, subtitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val broadcastNowIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_TRIGGER_NOW
        }
        val broadcastNowPendingIntent = PendingIntent.getService(
            this,
            1,
            broadcastNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("99.1 FM • $title")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                getString(R.string.notif_action_broadcast),
                broadcastNowPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_action_stop),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "wandercast_location_fgs_channel"
        const val NOTIFICATION_ID = 9910

        const val ACTION_START_SERVICE = "com.example.location.action.START_FGS"
        const val ACTION_STOP_SERVICE = "com.example.location.action.STOP_FGS"
        const val ACTION_TRIGGER_NOW = "com.example.location.action.TRIGGER_NOW"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.location.action.UPDATE_NOTIFICATION"
        const val ACTION_UPDATE_POWER_PROFILE = "com.example.location.action.UPDATE_POWER_PROFILE"

        const val EXTRA_BLOCK_TITLE = "extra_block_title"
        const val EXTRA_STATUS_SUBTITLE = "extra_status_subtitle"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (!LocationServiceBus.isForegroundServiceRunning.value) {
                try {
                    context.stopService(Intent(context, LocationService::class.java))
                } catch (_: Exception) {
                }
                return
            }
            try {
                val intent = Intent(context, LocationService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, LocationService::class.java))
                LocationServiceBus.setServiceRunning(false)
            }
        }

        fun updateNotificationStatus(context: Context, blockTitle: String, statusSubtitle: String) {
            if (!LocationServiceBus.isForegroundServiceRunning.value) return
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_BLOCK_TITLE, blockTitle)
                putExtra(EXTRA_STATUS_SUBTITLE, statusSubtitle)
            }
            context.startService(intent)
        }

        fun notifyPowerProfileChanged(context: Context) {
            if (!LocationServiceBus.isForegroundServiceRunning.value) return
            try {
                val intent = Intent(context, LocationService::class.java).apply {
                    action = ACTION_UPDATE_POWER_PROFILE
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
