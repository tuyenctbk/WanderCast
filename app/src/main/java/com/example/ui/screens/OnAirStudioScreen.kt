package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.NarratorState
import com.example.data.DocumentaryLens
import com.example.data.EpisodeEntity
import com.example.location.BatteryPowerProfileState
import com.example.location.BatteryPowerSavingOverride
import com.example.location.LocationBlockState
import com.example.ui.components.ArchitectureVisionScannerCard
import com.example.ui.components.BatteryPowerSaverCard
import com.example.ui.components.DocumentaryLensSelector
import com.example.ui.components.ForegroundLocationServiceCard
import com.example.ui.components.HeroBroadcastHeader
import com.example.ui.components.LiveVoiceNarratorDeck
import com.example.ui.components.LookaheadHeadingBufferCard
import com.example.ui.components.SmartQuotaAndSpatialCacheCard
import com.example.ui.components.UrbanTriviaChallengeCard
import com.example.ui.components.WalkingRoutePlannerCard
import com.example.viewmodel.AiUsageTier
import com.example.viewmodel.EpisodeGenerationState
import com.example.viewmodel.LiveHostQaState
import com.example.viewmodel.LookaheadPrefetchState
import com.example.viewmodel.QuotaAndCacheStats
import com.example.viewmodel.UrbanTriviaState
import com.example.viewmodel.VisionScannerState
import com.example.viewmodel.WalkingRouteState

