package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EpisodeEntity
import com.example.data.RouteWaypoint
import com.example.data.WalkRouteEntity
import com.example.data.WalkRouteWithNarratives
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FieldTapesArchiveScreen(
    episodes: List<EpisodeEntity>,
    completedWalkRoutes: List<WalkRouteWithNarratives>,
    activeEpisodeId: Long?,
    onReplayEpisode: (EpisodeEntity) -> Unit,
    onToggleBookmark: (EpisodeEntity) -> Unit,
    onDeleteEpisode: (Long) -> Unit,
    onLogCurrentWalkRoute: () -> Unit,
    onToggleRouteFavorite: (WalkRouteEntity) -> Unit,
    onDeleteWalkRoute: (Long) -> Unit,
    onGoToStudioAndBroadcast: () -> Unit,
    onNavigateBack: () -> Unit,
    unlockedBadgesCount: Int = 0,
    totalBadgesCount: Int = 8,
    onOpenRewards: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    // 0 = Completed Walk Routes (Tour History), 1 = Audio Narratives (Field Tapes)
    var selectedSectionTab by rememberSaveable { mutableIntStateOf(0) }
    var onlyBookmarked by rememberSaveable { mutableStateOf(false) }

    val filteredEpisodes = remember(episodes, onlyBookmarked) {
        if (onlyBookmarked) episodes.filter { it.isBookmarked } else episodes
    }

    val filteredRoutes = remember(completedWalkRoutes, onlyBookmarked) {
        if (onlyBookmarked) {
            completedWalkRoutes.filter { it.route.isFavorite }
        } else {
            completedWalkRoutes
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
                .testTag("field_tapes_archive_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Section Switcher Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.archive_header_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            FilledTonalButton(
                                onClick = onLogCurrentWalkRoute,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("log_current_walk_route_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocationAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.archive_save_walk_btn),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.archive_header_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Walk Rewards & Badges Collection Quick Access Banner
                        OutlinedButton(
                            onClick = onOpenRewards,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("archive_open_rewards_btn")
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

                        // Segmented Section Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedSectionTab == 0,
                                onClick = { selectedSectionTab = 0 },
                                label = {
                                    Text("${stringResource(R.string.rewards_stat_tours)} (${completedWalkRoutes.size})")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Route,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_completed_walk_routes")
                            )

                            FilterChip(
                                selected = selectedSectionTab == 1,
                                onClick = { selectedSectionTab = 1 },
                                label = {
                                    Text("${stringResource(R.string.nav_archive)} (${episodes.size})")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_audio_narratives")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bookmark filter row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !onlyBookmarked,
                                onClick = { onlyBookmarked = false },
                                label = {
                                    Text(
                                        if (selectedSectionTab == 0) {
                                            "${stringResource(R.string.rewards_stat_tours)} (${completedWalkRoutes.size})"
                                        } else {
                                            "${stringResource(R.string.nav_archive)} (${episodes.size})"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("filter_all_tapes_chip")
                            )
                            FilterChip(
                                selected = onlyBookmarked,
                                onClick = { onlyBookmarked = true },
                                label = {
                                    val count = if (selectedSectionTab == 0) {
                                        completedWalkRoutes.count { it.route.isFavorite }
                                    } else {
                                        episodes.count { it.isBookmarked }
                                    }
                                    Text("★ ($count)")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("filter_bookmarked_tapes_chip")
                            )
                        }
                    }
                }
            }

            if (selectedSectionTab == 0) {
                if (filteredRoutes.isEmpty()) {
                    item {
                        EmptyArchiveCard(
                            onlyBookmarked = onlyBookmarked,
                            isRouteTab = true,
                            onRecordFirstEpisode = onLogCurrentWalkRoute
                        )
                    }
                } else {
                    items(filteredRoutes, key = { "route_${it.route.routeId}" }) { routeWithNarratives ->
                        CompletedWalkRouteCard(
                            routeWithNarratives = routeWithNarratives,
                            activeEpisodeId = activeEpisodeId,
                            onReplayEpisode = onReplayEpisode,
                            onToggleFavorite = { onToggleRouteFavorite(routeWithNarratives.route) },
                            onDeleteRoute = { onDeleteWalkRoute(routeWithNarratives.route.routeId) }
                        )
                    }
                }
            } else {
                if (filteredEpisodes.isEmpty()) {
                    item {
                        EmptyArchiveCard(
                            onlyBookmarked = onlyBookmarked,
                            isRouteTab = false,
                            onRecordFirstEpisode = onGoToStudioAndBroadcast
                        )
                    }
                } else {
                    items(filteredEpisodes, key = { "ep_${it.id}" }) { episode ->
                        ArchivedEpisodeCard(
                            episode = episode,
                            isCurrentlyLoaded = episode.id == activeEpisodeId,
                            onReplay = { onReplayEpisode(episode) },
                            onToggleBookmark = { onToggleBookmark(episode) },
                            onDelete = { onDeleteEpisode(episode.id) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletedWalkRouteCard(
    routeWithNarratives: WalkRouteWithNarratives,
    activeEpisodeId: Long?,
    onReplayEpisode: (EpisodeEntity) -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteRoute: () -> Unit
) {
    val context = LocalContext.current
    val route = routeWithNarratives.route
    val narratives = remember(routeWithNarratives.narratives) {
        routeWithNarratives.narratives.sortedByDescending { it.timestamp }
    }
    var expanded by rememberSaveable { mutableStateOf(true) }

    val formattedDate = remember(route.completedAtTimestamp) {
        SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(route.completedAtTimestamp))
    }
    val durationMinutes = remember(route.durationSeconds) {
        (route.durationSeconds.coerceAtLeast(60) / 60)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("completed_walk_route_card_${route.routeId}"),
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
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${route.editorialLens.uppercase(Locale.US)} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("route_favorite_btn_${route.routeId}")
                    ) {
                        Icon(
                            imageVector = if (route.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Favorite Walk Route",
                            tint = if (route.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDeleteRoute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("route_delete_btn_${route.routeId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Walk Route",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = route.routeTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${route.neighborhoodName} • ${route.cityRegion}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Canvas Mini Route Path Map
            MiniWalkRouteCanvasMap(
                waypoints = route.waypoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Route Telemetry Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataBadge(
                    label = "${route.totalDistanceMeters}m walked"
                )
                MetadataBadge(
                    label = "${durationMinutes}m duration"
                )
                MetadataBadge(
                    label = "${route.blocksVisitedCount} blocks visited"
                )
                MetadataBadge(
                    label = "${narratives.size} audio narratives"
                )
                MetadataBadge(
                    label = "${route.waypoints.size} GPS waypoints"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val url = route.googleMapsUrl.ifBlank {
                            "https://www.google.com/maps/dir/?api=1&origin=${route.startLatitude},${route.startLongitude}&destination=${route.endLatitude},${route.endLongitude}&travelmode=walking"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("route_open_maps_btn_${route.routeId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retrace Walk in Maps")
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("route_expand_btn_${route.routeId}")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide route narratives" else "Show route narratives"
                    )
                }
            }

            AnimatedVisibility(visible = expanded && narratives.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text(
                        text = "AUDIO NARRATIVES TRIGGERED ON THIS WALK (${narratives.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    narratives.forEach { episode ->
                        val isLoaded = episode.id == activeEpisodeId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isLoaded) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = episode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${episode.voicePersona} • ${episode.speechRate}x • ${episode.wordCount} words • ${episode.durationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onReplayEpisode(episode) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("route_episode_replay_${episode.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Replay Narrative",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniWalkRouteCanvasMap(
    waypoints: List<RouteWaypoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    Box(
        modifier = modifier.background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw subtle radar grid lines
            val cols = 6
            val rows = 3
            for (i in 1 until cols) {
                val x = (w / cols) * i
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }
            for (j in 1 until rows) {
                val y = (h / rows) * j
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            if (waypoints.isEmpty()) return@Canvas

            val minLat = waypoints.minOf { it.latitude }
            val maxLat = waypoints.maxOf { it.latitude }
            val minLng = waypoints.minOf { it.longitude }
            val maxLng = waypoints.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

            val padX = w * 0.14f
            val padY = h * 0.22f
            val usableW = (w - padX * 2).coerceAtLeast(10f)
            val usableH = (h - padY * 2).coerceAtLeast(10f)

            val points = waypoints.mapIndexed { index, wp ->
                val normX = if (waypoints.size == 1) 0.5f else ((wp.longitude - minLng) / lngSpan).toFloat()
                val normY = if (waypoints.size == 1) 0.5f else (1f - ((wp.latitude - minLat) / latSpan).toFloat())
                // Add slight horizontal spread if all coordinates are identical
                val spreadX = if (maxLng == minLng && waypoints.size > 1) {
                    index.toFloat() / (waypoints.size - 1).toFloat()
                } else {
                    normX
                }
                Offset(
                    x = padX + spreadX * usableW,
                    y = padY + normY * usableH
                )
            }

            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.28f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = 4.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                    )
                )
            }

            points.forEachIndexed { idx, pt ->
                val isFirst = idx == 0
                val isLast = idx == points.lastIndex
                drawCircle(
                    color = if (isLast) primaryColor else secondaryColor,
                    radius = if (isFirst || isLast) 8f else 5f,
                    center = pt
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (waypoints.isNotEmpty()) {
                    "${waypoints.first().label} → ${waypoints.last().label}"
                } else {
                    "GPS Breadcrumb Trail"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetadataBadge(label: String) {
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

@Composable
private fun EmptyArchiveCard(
    onlyBookmarked: Boolean,
    isRouteTab: Boolean,
    onRecordFirstEpisode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_archive_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isRouteTab) Icons.Default.Route else Icons.Default.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    onlyBookmarked && isRouteTab -> "No Bookmarked Walk Routes Yet"
                    onlyBookmarked -> "No Bookmarked Field Tapes Yet"
                    isRouteTab -> "No Completed Walk Routes Logged Yet"
                    else -> stringResource(R.string.empty_archive_title)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    onlyBookmarked -> "Tap the bookmark icon on any walk route or audio documentary to save it to your favorites."
                    isRouteTab -> "Tap 'Save Current Walk' above or generate a 3-Stop Walking Route in the On-Air Studio to archive your GPS route and linked audio dispatches."
                    else -> stringResource(R.string.empty_archive_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!onlyBookmarked) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRecordFirstEpisode,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_archive_broadcast_btn")
                ) {
                    Text(
                        if (isRouteTab) {
                            "Log Current Walk Session Now"
                        } else {
                            "Return to On-Air Studio & Broadcast"
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchivedEpisodeCard(
    episode: EpisodeEntity,
    isCurrentlyLoaded: Boolean,
    onReplay: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val formattedDate = remember(episode.timestamp) {
        SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(episode.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("archived_episode_card_${episode.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrentlyLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${episode.category.uppercase(Locale.US)} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("archive_bookmark_btn_${episode.id}")
                    ) {
                        Icon(
                            imageVector = if (episode.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (episode.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("archive_delete_btn_${episode.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Episode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = episode.locationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Narrative Metadata Chips stored in Room
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataBadge(label = "Voice: ${episode.voicePersona}")
                MetadataBadge(label = "${episode.speechRate}x Speed")
                MetadataBadge(label = "${episode.wordCount} words • ${episode.durationSeconds}s")
                MetadataBadge(label = "Model: ${episode.aiModelUsed}")
                MetadataBadge(label = "Plays: ${episode.playCount}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "“${episode.hookLine}”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = episode.narrativeScript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (episode.fieldFacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        episode.fieldFacts.forEach { fact ->
                            Text(
                                text = "• $fact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onReplay,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("archive_replay_btn_${episode.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_replay_btn))
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("archive_expand_btn_${episode.id}")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse transcript" else "Expand transcript"
                    )
                }
            }
        }
    }
}
