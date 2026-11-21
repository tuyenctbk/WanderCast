package com.example.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

private const val TAG = "BatteryPowerProfileMgr"

enum class BatteryPowerSavingOverride(val label: String) {
    AUTO_THRESHOLD("Auto (< 20% Battery)"),
    FORCE_BATTERY_SAVER("Always Eco Battery Saver"),
    FORCE_HIGH_PERFORMANCE("Always High-Frequency GPS")
}

data class BatteryPowerProfileState(
    val deviceBatteryPercentage: Int = 100,
    val simulatedBatteryPercentage: Int? = null,
    val isCharging: Boolean = false,
    val lowBatteryThresholdPercent: Int = 20,
    val overrideMode: BatteryPowerSavingOverride = BatteryPowerSavingOverride.AUTO_THRESHOLD,
    val isBatterySaverActive: Boolean = false,
    val gpsPriority: Int = Priority.PRIORITY_HIGH_ACCURACY,
    val gpsPriorityLabel: String = "HIGH_ACCURACY",
    val gpsPollingIntervalMs: Long = 4_000L,
    val gpsFastestIntervalMs: Long = 2_000L,
    val gpsMinDisplacementMeters: Float = 8f,
    val mapTileRefreshThrottleMs: Long = 0L,
    val mapTileKeepBuffer: Int = 2,
    val mapUpdateWhenMoving: Boolean = true
) {
    val effectiveBatteryPercentage: Int
        get() = simulatedBatteryPercentage ?: deviceBatteryPercentage

    val isBelowLowBatteryThreshold: Boolean
        get() = effectiveBatteryPercentage < lowBatteryThresholdPercent
}

/**
 * Monitors real-time Android device battery level and charging status via [Intent.ACTION_BATTERY_CHANGED]
 * and [BatteryManager], and computes adaptive GPS polling intervals and map tile refresh throttling:
 *
 * - **Normal Walk Profile (>= 20% Battery)**:
 *   - GPS Priority: [Priority.PRIORITY_HIGH_ACCURACY]
 *   - GPS Polling Interval: `4,000 ms` (Fastest: `2,000 ms`, Min displacement: `8 m`)
 *   - Map Tile Refresh: Immediate (`0 ms` throttle, `updateWhenIdle = false`, `keepBuffer = 2`)
 *
 * - **Battery-Saving Walk Profile (< 20% Battery or Manual Eco Override)**:
 *   - GPS Priority: [Priority.PRIORITY_BALANCED_POWER_ACCURACY]
 *   - GPS Polling Interval: `18,000 ms` (Fastest: `10,000 ms`, Min displacement: `28 m`) — ~78% reduction in GPS radio wakeups
 *   - Map Tile Refresh: Throttled (`12,000 ms` minimum interval between WebView map redraws, Leaflet `updateWhenIdle = true`, `updateInterval = 900 ms`, `keepBuffer = 1`, `preferCanvas = true`)
 */