@Composable
fun OnAirStudioScreen(
    locationState: LocationBlockState,
    selectedLens: DocumentaryLens,
    quotaStats: QuotaAndCacheStats,
    episodeState: EpisodeGenerationState,
    activeEpisode: EpisodeEntity?,
    narratorState: NarratorState,
    autoNarrateEnabled: Boolean,
    liveHostQaState: LiveHostQaState,
    visionScannerState: VisionScannerState,
    walkingRouteState: WalkingRouteState,
    lookaheadState: LookaheadPrefetchState,
    urbanTriviaState: UrbanTriviaState = UrbanTriviaState.Idle,
    hasLocationPermission: Boolean,
    isDarkTheme: Boolean,
    onRequestLocationPermission: () -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onSelectLens: (DocumentaryLens) -> Unit,
    onSelectUsageTier: (AiUsageTier) -> Unit,
    onToggleSpatialCache: (Boolean) -> Unit,
    onChangeDailyBudget: (Int) -> Unit = {},
    onSaveByokKey: (String) -> Unit = {},
    onClearByokKey: () -> Unit = {},
    onPrefetchNextBlock: () -> Unit,
    onPlayPrefetchedEpisode: (EpisodeEntity) -> Unit,
    onBroadcastCurrentBlock: () -> Unit,
    onWalkOneBlockForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onToggleAutoNarrate: (Boolean) -> Unit,
    onCycleVoicePersona: () -> Unit,
    onToggleBookmark: (EpisodeEntity) -> Unit,
    onAskHostQuestion: (String) -> Unit,
    onPickArchitecturePhoto: () -> Unit,
    onGenerateWalkingRoute: () -> Unit,
    onGenerateUrbanTrivia: () -> Unit = {},
    onSubmitTriviaAnswer: (Int) -> Unit = {},
    currentLanguageTag: String = "en",
    onOpenLanguagePicker: () -> Unit = {},
    batteryPowerProfile: BatteryPowerProfileState = BatteryPowerProfileState(),
    onSelectBatteryOverrideMode: (BatteryPowerSavingOverride) -> Unit = {},
    onSimulateLowBattery: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var followUpQuestion by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("on_air_studio_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Editorial Hero Banner
            item {
                HeroBroadcastHeader(
                    locationState = locationState,
                    isSpeaking = narratorState.isSpeaking,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    currentLanguageTag = currentLanguageTag,
                    onOpenLanguagePicker = onOpenLanguagePicker
                )
            }

            // 2. GPS Permission Banner (if not granted)
            if (!hasLocationPermission) {
                item {
                    GpsPermissionPromptCard(
                        onRequestPermission = onRequestLocationPermission
                    )
                }
            }

            // 3. Primary Walk & Broadcast Action Row (immediately accessible above the fold)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onBroadcastCurrentBlock,
                        modifier = Modifier
                            .weight(1.08f)
                            .height(52.dp)
                            .testTag("broadcast_block_button"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = stringResource(R.string.cd_broadcast_block),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.generate_episode_btn),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onWalkOneBlockForward,
                        modifier = Modifier
                            .weight(1.0f)
                            .height(52.dp)
                            .testTag("step_forward_block_button"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = stringResource(R.string.cd_walk_one_block),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.step_forward_btn),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 4. Documentary Lens Selector
            item {
                DocumentaryLensSelector(
                    selectedLens = selectedLens,
                    onSelectLens = onSelectLens
                )
            }

            // 5. Live Voice Narrator Deck (TtsManager)
            item {
                LiveVoiceNarratorDeck(
                    narratorState = narratorState,
                    autoNarrateEnabled = autoNarrateEnabled,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipBackward = onSkipBackward,
                    onSkipForward = onSkipForward,
                    onChangeSpeed = onChangeSpeed,
                    onToggleAutoNarrate = onToggleAutoNarrate,
                    onCycleVoicePersona = onCycleVoicePersona
                )
            }

            // 6. Active Episode Documentary Content or Generation Status
            item {
                when (episodeState) {
                    is EpisodeGenerationState.Generating -> {
                        GeneratingDocumentaryCard(
                            blockName = episodeState.blockName,
                            lensLabel = episodeState.lensLabel
                        )
                    }

                    is EpisodeGenerationState.Error -> {
                        EpisodeErrorCard(
                            errorMessage = episodeState.message,
                            onRetry = onBroadcastCurrentBlock
                        )
                    }

                    is EpisodeGenerationState.Success -> {
                        EpisodeDocumentaryCard(
                            episode = episodeState.episode,
                            onToggleBookmark = { onToggleBookmark(episodeState.episode) }
                        )
                    }

                    is EpisodeGenerationState.Idle -> {
                        if (activeEpisode != null) {
                            EpisodeDocumentaryCard(
                                episode = activeEpisode,
                                onToggleBookmark = { onToggleBookmark(activeEpisode) }
                            )
                        } else {
                            ReadyToBroadcastOnboardingCard(
                                hasLocationFix = locationState.hasLocationFix,
                                blockLabel = locationState.streetBlockLabel,
                                neighborhoodLabel = locationState.neighborhoodName,
                                onStartBroadcast = onBroadcastCurrentBlock
                            )
                        }
                    }
                }
            }

            // 7. Interactive Studio Tools: Route Planner, Facade Scanner, Trivia, Host Q&A
            item {
                WalkingRoutePlannerCard(
                    routeState = walkingRouteState,
                    onGenerateWalkingRoute = onGenerateWalkingRoute
                )
            }

            item {
                ArchitectureVisionScannerCard(
                    visionState = visionScannerState,
                    onPickArchitecturePhoto = onPickArchitecturePhoto
                )
            }

            item {
                UrbanTriviaChallengeCard(
                    triviaState = urbanTriviaState,
                    streetBlockLabel = locationState.streetBlockLabel,
                    onGenerateTrivia = onGenerateUrbanTrivia,
                    onSubmitAnswer = onSubmitTriviaAnswer
                )
            }

            item {
                LiveFieldQaCard(
                    questionInput = followUpQuestion,
                    onQuestionChange = { followUpQuestion = it },
                    qaState = liveHostQaState,
                    onSubmitQuestion = { q ->
                        onAskHostQuestion(q)
                        followUpQuestion = ""
                    }
                )
            }

            // 8. Field Broadcast Console: Background Walk Service, Heading Buffer, Quota & Battery
            item {
                val remaining = (locationState.triggerThresholdMeters - locationState.distanceFromLastBroadcastMeters).coerceAtLeast(0)
                ForegroundLocationServiceCard(
                    isForegroundServiceActive = locationState.isForegroundServiceActive,
                    streetBlockLabel = locationState.streetBlockLabel,
                    remainingMetersToTrigger = remaining,
                    onToggleForegroundService = onToggleForegroundService
                )
            }

            item {
                LookaheadHeadingBufferCard(
                    locationState = locationState,
                    lookaheadState = lookaheadState,
                    onPrefetchNextBlock = onPrefetchNextBlock,
                    onPlayPrefetchedEpisode = onPlayPrefetchedEpisode
                )
            }

            item {
                SmartQuotaAndSpatialCacheCard(
                    quotaStats = quotaStats,
                    onSelectTier = onSelectUsageTier,
                    onToggleSpatialCache = onToggleSpatialCache,
                    onChangeDailyBudget = onChangeDailyBudget,
                    onSaveByokKey = onSaveByokKey,
                    onClearByokKey = onClearByokKey
                )
            }

            item {
                BatteryPowerSaverCard(
                    batteryProfile = batteryPowerProfile,
                    onSelectOverrideMode = onSelectBatteryOverrideMode,
                    onSimulateLowBattery = onSimulateLowBattery
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GpsPermissionPromptCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gps_permission_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.cd_gps_receiver),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("grant_gps_permission_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CompassCalibration,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.grant_location_btn))
            }
        }
    }
}

