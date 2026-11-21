package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.location.BatteryPowerProfileState
import com.example.location.BatteryPowerSavingOverride
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.RadarTeal
import com.example.ui.theme.SignalCoral

@Composable
fun BatteryPowerSaverCard(
    batteryProfile: BatteryPowerProfileState,
    onSelectOverrideMode: (BatteryPowerSavingOverride) -> Unit,
    onSimulateLowBattery: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSaver = batteryProfile.isBatterySaverActive
    val effectivePct = batteryProfile.effectiveBatteryPercentage

    val accentColor by animateColorAsState(
        targetValue = when {
            isSaver -> BrassAmber
            effectivePct < 20 -> SignalCoral
            else -> RadarTeal
        },
        label = "batterySaverAccent"
    )

    val batteryIcon = when {
        batteryProfile.isCharging && batteryProfile.simulatedBatteryPercentage == null ->
            Icons.Default.BatteryChargingFull
        isSaver -> Icons.Default.BatterySaver
        effectivePct < 20 -> Icons.Default.BatteryAlert
        else -> Icons.Default.BatteryStd
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_power_saver_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.battery_saver_card_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (isSaver) {
                            "${effectivePct}% • ${stringResource(R.string.battery_saver_active_badge)}"
                        } else {
                            "${effectivePct}% • ${stringResource(R.string.battery_saver_normal_badge)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Battery Progress Bar
            LinearProgressIndicator(
                progress = { (effectivePct / 100f).coerceIn(0.02f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Active GPS Polling & Map Tile Refresh Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.battery_gps_polling_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${batteryProfile.gpsPollingIntervalMs / 1000}s • ${batteryProfile.gpsMinDisplacementMeters.toInt()}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.battery_map_refresh_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (batteryProfile.mapTileRefreshThrottleMs > 0L) {
                                    "${batteryProfile.mapTileRefreshThrottleMs / 1000}s Eco Idle"
                                } else {
                                    stringResource(R.string.battery_map_realtime_value)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = if (isSaver) {
                    stringResource(R.string.battery_saver_active_desc)
                } else {
                    stringResource(R.string.battery_saver_normal_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Mode Override Chips + 1-Tap Low Battery (<20%) Simulator for Testing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = batteryProfile.overrideMode == BatteryPowerSavingOverride.AUTO_THRESHOLD &&
                        batteryProfile.simulatedBatteryPercentage == null,
                    onClick = {
                        onSimulateLowBattery(null)
                        onSelectOverrideMode(BatteryPowerSavingOverride.AUTO_THRESHOLD)
                    },
                    label = { Text(stringResource(R.string.battery_mode_auto_chip)) },
                    modifier = Modifier.testTag("battery_mode_auto_chip")
                )

                val isSimulatingLow = batteryProfile.simulatedBatteryPercentage != null &&
                    batteryProfile.simulatedBatteryPercentage < 20
                if (isSimulatingLow) {
                    OutlinedButton(
                        onClick = { onSimulateLowBattery(null) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("battery_restore_live_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.battery_restore_live_btn),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            onSelectOverrideMode(BatteryPowerSavingOverride.AUTO_THRESHOLD)
                            onSimulateLowBattery(14)
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("battery_simulate_low_14_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.battery_simulate_14_btn),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
