package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EpisodeEntity
import com.example.location.GeofenceNode
import com.example.location.LocationBlockState
import com.example.location.LocationGeofenceManager
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.RadarTeal
import com.example.ui.theme.SignalCoral
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GeofenceRadarScreen(
    locationState: LocationBlockState,
    savedEpisodes: List<EpisodeEntity>,
    autoTriggerEnabled: Boolean,
    hasLocationPermission: Boolean,
    onToggleAutoTrigger: (Boolean) -> Unit,
    onChangeGeofenceRadius: (Int) -> Unit,
    onStepDirection: (Double, Double) -> Unit,
    onTuneCustomNeighborhood: (String, Double, Double) -> Unit,
    onStartLiveGps: () -> Unit,
    onReplayNearbyEpisode: (EpisodeEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    var showCustomLocationDialog by rememberSaveable { mutableStateOf(false) }

    // Derive real nearby geofence nodes solely from user-recorded Room episodes near the active GPS fix
    val nearbyEpisodeNodes = remember(locationState, savedEpisodes) {
        if (!locationState.hasLocationFix || savedEpisodes.isEmpty()) {
            emptyList()
        } else {
            savedEpisodes.mapNotNull { ep ->
                val dist = LocationGeofenceManager.haversineMeters(
                    locationState.latitude,
                    locationState.longitude,
                    ep.latitude,
                    ep.longitude
                ).roundToInt()
                if (dist <= 2000) {
                    val bearing = LocationGeofenceManager.bearingDegrees(
                        locationState.latitude,
                        locationState.longitude,
                        ep.latitude,
                        ep.longitude
                    )
                    Pair(
                        ep,
                        GeofenceNode(
                            id = "ep_${ep.id}",
                            title = ep.title,
                            zoneType = ep.category,
                            latitude = ep.latitude,
                            longitude = ep.longitude,
                            radiusMeters = locationState.triggerThresholdMeters,
                            distanceMeters = dist,
                            bearingDegrees = bearing,
                            isInsideZone = dist <= locationState.triggerThresholdMeters
                        )
                    )
                } else null
            }.sortedBy { it.second.distanceMeters }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("geofence_radar_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Radar Screen Title & Telemetry Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = stringResource(R.string.radar_header_title),
                                    tint = RadarTeal
                                )
                                Text(
                                    text = stringResource(R.string.radar_header_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = RadarTeal
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${locationState.triggerThresholdMeters}M",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = locationState.streetBlockLabel.ifBlank {
                                stringResource(R.string.waiting_for_gps_block)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = locationState.neighborhoodName.ifBlank {
                                stringResource(R.string.waiting_for_gps_neighborhood)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Topographic Radar Canvas
                        GeofenceRadarCanvas(
                            nodes = nearbyEpisodeNodes.map { it.second },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.15f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Walk Telemetry Counters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryStatPill(
                                label = stringResource(R.string.radar_blocks_crossed),
                                value = "${locationState.blocksExploredCount}",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TelemetryStatPill(
                                label = stringResource(R.string.radar_distance_walked),
                                value = "${locationState.totalWalkedMeters}m",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TelemetryStatPill(
                                label = stringResource(R.string.radar_block_progress),
                                value = "${locationState.distanceFromLastBroadcastMeters}/${locationState.triggerThresholdMeters}m",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val progress = (locationState.distanceFromLastBroadcastMeters.toFloat() /
                            locationState.triggerThresholdMeters.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = RadarTeal,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // 2. Compass Field Walk Stepper & Custom Location Tuner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.radar_stepper_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.radar_stepper_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (hasLocationPermission) {
                                OutlinedButton(
                                    onClick = onStartLiveGps,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("reconnect_live_gps_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.radar_live_gps_btn), maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4-Direction Block Stepper (~95m per tap)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onStepDirection(0.00086, 0.0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("step_north_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.North, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.dir_north),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = { onStepDirection(0.0, 0.00105) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("step_east_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.East, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.dir_east),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = { onStepDirection(-0.00086, 0.0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("step_south_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.South, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.dir_south),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = { onStepDirection(0.0, -0.00105) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("step_west_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.West, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.dir_west),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showCustomLocationDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("tune_custom_neighborhood_button"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.radar_tune_worldwide_btn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Geofence Perimeter Sensitivity & Auto-Trigger Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.radar_auto_trigger_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        R.string.radar_auto_trigger_desc,
                                        locationState.triggerThresholdMeters
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoTriggerEnabled,
                                onCheckedChange = onToggleAutoTrigger,
                                modifier = Modifier.testTag("auto_trigger_geofence_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.radar_perimeter_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.radar_meters_value, locationState.triggerThresholdMeters),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Slider(
                            value = locationState.triggerThresholdMeters.toFloat(),
                            onValueChange = { onChangeGeofenceRadius(it.roundToInt()) },
                            valueRange = 50f..160f,
                            modifier = Modifier.testTag("geofence_radius_slider")
                        )
                    }
                }
            }

            // 4. Nearby Recorded Field Zones Around Current Block
            item {
                Text(
                    text = stringResource(R.string.radar_nearby_zones_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (nearbyEpisodeNodes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = RadarTeal,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = stringResource(R.string.radar_nearby_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.radar_nearby_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(nearbyEpisodeNodes, key = { it.second.id }) { (episode, node) ->
                    GeofenceNodeItemCard(
                        node = node,
                        onBroadcastZone = { onReplayNearbyEpisode(episode) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCustomLocationDialog) {
        CustomNeighborhoodTunerDialog(
            hasLocationFix = locationState.hasLocationFix,
            currentLat = locationState.latitude,
            currentLng = locationState.longitude,
            onDismiss = { showCustomLocationDialog = false },
            onConfirm = { name, lat, lng ->
                showCustomLocationDialog = false
                onTuneCustomNeighborhood(name, lat, lng)
            }
        )
    }
}

@Composable
private fun GeofenceRadarCanvas(
    nodes: List<GeofenceNode>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0A1419)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = min(centerX, centerY) * 0.85f

            // Subtle grid crosshairs
            drawLine(
                color = RadarTeal.copy(alpha = 0.18f),
                start = Offset(centerX, centerY - maxRadius),
                end = Offset(centerX, centerY + maxRadius),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = RadarTeal.copy(alpha = 0.18f),
                start = Offset(centerX - maxRadius, centerY),
                end = Offset(centerX + maxRadius, centerY),
                strokeWidth = 1.dp.toPx()
            )

            // Concentric Geofence Rings (50m, 100m, 150m)
            val dashedStroke = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
            listOf(0.35f, 0.68f, 1.0f).forEachIndexed { idx, ratio ->
                drawCircle(
                    color = if (idx == 1) BrassAmber.copy(alpha = 0.45f) else RadarTeal.copy(alpha = 0.28f),
                    radius = maxRadius * ratio,
                    center = Offset(centerX, centerY),
                    style = if (idx == 1) Stroke(width = 1.8.dp.toPx()) else dashedStroke
                )
            }

            // Rotating Radar Sweep Beam + Trailing Phosphor Arc
            val rad = Math.toRadians(sweepAngle.toDouble())
            val beamEndX = centerX + (maxRadius * cos(rad)).toFloat()
            val beamEndY = centerY + (maxRadius * sin(rad)).toFloat()

            drawArc(
                color = RadarTeal.copy(alpha = 0.14f),
                startAngle = sweepAngle - 42f,
                sweepAngle = 42f,
                useCenter = true,
                topLeft = Offset(centerX - maxRadius, centerY - maxRadius),
                size = Size(maxRadius * 2f, maxRadius * 2f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RadarTeal.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = Offset(centerX, centerY)
            )

            drawLine(
                color = RadarTeal.copy(alpha = 0.85f),
                start = Offset(centerX, centerY),
                end = Offset(beamEndX, beamEndY),
                strokeWidth = 2.dp.toPx()
            )

            // Plot Nearby Recorded Geofence Nodes
            nodes.forEachIndexed { idx, node ->
                val angleRad = Math.toRadians((node.bearingDegrees - 90f).toDouble())
                val normalizedDist = (node.distanceMeters.toFloat() / 250f).coerceIn(0.22f, 0.92f)
                val nodeX = centerX + (maxRadius * normalizedDist * cos(angleRad)).toFloat()
                val nodeY = centerY + (maxRadius * normalizedDist * sin(angleRad)).toFloat()

                val nodeColor = when (idx % 3) {
                    0 -> BrassAmber
                    1 -> RadarTeal
                    else -> SignalCoral
                }

                // Node geofence halo
                drawCircle(
                    color = nodeColor.copy(alpha = 0.22f),
                    radius = 16.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
                drawCircle(
                    color = nodeColor,
                    radius = 5.5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }

            // Walker Current GPS Beacon at Center
            drawCircle(
                color = BrassAmber.copy(alpha = 0.3f),
                radius = 14.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = BrassAmber,
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }

        // Compass Labels Overlay
        Text(
            text = stringResource(R.string.compass_n),
            style = MaterialTheme.typography.labelSmall,
            color = BrassAmber,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
        )
        Text(
            text = stringResource(R.string.compass_s),
            style = MaterialTheme.typography.labelSmall,
            color = RadarTeal.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
        Text(
            text = stringResource(R.string.compass_w),
            style = MaterialTheme.typography.labelSmall,
            color = RadarTeal.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        )
        Text(
            text = stringResource(R.string.compass_e),
            style = MaterialTheme.typography.labelSmall,
            color = RadarTeal.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        )
    }
}

@Composable
private fun TelemetryStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GeofenceNodeItemCard(
    node: GeofenceNode,
    onBroadcastZone: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("geofence_node_card_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (node.isInsideZone) RadarTeal else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (node.isInsideZone) RadarTeal.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (node.isInsideZone) RadarTeal else MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = node.zoneType.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (node.isInsideZone) RadarTeal else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.radar_distance_away_format, node.distanceMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onBroadcastZone,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("broadcast_node_btn_${node.id}")
            ) {
                Text(
                    text = stringResource(R.string.narrate_btn),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CustomNeighborhoodTunerDialog(
    hasLocationFix: Boolean,
    currentLat: Double,
    currentLng: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit
) {
    var blockName by rememberSaveable { mutableStateOf("") }
    var latInput by rememberSaveable {
        mutableStateOf(if (hasLocationFix) String.format(Locale.US, "%.4f", currentLat) else "")
    }
    var lngInput by rememberSaveable {
        mutableStateOf(if (hasLocationFix) String.format(Locale.US, "%.4f", currentLng) else "")
    }
    val defaultBlockName = stringResource(R.string.radar_custom_default_name)

    val parsedLat = latInput.trim().toDoubleOrNull()
    val parsedLng = lngInput.trim().toDoubleOrNull()
    val isValidCoordinates = parsedLat != null && parsedLng != null &&
        parsedLat in -90.0..90.0 && parsedLng in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.radar_custom_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.radar_custom_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = blockName,
                    onValueChange = { blockName = it },
                    label = { Text(stringResource(R.string.radar_custom_block_label)) },
                    placeholder = { Text(stringResource(R.string.radar_custom_block_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_block_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text(stringResource(R.string.radar_custom_lat_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngInput,
                        onValueChange = { lngInput = it },
                        label = { Text(stringResource(R.string.radar_custom_lng_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = blockName.trim().ifEmpty { defaultBlockName }
                    if (parsedLat != null && parsedLng != null) {
                        onConfirm(finalName, parsedLat, parsedLng)
                    }
                },
                enabled = isValidCoordinates,
                modifier = Modifier.testTag("confirm_custom_location_button")
            ) {
                Text(stringResource(R.string.radar_custom_confirm_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
