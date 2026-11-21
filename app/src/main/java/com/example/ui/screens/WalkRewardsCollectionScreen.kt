package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppEventEntity
import com.example.data.WalkBadgeEntity
import com.example.data.WalkRouteWithNarratives
import com.example.ui.components.SmartEngagementTelemetryCard
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.RadarTeal
import com.example.ui.theme.SignalCoral
import com.example.util.SmartEngagementTelemetryState
import com.example.util.SmartPromptType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class BadgeTitleAndDesc(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun resolveBadgeMetadata(badgeId: String): BadgeTitleAndDesc {
    return when (badgeId) {
        "early_bird" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_early_bird_title,
            descRes = R.string.badge_early_bird_desc,
            icon = Icons.Default.WbSunny,
            accentColor = BrassAmber
        )
        "marathoner" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_marathoner_title,
            descRes = R.string.badge_marathoner_desc,
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            accentColor = RadarTeal
        )
        "first_transmission" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_first_transmission_title,
            descRes = R.string.badge_first_transmission_desc,
            icon = Icons.Default.CellTower,
            accentColor = BrassAmber
        )
        "architecture_critic" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_architecture_critic_title,
            descRes = R.string.badge_architecture_critic_desc,
            icon = Icons.Default.AccountBalance,
            accentColor = RadarTeal
        )
        "geofence_pioneer" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_geofence_pioneer_title,
            descRes = R.string.badge_geofence_pioneer_desc,
            icon = Icons.Default.Explore,
            accentColor = RadarTeal
        )
        "deep_historian" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_deep_historian_title,
            descRes = R.string.badge_deep_historian_desc,
            icon = Icons.Default.HistoryEdu,
            accentColor = BrassAmber
        )
        "night_owl" -> BadgeTitleAndDesc(
            titleRes = R.string.badge_night_owl_title,
            descRes = R.string.badge_night_owl_desc,
            icon = Icons.Default.NightsStay,
            accentColor = SignalCoral
        )
        else -> BadgeTitleAndDesc(
            titleRes = R.string.badge_trivia_virtuoso_title,
            descRes = R.string.badge_trivia_virtuoso_desc,
            icon = Icons.Default.EmojiEvents,
            accentColor = BrassAmber
        )
    }
}