@Composable
private fun GeneratingDocumentaryCard(
    blockName: String,
    lensLabel: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("generating_episode_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.generating_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = blockName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.generating_lens_meta, lensLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EpisodeErrorCard(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode_error_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.error_signal_interrupted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("retry_broadcast_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.retry_broadcast_btn))
            }
        }
    }
}

@Composable
private fun ReadyToBroadcastOnboardingCard(
    hasLocationFix: Boolean,
    blockLabel: String,
    neighborhoodLabel: String,
    onStartBroadcast: () -> Unit
) {
    val displayBlock = blockLabel.ifBlank { stringResource(R.string.waiting_for_gps_block) }
    val displayNeighborhood = neighborhoodLabel.ifBlank { stringResource(R.string.waiting_for_gps_neighborhood) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_broadcast_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (hasLocationFix) {
                        stringResource(R.string.onboarding_ready_badge)
                    } else {
                        stringResource(R.string.onboarding_waiting_badge)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = displayBlock,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$displayNeighborhood • ${stringResource(R.string.splash_subtitle)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartBroadcast,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_first_episode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_generate_btn))
            }
        }
    }
}

@Composable
fun EpisodeDocumentaryCard(
    episode: EpisodeEntity,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("episode_documentary_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Top Metadata Row
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
                        text = "${episode.stationCallSign} • ${episode.durationSeconds}S",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("bookmark_episode_button")
                ) {
                    Icon(
                        imageVector = if (episode.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (episode.isBookmarked) {
                            stringResource(R.string.cd_bookmark_remove)
                        } else {
                            stringResource(R.string.cd_bookmark_add)
                        },
                        tint = if (episode.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Episode Headline (Fraunces Display Serif)
            Text(
                text = episode.title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${episode.locationLabel} • ${episode.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Opening Radio Hook Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "“${episode.hookLine}”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 60-Second Radio Narrative Transcript
            Text(
                text = stringResource(R.string.transcript_section_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = episode.narrativeScript,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 3 Verified Block Field Notes
            if (episode.fieldFacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.field_notes_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                episode.fieldFacts.forEachIndexed { index, fact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = fact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Explorer Visual Cue
            if (episode.explorerPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.look_around_cue_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = episode.explorerPrompt,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFieldQaCard(
    questionInput: String,
    onQuestionChange: (String) -> Unit,
    qaState: LiveHostQaState,
    onSubmitQuestion: (String) -> Unit
) {
    val quickPrompts = listOf(
        stringResource(R.string.qa_quick_prompt_1),
        stringResource(R.string.qa_quick_prompt_2),
        stringResource(R.string.qa_quick_prompt_3)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_field_qa_card"),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.live_qa_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.live_qa_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, prompt ->
                    AssistChip(
                        onClick = { onSubmitQuestion(prompt) },
                        label = {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("quick_qa_chip_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionInput,
                    onValueChange = onQuestionChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.ask_host_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ask_host_input")
                )

                Button(
                    onClick = {
                        if (questionInput.isNotBlank()) {
                            onSubmitQuestion(questionInput)
                        }
                    },
                    enabled = questionInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("ask_host_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_question)
                    )
                }
            }

            // Streaming / Completed Answer Display
            AnimatedVisibility(visible = qaState !is LiveHostQaState.Idle) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            when (qaState) {
                                is LiveHostQaState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = stringResource(R.string.qa_loading_format, qaState.question),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                is LiveHostQaState.Streaming -> {
                                    Text(
                                        text = stringResource(R.string.qa_streaming_header),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = qaState.partialReply,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                is LiveHostQaState.Success -> {
                                    Text(
                                        text = stringResource(R.string.qa_dispatch_header, qaState.question),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = qaState.reply,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                is LiveHostQaState.Error -> {
                                    Text(
                                        text = qaState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                is LiveHostQaState.Idle -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
