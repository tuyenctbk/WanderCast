package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EpisodeEntity
import com.example.data.RouteWaypoint
import com.example.data.WalkRouteEntity
import com.example.data.WalkRouteWithNarratives
import com.example.location.BatteryPowerProfileState
import com.example.location.BatteryPowerSavingOverride
import com.example.location.LocationBlockState
import com.example.location.LocationGeofenceManager
import com.example.ui.components.BatteryPowerSaverCard
import com.example.ui.components.KeylessInteractiveStreetMapView
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.RadarTeal
import com.example.util.TourExportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class TourMapEngineMode(val label: String) {
    KEYLESS_STREET_MAP("Live Street & Satellite Map"),
    VECTOR_RADAR("Vector Radar (100% Offline)")
}

private val TILE_LAYER_LABELS = listOf(
    "Dark Studio",
    "Daylight Street",
    "OSM Grid",
    "Satellite Imagery"
)

data class HistoricalPointGeofenceStatus(
    val episode: EpisodeEntity,
    val stopNumber: Int,
    val distanceFromCurrentGpsMeters: Int,
    val bearingCardinal: String,
    val isCurrentlyInsideGeofence: Boolean,
    val isVisited: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TourSummaryScreen(
    completedWalkRoutes: List<WalkRouteWithNarratives>,
    savedEpisodes: List<EpisodeEntity>,
    activeSessionWaypoints: List<RouteWaypoint>,
    currentLocationState: LocationBlockState,
    activeEpisodeId: Long?,
    visitedHistoricalPointIds: Set<Long> = emptySet(),
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onToggleEpisodeBookmark: (EpisodeEntity) -> Unit,
    onWalkToHistoricalPointGeofence: (EpisodeEntity, Boolean) -> Unit = { _, _ -> },
    onResetGeofenceProgress: () -> Unit = {},
    onLogCurrentWalkSession: () -> Unit,
    onNavigateBack: () -> Unit,
    unlockedBadgesCount: Int = 0,
    totalBadgesCount: Int = 8,
    onOpenRewards: () -> Unit = {},
    batteryPowerProfile: BatteryPowerProfileState = BatteryPowerProfileState(),
    onSelectBatteryOverrideMode: (BatteryPowerSavingOverride) -> Unit = {},
    onSimulateLowBattery: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current

    // Construct a live virtual WalkRouteWithNarratives for the active session so the user can
    // switch between completed Room routes and their current live walking session
    val liveSessionRouteWithNarratives = remember(
        activeSessionWaypoints,
        savedEpisodes,
        currentLocationState
    ) {
        val pts = activeSessionWaypoints.ifEmpty {
            listOf(
                RouteWaypoint(
                    currentLocationState.latitude - 0.0007,
                    currentLocationState.longitude - 0.0005,
                    "Start • ${currentLocationState.streetBlockLabel}"
                ),
                RouteWaypoint(
                    currentLocationState.latitude,
                    currentLocationState.longitude,
                    currentLocationState.streetBlockLabel
                )
            )
        }
        val first = pts.first()
        val last = pts.last()
        val sessionEpisodes = savedEpisodes.take(5)
        WalkRouteWithNarratives(
            route = WalkRouteEntity(
                routeId = -1L,
                routeTitle = "Active Session • ${currentLocationState.streetBlockLabel}",
                neighborhoodName = currentLocationState.neighborhoodName,
                cityRegion = currentLocationState.cityRegion,
                startBlockLabel = first.label,
                endBlockLabel = last.label,
                startLatitude = first.latitude,
                startLongitude = first.longitude,
                endLatitude = last.latitude,
                endLongitude = last.longitude,
                waypointsEncoded = WalkRouteEntity.encodeWaypoints(pts),
                totalDistanceMeters = currentLocationState.totalWalkedMeters.coerceAtLeast(320),
                durationSeconds = (currentLocationState.blocksExploredCount * 240).coerceAtLeast(360),
                blocksVisitedCount = currentLocationState.blocksExploredCount.coerceAtLeast(pts.size),
                narrativesGeneratedCount = sessionEpisodes.size,
                editorialLens = "Live GPS Walk",
                googleMapsUrl = "https://www.google.com/maps/dir/?api=1&travelmode=walking&origin=${first.latitude},${first.longitude}&destination=${last.latitude},${last.longitude}",
                startedAtTimestamp = System.currentTimeMillis() - 900_000L,
                completedAtTimestamp = System.currentTimeMillis()
            ),
            narratives = sessionEpisodes
        )
    }

    val allTourOptions = remember(completedWalkRoutes, liveSessionRouteWithNarratives) {
        if (completedWalkRoutes.isNotEmpty()) {
            completedWalkRoutes + liveSessionRouteWithNarratives
        } else {
            listOf(liveSessionRouteWithNarratives)
        }
    }

    var selectedTourIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = selectedTourIndex.coerceIn(0, (allTourOptions.size - 1).coerceAtLeast(0))
    val activeTour = allTourOptions[safeIndex]
    val activeRoute = activeTour.route

    // Triggered historical / architectural points retrieved from Room DB for this tour
    val triggeredPoints: List<EpisodeEntity> = remember(activeTour, savedEpisodes) {
        if (activeTour.narratives.isNotEmpty()) {
            activeTour.narratives.sortedBy { it.timestamp }
        } else {
            savedEpisodes.take(4).sortedBy { it.timestamp }
        }
    }

    // Evaluate GPS & Geofence status for each historical point based on the user's current GPS
    // coordinates, geofence trigger radius, and triggered geofence history
    val geofenceRadiusMeters = currentLocationState.triggerThresholdMeters.coerceAtLeast(45)
    val pointGeofenceStatuses: List<HistoricalPointGeofenceStatus> = remember(
        triggeredPoints,
        currentLocationState.latitude,
        currentLocationState.longitude,
        geofenceRadiusMeters,
        visitedHistoricalPointIds
    ) {
        triggeredPoints.mapIndexed { idx, ep ->
            val distMeters = LocationGeofenceManager.haversineMeters(
                currentLocationState.latitude,
                currentLocationState.longitude,
                ep.latitude,
                ep.longitude
            ).roundToInt()
            val bearingDeg = LocationGeofenceManager.bearingDegrees(
                currentLocationState.latitude,
                currentLocationState.longitude,
                ep.latitude,
                ep.longitude
            )
            val cardinal = LocationGeofenceManager.cardinalFromBearing(bearingDeg)
            val isInsideGeofenceNow = distMeters <= geofenceRadiusMeters
            val isVisited = isInsideGeofenceNow || visitedHistoricalPointIds.contains(ep.id)

            HistoricalPointGeofenceStatus(
                episode = ep,
                stopNumber = idx + 1,
                distanceFromCurrentGpsMeters = distMeters,
                bearingCardinal = cardinal,
                isCurrentlyInsideGeofence = isInsideGeofenceNow,
                isVisited = isVisited
            )
        }
    }

    val effectiveVisitedIds = remember(pointGeofenceStatuses) {
        pointGeofenceStatuses.filter { it.isVisited }.map { it.episode.id }.toSet()
    }

    // Build complete walk path coordinates combining route waypoints and triggered point locations
    val routeWaypoints: List<RouteWaypoint> = remember(activeRoute, triggeredPoints) {
        val base = activeRoute.waypoints.toMutableList()
        if (base.isEmpty()) {
            base.add(
                RouteWaypoint(
                    activeRoute.startLatitude,
                    activeRoute.startLongitude,
                    activeRoute.startBlockLabel
                )
            )
            triggeredPoints.forEach { ep ->
                base.add(RouteWaypoint(ep.latitude, ep.longitude, ep.title))
            }
            base.add(
                RouteWaypoint(
                    activeRoute.endLatitude,
                    activeRoute.endLongitude,
                    activeRoute.endBlockLabel
                )
            )
        }
        base
    }

    var selectedPointId by rememberSaveable { mutableStateOf<Long?>(null) }
    var mapEngineMode by rememberSaveable {
        mutableStateOf(TourMapEngineMode.KEYLESS_STREET_MAP)
    }
    var selectedMapTypeIndex by rememberSaveable { mutableIntStateOf(0) }
    var keylessFitBoundsTrigger by rememberSaveable { mutableIntStateOf(0) }
    var showGpxDialog by rememberSaveable { mutableStateOf(false) }

    val mapTypeLabel = TILE_LAYER_LABELS[selectedMapTypeIndex % TILE_LAYER_LABELS.size]

    if (showGpxDialog) {
        val gpxXml = remember(activeRoute, triggeredPoints) {
            TourExportHelper.buildGpxXml(activeRoute, triggeredPoints)
        }
        AlertDialog(
            onDismissRequest = { showGpxDialog = false },
            title = {
                Text(
                    text = "GPX 1.1 Walk Route & Historical Waypoints",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = gpxXml,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        TourExportHelper.launchShareSheet(
                            context = context,
                            subject = "${activeRoute.routeTitle}.gpx",
                            content = gpxXml
                        )
                        showGpxDialog = false
                    },
                    modifier = Modifier.testTag("dialog_share_gpx_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share GPX File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpxDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .testTag("tour_summary_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Tour Summary Header & Room Session Selector
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tour_summary_header_card"),
                    shape = RoundedCornerShape(22.dp),
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
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.tour_summary_header_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            FilledTonalButton(
                                onClick = onLogCurrentWalkSession,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("tour_summary_log_session_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocationAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.tour_summary_snapshot_btn),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = activeRoute.routeTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${activeRoute.neighborhoodName} (${activeRoute.cityRegion})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Walk Rewards & Badges Collection Quick Access Banner
                        OutlinedButton(
                            onClick = onOpenRewards,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tour_summary_open_rewards_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stringResource(R.string.rewards_view_collection_btn)} ($unlockedBadgesCount/$totalBadgesCount)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selector chips for completed Room walk routes & active session
                        Text(
                            text = "SELECT WALK SESSION FROM ROOM DATABASE (${allTourOptions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allTourOptions.forEachIndexed { idx, tourOption ->
                                val selected = idx == safeIndex
                                val isLiveSession = tourOption.route.routeId == -1L
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedTourIndex = idx },
                                    label = {
                                        Text(
                                            if (isLiveSession) {
                                                "Live Session (${tourOption.narratives.size} pts)"
                                            } else {
                                                "${tourOption.route.routeTitle.take(26)} (${tourOption.narratives.size} pts)"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isLiveSession) Icons.Default.MyLocation else Icons.Default.Route,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("tour_session_chip_$idx")
                                )
                            }
                        }
                    }
                }
            }

            // 1b. Historical Points Visited & GPS Geofence Trigger Progress Card
            item {
                GeofenceHistoricalProgressCard(
                    pointStatuses = pointGeofenceStatuses,
                    currentLocationState = currentLocationState,
                    onSelectAndFocusPoint = { pointEp ->
                        selectedPointId = pointEp.id
                    },
                    onWalkToNextPoint = { narrate ->
                        val nextTarget = pointGeofenceStatuses.firstOrNull { !it.isVisited }?.episode
                            ?: pointGeofenceStatuses.firstOrNull()?.episode
                        if (nextTarget != null) {
                            selectedPointId = nextTarget.id
                            onWalkToHistoricalPointGeofence(nextTarget, narrate)
                        }
                    },
                    onResetProgress = {
                        onResetGeofenceProgress()
                    }
                )
            }

            // 1c. Adaptive Low-Battery (<20%) GPS Polling & Map Tile Refresh Rate Saver Card
            item {
                BatteryPowerSaverCard(
                    batteryProfile = batteryPowerProfile,
                    onSelectOverrideMode = onSelectBatteryOverrideMode,
                    onSimulateLowBattery = onSimulateLowBattery
                )
            }

            // 2. Zero-Key Interactive Street & Satellite Map Preview Card + Offline Vector Radar
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_maps_route_preview_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tour_summary_map_preview_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${routeWaypoints.size} GPS path vertices • ${effectiveVisitedIds.size}/${triggeredPoints.size} geofence points visited",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Map Engine Switcher (Live Street & Satellite Map vs 100% Offline Vector Radar)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TourMapEngineMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mapEngineMode == mode,
                                    onClick = { mapEngineMode = mode },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("map_engine_chip_${mode.name.lowercase()}")
                                )
                            }
                        }

                        // Quick 1-Tap Tile Layer Selector Chips when in Live Street & Satellite Map mode
                        if (mapEngineMode == TourMapEngineMode.KEYLESS_STREET_MAP) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TILE_LAYER_LABELS.forEachIndexed { styleIdx, styleName ->
                                    FilterChip(
                                        selected = (selectedMapTypeIndex % TILE_LAYER_LABELS.size) == styleIdx,
                                        onClick = { selectedMapTypeIndex = styleIdx },
                                        label = { Text(styleName) },
                                        modifier = Modifier.testTag("tile_layer_chip_$styleIdx")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Map Viewport Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("tour_summary_map_container")
                        ) {
                            when (mapEngineMode) {
                                TourMapEngineMode.KEYLESS_STREET_MAP -> {
                                    KeylessInteractiveStreetMapView(
                                        waypoints = routeWaypoints,
                                        triggeredPoints = triggeredPoints,
                                        selectedPointId = selectedPointId ?: activeEpisodeId,
                                        tileStyleIndex = selectedMapTypeIndex,
                                        fitBoundsTrigger = keylessFitBoundsTrigger,
                                        onSelectPoint = { selectedPointId = it.id },
                                        onNarratePoint = { episode ->
                                            selectedPointId = episode.id
                                            onPlayEpisode(episode)
                                        },
                                        visitedPointIds = effectiveVisitedIds,
                                        currentGpsLat = currentLocationState.latitude,
                                        currentGpsLng = currentLocationState.longitude,
                                        geofenceRadiusMeters = geofenceRadiusMeters,
                                        isBatterySaverActive = batteryPowerProfile.isBatterySaverActive,
                                        mapTileRefreshThrottleMs = batteryPowerProfile.mapTileRefreshThrottleMs
                                    )
                                }

                                TourMapEngineMode.VECTOR_RADAR -> {
                                    // Tactical Topographic Vector Route Preview (100% offline fallback)
                                    InteractiveVectorTourCanvas(
                                        waypoints = routeWaypoints,
                                        triggeredPoints = triggeredPoints,
                                        visitedPointIds = effectiveVisitedIds,
                                        selectedPointId = selectedPointId ?: activeEpisodeId,
                                        onSelectPoint = { selectedPointId = it.id }
                                    )
                                }
                            }

                            // Floating Map Controls Overlay (Top-Right)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (mapEngineMode != TourMapEngineMode.VECTOR_RADAR) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .clickable {
                                                selectedMapTypeIndex = (selectedMapTypeIndex + 1) % TILE_LAYER_LABELS.size
                                            }
                                            .testTag("cycle_map_type_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Layers,
                                                contentDescription = "Cycle Map Style",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = mapTypeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .clickable {
                                            keylessFitBoundsTrigger += 1
                                        }
                                        .testTag("fit_route_bounds_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CenterFocusStrong,
                                            contentDescription = "Fit Walk Route",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Fit Route",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Session Telemetry & Export Bar
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val durationMin = (activeRoute.durationSeconds.coerceAtLeast(60) / 60)
                            TourTelemetryBadge("${activeRoute.totalDistanceMeters}m Walked")
                            TourTelemetryBadge("${durationMin}m Duration")
                            TourTelemetryBadge("${activeRoute.blocksVisitedCount} Blocks")
                            TourTelemetryBadge("${effectiveVisitedIds.size}/${triggeredPoints.size} Visited")
                            TourTelemetryBadge("Lens: ${activeRoute.editorialLens}")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // High-Value Export & Keyless Google Maps App Turn-by-Turn Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val postcard = TourExportHelper.buildStoryPostcardText(
                                        route = activeRoute,
                                        narratives = triggeredPoints
                                    )
                                    TourExportHelper.launchShareSheet(
                                        context = context,
                                        subject = "WanderCast Tour Summary: ${activeRoute.routeTitle}",
                                        content = postcard
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_story_postcard_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.tour_summary_share_postcard))
                            }

                            OutlinedButton(
                                onClick = { showGpxDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_gpx_track_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.tour_summary_export_gpx))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Zero-API-Key Google Maps App Turn-by-Turn Deep-Link Button
                        OutlinedButton(
                            onClick = {
                                val url = activeRoute.googleMapsUrl.ifBlank {
                                    "https://www.google.com/maps/dir/?api=1&travelmode=walking&origin=${activeRoute.startLatitude},${activeRoute.startLongitude}&destination=${activeRoute.endLatitude},${activeRoute.endLongitude}"
                                }
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_external_google_maps_walk_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.route_planner_title))
                        }
                    }
                }
            }

            // 3. Section Header: Key Historical & Interesting Points Triggered (from Room DB)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.tour_summary_points_section),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Retrieved from Room Database (${effectiveVisitedIds.size} of ${triggeredPoints.size} visited via GPS geofence)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. List of Triggered Historical & Architectural Points from Room
            itemsIndexed(
                items = pointGeofenceStatuses,
                key = { _, status -> "summary_point_${status.episode.id}" }
            ) { _, pointStatus ->
                val pointEpisode = pointStatus.episode
                val isHighlighted = pointEpisode.id == selectedPointId || pointEpisode.id == activeEpisodeId
                TriggeredHistoricalPointCard(
                    pointStatus = pointStatus,
                    geofenceRadiusMeters = geofenceRadiusMeters,
                    isHighlightedOnMap = isHighlighted,
                    onFocusOnMap = {
                        selectedPointId = pointEpisode.id
                    },
                    onWalkToGeofence = {
                        selectedPointId = pointEpisode.id
                        onWalkToHistoricalPointGeofence(pointEpisode, false)
                    },
                    onPlayNarrative = { onPlayEpisode(pointEpisode) },
                    onToggleBookmark = { onToggleEpisodeBookmark(pointEpisode) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/**
 * Displays an animated progress bar and checkpoint timeline showing the percentage of historical
 * points visited based on the user's current GPS location and geofence triggers.
 */
@Composable
private fun GeofenceHistoricalProgressCard(
    pointStatuses: List<HistoricalPointGeofenceStatus>,
    currentLocationState: LocationBlockState,
    onSelectAndFocusPoint: (EpisodeEntity) -> Unit,
    onWalkToNextPoint: (narrate: Boolean) -> Unit,
    onResetProgress: () -> Unit
) {
    val totalPoints = pointStatuses.size
    val visitedCount = pointStatuses.count { it.isVisited }
    val rawFraction = if (totalPoints > 0) {
        (visitedCount.toFloat() / totalPoints.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "visited_geofence_progress"
    )
    val percentageInt = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)
    val isComplete = totalPoints > 0 && visitedCount == totalPoints

    val accentColor by animateColorAsState(
        targetValue = if (isComplete) RadarTeal else BrassAmber,
        label = "progress_accent_color"
    )

    val nearestUnvisited = remember(pointStatuses) {
        pointStatuses.filter { !it.isVisited }.minByOrNull { it.distanceFromCurrentGpsMeters }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tour_summary_geofence_progress_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Title + Large Percentage Pill
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
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.GpsFixed,
                        contentDescription = "Geofence Tour Completion",
                        tint = accentColor
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.tour_summary_geofence_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor
                        )
                        Text(
                            text = "$visitedCount / $totalPoints (${currentLocationState.triggerThresholdMeters}m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = accentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Text(
                        text = "$percentageInt%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("tour_summary_visited_percentage_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Animated Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .testTag("tour_summary_visited_progress_bar"),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Checkpoint Timeline Chips for Each Historical Point
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pointStatuses.forEach { status ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (status.isVisited) {
                            RadarTeal.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (status.isVisited) RadarTeal else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectAndFocusPoint(status.episode) }
                            .testTag("geofence_checkpoint_chip_${status.stopNumber}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (status.isVisited) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (status.isVisited) RadarTeal else BrassAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Column {
                                Text(
                                    text = "0${status.stopNumber}: ${status.episode.title.take(18)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${status.distanceFromCurrentGpsMeters}m ${status.bearingCardinal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status.isVisited) RadarTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live GPS Telemetry & Nearest Geofence Distance Summary
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (nearestUnvisited != null) {
                            "0${nearestUnvisited.stopNumber} • ${nearestUnvisited.distanceFromCurrentGpsMeters}m ${nearestUnvisited.bearingCardinal}"
                        } else {
                            "100% ($totalPoints/$totalPoints)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "GPS %.4f, %.4f",
                            currentLocationState.latitude,
                            currentLocationState.longitude
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Geofence Stepper & Reset Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onWalkToNextPoint(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("tour_summary_walk_next_point_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tour_summary_walk_next_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                FilledTonalButton(
                    onClick = { onWalkToNextPoint(true) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("tour_summary_walk_narrate_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.tour_summary_walk_narrate_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                IconButton(
                    onClick = onResetProgress,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("tour_summary_reset_progress_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry_broadcast_btn),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggeredHistoricalPointCard(
    pointStatus: HistoricalPointGeofenceStatus,
    geofenceRadiusMeters: Int,
    isHighlightedOnMap: Boolean,
    onFocusOnMap: () -> Unit,
    onWalkToGeofence: () -> Unit,
    onPlayNarrative: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val episode = pointStatus.episode
    val stopNumber = pointStatus.stopNumber
    var transcriptExpanded by rememberSaveable { mutableStateOf(false) }
    val formattedTime = remember(episode.timestamp) {
        SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(episode.timestamp))
    }
    val coordLabel = remember(episode.latitude, episode.longitude) {
        String.format(
            Locale.US,
            "%.4f°N, %.4f°W",
            kotlin.math.abs(episode.latitude),
            kotlin.math.abs(episode.longitude)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("tour_summary_point_card_${episode.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isHighlightedOnMap) 2.dp else 1.dp,
            color = when {
                isHighlightedOnMap -> MaterialTheme.colorScheme.primary
                pointStatus.isVisited -> RadarTeal.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outline
            }
        )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (pointStatus.isVisited) RadarTeal else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (pointStatus.isVisited) "✓$stopNumber" else "0$stopNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A1419)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${episode.category.uppercase(Locale.US)} • $formattedTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("summary_point_bookmark_btn_${episode.id}")
                ) {
                    Icon(
                        imageVector = if (episode.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark Historical Point",
                        tint = if (episode.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Geofence Proximity & Visit Status Pill
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (pointStatus.isVisited) {
                        RadarTeal.copy(alpha = 0.18f)
                    } else {
                        BrassAmber.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pointStatus.isVisited) RadarTeal else BrassAmber.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = when {
                            pointStatus.isCurrentlyInsideGeofence ->
                                "✓ INSIDE ${geofenceRadiusMeters}M GEOFENCE (${pointStatus.distanceFromCurrentGpsMeters}M AWAY)"
                            pointStatus.isVisited ->
                                "✓ VISITED • GEOFENCE TRIGGERED (${pointStatus.distanceFromCurrentGpsMeters}M ${pointStatus.bearingCardinal})"
                            else ->
                                "⏳ UPCOMING • ${pointStatus.distanceFromCurrentGpsMeters}M ${pointStatus.bearingCardinal} FROM CURRENT GPS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pointStatus.isVisited) RadarTeal else BrassAmber,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${episode.locationLabel} • $coordLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opening Radio Hook
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "“${episode.hookLine}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Key Historical & Architectural Highlights from Room
            if (episode.fieldFacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "KEY HISTORICAL & ARCHITECTURAL HIGHLIGHTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                episode.fieldFacts.forEach { fact ->
                    Text(
                        text = "• $fact",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // Visual Explorer Cue
            if (episode.explorerPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = episode.explorerPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Room Audio Narrative Metadata Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TourTelemetryBadge("Voice: ${episode.voicePersona}")
                TourTelemetryBadge("${episode.speechRate}x")
                TourTelemetryBadge("${episode.wordCount} words • ${episode.durationSeconds}s")
                TourTelemetryBadge("Model: ${episode.aiModelUsed}")
            }

            AnimatedVisibility(visible = transcriptExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FULL 60-SECOND DOCUMENTARY TRANSCRIPT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episode.narrativeScript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Narrate Audio, Walk Here (Trigger Geofence), Focus Map, Expand Transcript
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPlayNarrative,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("summary_point_play_btn_${episode.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Narrate")
                    }

                    OutlinedButton(
                        onClick = onWalkToGeofence,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("summary_point_walk_geofence_btn_${episode.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (pointStatus.isVisited) "Visited ✓" else "Walk Here")
                    }

                    OutlinedButton(
                        onClick = onFocusOnMap,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("summary_point_focus_map_btn_${episode.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus Map")
                    }
                }

                IconButton(
                    onClick = { transcriptExpanded = !transcriptExpanded },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("summary_point_expand_btn_${episode.id}")
                ) {
                    Icon(
                        imageVector = if (transcriptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (transcriptExpanded) "Collapse transcript" else "Expand transcript"
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveVectorTourCanvas(
    waypoints: List<RouteWaypoint>,
    triggeredPoints: List<EpisodeEntity>,
    visitedPointIds: Set<Long>,
    selectedPointId: Long?,
    onSelectPoint: (EpisodeEntity) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val visitedColor = RadarTeal
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cols = 8
            val rows = 6
            for (i in 1 until cols) {
                val x = (w / cols) * i
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            for (j in 1 until rows) {
                val y = (h / rows) * j
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val allLats = waypoints.map { it.latitude } + triggeredPoints.map { it.latitude }
            val allLngs = waypoints.map { it.longitude } + triggeredPoints.map { it.longitude }
            if (allLats.isEmpty() || allLngs.isEmpty()) return@Canvas

            val minLat = allLats.minOrNull() ?: 37.7950
            val maxLat = allLats.maxOrNull() ?: 37.7980
            val minLng = allLngs.minOrNull() ?: -122.4060
            val maxLng = allLngs.maxOrNull() ?: -122.4020

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0008)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0008)
            val padX = w * 0.14f
            val padY = h * 0.18f
            val usableW = (w - padX * 2).coerceAtLeast(10f)
            val usableH = (h - padY * 2).coerceAtLeast(10f)

            fun toScreen(lat: Double, lng: Double): Offset {
                val nx = ((lng - minLng) / lngSpan).toFloat().coerceIn(0f, 1f)
                val ny = (1f - ((lat - minLat) / latSpan).toFloat()).coerceIn(0f, 1f)
                return Offset(padX + nx * usableW, padY + ny * usableH)
            }

            val routeScreenPts = waypoints.map { toScreen(it.latitude, it.longitude) }
            if (routeScreenPts.size >= 2) {
                val path = Path().apply {
                    moveTo(routeScreenPts.first().x, routeScreenPts.first().y)
                    for (i in 1 until routeScreenPts.size) {
                        lineTo(routeScreenPts[i].x, routeScreenPts[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                    )
                )
            }

            triggeredPoints.forEach { ep ->
                val center = toScreen(ep.latitude, ep.longitude)
                val selected = ep.id == selectedPointId
                val visited = visitedPointIds.contains(ep.id)
                val dotColor = if (visited) visitedColor else primaryColor
                drawCircle(
                    color = dotColor.copy(alpha = if (selected) 0.34f else 0.20f),
                    radius = if (selected) 30f else 22f,
                    center = center
                )
                drawCircle(
                    color = dotColor,
                    radius = if (selected) 10f else 8f,
                    center = center
                )
            }
        }

        // Interactive Legend pills at bottom of Vector Radar fallback
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            triggeredPoints.forEachIndexed { idx, ep ->
                val visited = visitedPointIds.contains(ep.id)
                FilterChip(
                    selected = ep.id == selectedPointId,
                    onClick = { onSelectPoint(ep) },
                    label = {
                        Text(
                            if (visited) "✓ 0${idx + 1}: ${ep.title.take(16)}…"
                            else "0${idx + 1}: ${ep.title.take(18)}…"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (visited) Icons.Default.CheckCircle else Icons.Default.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TourTelemetryBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