class BatteryPowerProfileManager(
    context: Context,
    private val onBatterySaverProfileChanged: (BatteryPowerProfileState) -> Unit = {}
) {
    companion object {
        const val LOW_BATTERY_THRESHOLD_PERCENT = 20

        // Standard High-Frequency GPS & Map Tile parameters (>= 20% battery)
        const val NORMAL_GPS_INTERVAL_MS = 4_000L
        const val NORMAL_GPS_FASTEST_INTERVAL_MS = 2_000L
        const val NORMAL_GPS_MIN_DISPLACEMENT_M = 8f
        const val NORMAL_MAP_REFRESH_THROTTLE_MS = 0L
        const val NORMAL_MAP_KEEP_BUFFER = 2

        // Battery-Saving GPS & Map Tile parameters (< 20% battery)
        const val SAVER_GPS_INTERVAL_MS = 18_000L
        const val SAVER_GPS_FASTEST_INTERVAL_MS = 10_000L
        const val SAVER_GPS_MIN_DISPLACEMENT_M = 28f
        const val SAVER_MAP_REFRESH_THROTTLE_MS = 12_000L
        const val SAVER_MAP_KEEP_BUFFER = 1
    }

    private val appContext = context.applicationContext

    private var deviceBatteryPercent: Int = readInitialBatteryPercentage()
    private var deviceIsCharging: Boolean = false
    private var simulatedBatteryPercent: Int? = null
    private var overrideMode: BatteryPowerSavingOverride = BatteryPowerSavingOverride.AUTO_THRESHOLD
    private var isReceiverRegistered: Boolean = false

    private val _powerProfileState = MutableStateFlow(computeProfileState())
    val powerProfileState: StateFlow<BatteryPowerProfileState> = _powerProfileState.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            if (level >= 0 && scale > 0) {
                val pct = ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(1, 100)
                deviceBatteryPercent = pct
                deviceIsCharging = charging
                publishUpdatedProfile()
            }
        }
    }

    init {
        registerBatteryReceiver()
    }

    private fun readInitialBatteryPercentage(): Int {
        return try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val prop = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (prop in 1..100) prop else 100
        } catch (_: Exception) {
            100
        }
    }

    private fun registerBatteryReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = appContext.registerReceiver(batteryReceiver, filter)
            isReceiverRegistered = true
            if (stickyIntent != null) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                deviceIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                if (level >= 0 && scale > 0) {
                    deviceBatteryPercent = ((level.toFloat() / scale.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(1, 100)
                }
            }
            _powerProfileState.value = computeProfileState()
            LocationServiceBus.updateBatteryPowerProfile(_powerProfileState.value)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register battery receiver: ${e.message}")
        }
    }

    fun setOverrideMode(mode: BatteryPowerSavingOverride) {
        overrideMode = mode
        publishUpdatedProfile()
    }

    /**
     * Allows testing the `< 20%` low-battery auto-trigger on an emulator or plugged-in development
     * device (e.g., simulating 14% battery vs restoring live hardware battery reading).
     */
    fun setSimulatedBatteryPercentage(simulatedPercent: Int?) {
        simulatedBatteryPercent = simulatedPercent?.coerceIn(1, 100)
        publishUpdatedProfile()
    }

    private fun publishUpdatedProfile() {
        val previous = _powerProfileState.value
        val updated = computeProfileState()
        _powerProfileState.value = updated
        LocationServiceBus.updateBatteryPowerProfile(updated)

        if (previous.isBatterySaverActive != updated.isBatterySaverActive ||
            previous.gpsPollingIntervalMs != updated.gpsPollingIntervalMs
        ) {
            onBatterySaverProfileChanged(updated)
        }
    }

    private fun computeProfileState(): BatteryPowerProfileState {
        val effectivePct = simulatedBatteryPercent ?: deviceBatteryPercent
        val isBelowThreshold = effectivePct < LOW_BATTERY_THRESHOLD_PERCENT

        val saverActive = when (overrideMode) {
            BatteryPowerSavingOverride.AUTO_THRESHOLD -> isBelowThreshold
            BatteryPowerSavingOverride.FORCE_BATTERY_SAVER -> true
            BatteryPowerSavingOverride.FORCE_HIGH_PERFORMANCE -> false
        }

        return if (saverActive) {
            BatteryPowerProfileState(
                deviceBatteryPercentage = deviceBatteryPercent,
                simulatedBatteryPercentage = simulatedBatteryPercent,
                isCharging = deviceIsCharging,
                lowBatteryThresholdPercent = LOW_BATTERY_THRESHOLD_PERCENT,
                overrideMode = overrideMode,
                isBatterySaverActive = true,
                gpsPriority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                gpsPriorityLabel = "BALANCED_POWER (18s / 28m)",
                gpsPollingIntervalMs = SAVER_GPS_INTERVAL_MS,
                gpsFastestIntervalMs = SAVER_GPS_FASTEST_INTERVAL_MS,
                gpsMinDisplacementMeters = SAVER_GPS_MIN_DISPLACEMENT_M,
                mapTileRefreshThrottleMs = SAVER_MAP_REFRESH_THROTTLE_MS,
                mapTileKeepBuffer = SAVER_MAP_KEEP_BUFFER,
                mapUpdateWhenMoving = false
            )
        } else {
            BatteryPowerProfileState(
                deviceBatteryPercentage = deviceBatteryPercent,
                simulatedBatteryPercentage = simulatedBatteryPercent,
                isCharging = deviceIsCharging,
                lowBatteryThresholdPercent = LOW_BATTERY_THRESHOLD_PERCENT,
                overrideMode = overrideMode,
                isBatterySaverActive = false,
                gpsPriority = Priority.PRIORITY_HIGH_ACCURACY,
                gpsPriorityLabel = "HIGH_ACCURACY (4s / 8m)",
                gpsPollingIntervalMs = NORMAL_GPS_INTERVAL_MS,
                gpsFastestIntervalMs = NORMAL_GPS_FASTEST_INTERVAL_MS,
                gpsMinDisplacementMeters = NORMAL_GPS_MIN_DISPLACEMENT_M,
                mapTileRefreshThrottleMs = NORMAL_MAP_REFRESH_THROTTLE_MS,
                mapTileKeepBuffer = NORMAL_MAP_KEEP_BUFFER,
                mapUpdateWhenMoving = true
            )
        }
    }

    fun unregister() {
        if (!isReceiverRegistered) return
        try {
            appContext.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        isReceiverRegistered = false
    }
}