@Composable
fun WalkRewardsCollectionScreen(
    walkBadges: List<WalkBadgeEntity>,
    completedWalkRoutes: List<WalkRouteWithNarratives>,
    newlyUnlockedBadgeId: String?,
    onDismissNewlyUnlockedBanner: () -> Unit,
    onSimulateCompleteBadgeMilestone: (badgeId: String, titleText: String, descText: String) -> Unit,
    onNarrateBadgeAchievement: (titleText: String, descText: String, tourTitle: String?, xp: Int) -> Unit,
    onOpenTourSummary: () -> Unit,
    onNavigateBack: () -> Unit,
    engagementTelemetryState: SmartEngagementTelemetryState = SmartEngagementTelemetryState(),
    recentAppEvents: List<AppEventEntity> = emptyList(),
    onSimulatePeakDelightAutoPopup: () -> Unit = {},
    onPreviewSmartPopup: (SmartPromptType) -> Unit = {},
    onResetSmartPromptCooldowns: () -> Unit = {},
    onClearLoggedAppEvents: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    // 0 = All Badges, 1 = Unlocked, 2 = In Progress
    var filterTab by rememberSaveable { mutableIntStateOf(0) }

    val unlockedCount = remember(walkBadges) {
        walkBadges.count { it.isUnlocked }
    }
    val totalXpEarned = remember(walkBadges) {
        walkBadges.filter { it.isUnlocked }.sumOf { it.xpReward }
    }
    val totalMaxXp = remember(walkBadges) {
        walkBadges.sumOf { it.xpReward }.coerceAtLeast(1000)
    }

    val filteredBadges = remember(walkBadges, filterTab) {
        when (filterTab) {
            1 -> walkBadges.filter { it.isUnlocked }
            2 -> walkBadges.filter { !it.isUnlocked }
            else -> walkBadges
        }
    }

    val rankTitle = when {
        totalXpEarned >= 1600 -> stringResource(R.string.rewards_rank_legend)
        totalXpEarned >= 1000 -> stringResource(R.string.rewards_rank_archivist)
        totalXpEarned >= 450 -> stringResource(R.string.rewards_rank_chronicler)
        else -> stringResource(R.string.rewards_rank_novice)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .testTag("walk_rewards_collection_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Newly Unlocked Celebration Banner (if triggered)
            if (newlyUnlockedBadgeId != null) {
                val unlockedBadge = walkBadges.firstOrNull { it.badgeId == newlyUnlockedBadgeId }
                if (unlockedBadge != null) {
                    item {
                        val meta = resolveBadgeMetadata(unlockedBadge.badgeId)
                        NewlyUnlockedCelebrationBanner(
                            badgeTitle = stringResource(meta.titleRes),
                            xpReward = unlockedBadge.xpReward,
                            tourTitle = unlockedBadge.unlockedTourTitle,
                            onDismiss = onDismissNewlyUnlockedBanner
                        )
                    }
                }
            }

            // 2. Explorer Rank, XP Vault & Room DB Rewards Summary Card
            item {
                ExplorerRankAndVaultSummaryCard(
                    rankTitle = rankTitle,
                    unlockedCount = unlockedCount,
                    totalBadgesCount = walkBadges.size.coerceAtLeast(8),
                    totalXpEarned = totalXpEarned,
                    totalMaxXp = totalMaxXp,
                    completedToursCount = completedWalkRoutes.size,
                    filterTab = filterTab,
                    onSelectFilterTab = { filterTab = it },
                    onOpenTourSummary = onOpenTourSummary
                )
            }

            // 3. Visual Digital Badge Collection Cards from Room DB
            items(
                items = filteredBadges,
                key = { "walk_badge_${it.badgeId}" }
            ) { badge ->
                val meta = resolveBadgeMetadata(badge.badgeId)
                val localizedTitle = stringResource(meta.titleRes)
                val localizedDesc = stringResource(meta.descRes)

                DigitalBadgeCollectionCard(
                    badge = badge,
                    localizedTitle = localizedTitle,
                    localizedDescription = localizedDesc,
                    icon = meta.icon,
                    accentColor = meta.accentColor,
                    onUnlockMilestone = {
                        onSimulateCompleteBadgeMilestone(
                            badge.badgeId,
                            localizedTitle,
                            localizedDesc
                        )
                    },
                    onNarrateAchievement = {
                        onNarrateBadgeAchievement(
                            localizedTitle,
                            localizedDesc,
                            badge.unlockedTourTitle,
                            badge.xpReward
                        )
                    }
                )
            }

            // 4. Smart Engagement Auto-Popup Engine & Room App Event Telemetry Inspector Card
            item {
                SmartEngagementTelemetryCard(
                    telemetryState = engagementTelemetryState,
                    recentAppEvents = recentAppEvents,
                    onSimulatePeakDelightTrigger = onSimulatePeakDelightAutoPopup,
                    onPreviewPopup = onPreviewSmartPopup,
                    onResetCooldowns = onResetSmartPromptCooldowns,
                    onClearEvents = onClearLoggedAppEvents
                )
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun NewlyUnlockedCelebrationBanner(
    badgeTitle: String,
    xpReward: Int,
    tourTitle: String?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("newly_unlocked_badge_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.5.dp, BrassAmber)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrassAmber.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BrassAmber
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.rewards_newly_unlocked_banner),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrassAmber
                    )
                    Text(
                        text = "$badgeTitle (+$xpReward XP)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (!tourTitle.isNullOrBlank()) {
                        Text(
                            text = tourTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_newly_unlocked_banner_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dialog_close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ExplorerRankAndVaultSummaryCard(
    rankTitle: String,
    unlockedCount: Int,
    totalBadgesCount: Int,
    totalXpEarned: Int,
    totalMaxXp: Int,
    completedToursCount: Int,
    filterTab: Int,
    onSelectFilterTab: (Int) -> Unit,
    onOpenTourSummary: () -> Unit
) {
    val xpFraction = (totalXpEarned.toFloat() / totalMaxXp.toFloat()).coerceIn(0f, 1f)
    val animatedXpProgress by animateFloatAsState(
        targetValue = xpFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "xp_progress_anim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rewards_vault_header_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = BrassAmber
                )
                Text(
                    text = stringResource(R.string.rewards_header_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrassAmber
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.rewards_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.rewards_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Explorer Rank Banner + XP Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BrassAmber.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.rewards_explorer_rank_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrassAmber
                            )
                            Text(
                                text = rankTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = BrassAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, BrassAmber)
                        ) {
                            Text(
                                text = "$totalXpEarned / $totalMaxXp XP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrassAmber,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { animatedXpProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("rewards_xp_progress_bar"),
                        color = BrassAmber,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Telemetry Stat Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RewardStatTile(
                    label = stringResource(R.string.rewards_stat_unlocked),
                    value = "$unlockedCount / $totalBadgesCount",
                    accentColor = RadarTeal,
                    modifier = Modifier.weight(1f)
                )
                RewardStatTile(
                    label = stringResource(R.string.rewards_stat_total_xp),
                    value = "$totalXpEarned XP",
                    accentColor = BrassAmber,
                    modifier = Modifier.weight(1f)
                )
                RewardStatTile(
                    label = stringResource(R.string.rewards_stat_tours),
                    value = "$completedToursCount",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips: All / Unlocked / In Progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterTab == 0,
                    onClick = { onSelectFilterTab(0) },
                    label = {
                        Text("${stringResource(R.string.rewards_filter_all)} ($totalBadgesCount)")
                    },
                    modifier = Modifier.testTag("rewards_filter_all_chip")
                )
                FilterChip(
                    selected = filterTab == 1,
                    onClick = { onSelectFilterTab(1) },
                    label = {
                        Text("${stringResource(R.string.rewards_filter_unlocked)} ($unlockedCount)")
                    },
                    modifier = Modifier.testTag("rewards_filter_unlocked_chip")
                )
                FilterChip(
                    selected = filterTab == 2,
                    onClick = { onSelectFilterTab(2) },
                    label = {
                        val inProg = (totalBadgesCount - unlockedCount).coerceAtLeast(0)
                        Text("${stringResource(R.string.rewards_filter_in_progress)} ($inProg)")
                    },
                    modifier = Modifier.testTag("rewards_filter_in_progress_chip")
                )
            }
        }
    }
}

@Composable
private fun RewardStatTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun DigitalBadgeCollectionCard(
    badge: WalkBadgeEntity,
    localizedTitle: String,
    localizedDescription: String,
    icon: ImageVector,
    accentColor: Color,
    onUnlockMilestone: () -> Unit,
    onNarrateAchievement: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = badge.progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "badge_progress_${badge.badgeId}"
    )

    val borderColor by animateColorAsState(
        targetValue = if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.outline,
        label = "badge_border_${badge.badgeId}"
    )

    val formattedDate = remember(badge.unlockedAtTimestamp) {
        badge.unlockedAtTimestamp?.let {
            SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(it))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("walk_badge_card_${badge.badgeId}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (badge.isUnlocked) 1.8.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Animated Canvas Emblem Crest
                BadgeCrestMedallion(
                    icon = icon,
                    accentColor = accentColor,
                    isUnlocked = badge.isUnlocked,
                    modifier = Modifier.size(76.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (badge.isUnlocked) {
                                accentColor.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(
                                text = if (badge.isUnlocked) {
                                    stringResource(R.string.rewards_badge_unlocked_pill)
                                } else {
                                    stringResource(R.string.rewards_badge_locked_pill)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = "+${badge.xpReward} XP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrassAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = localizedTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = localizedDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Milestone Progress Bar & Counter
            val localizedUnit = when (badge.progressUnit.lowercase(Locale.US)) {
                "tours" -> stringResource(R.string.unit_tours)
                "dawn_walks" -> stringResource(R.string.unit_dawn_walks)
                "meters" -> stringResource(R.string.unit_meters)
                "dispatches" -> stringResource(R.string.unit_dispatches)
                "checkpoints" -> stringResource(R.string.unit_checkpoints)
                "night_walks" -> stringResource(R.string.unit_night_walks)
                else -> stringResource(R.string.unit_pts)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${badge.currentProgress} / ${badge.targetProgress} $localizedUnit",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("badge_progress_bar_${badge.badgeId}"),
                color = if (badge.isUnlocked) accentColor else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Unlocked Tour Metadata or Milestone Action Button
            Spacer(modifier = Modifier.height(12.dp))

            if (badge.isUnlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!badge.unlockedTourTitle.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = badge.unlockedTourTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (formattedDate != null) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onNarrateAchievement,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("narrate_badge_btn_${badge.badgeId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.rewards_narrate_badge_btn),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onUnlockMilestone,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("unlock_badge_milestone_btn_${badge.badgeId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.rewards_complete_milestone_btn),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onNarrateAchievement,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("preview_badge_voice_btn_${badge.badgeId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_preview_badge_audio),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCrestMedallion(
    icon: ImageVector,
    accentColor: Color,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_crest_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension / 2f

            if (isUnlocked) {
                // Radiant metallic halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // Outer octagon / starburst gear teeth
                val rays = 12
                for (i in 0 until rays) {
                    val angle = Math.toRadians((i * (360.0 / rays)))
                    val r1 = maxR * 0.78f
                    val r2 = maxR * 0.94f
                    drawLine(
                        color = accentColor.copy(alpha = 0.75f),
                        start = Offset(
                            cx + (r1 * cos(angle)).toFloat(),
                            cy + (r1 * sin(angle)).toFloat()
                        ),
                        end = Offset(
                            cx + (r2 * cos(angle)).toFloat(),
                            cy + (r2 * sin(angle)).toFloat()
                        ),
                        strokeWidth = 2.2.dp.toPx()
                    )
                }

                drawCircle(
                    color = accentColor,
                    radius = maxR * 0.76f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            } else {
                // Locked dashed blueprint ring
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = maxR * 0.78f,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = if (isUnlocked) {
                accentColor.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isUnlocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
