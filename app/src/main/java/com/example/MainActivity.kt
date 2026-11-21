package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.ui.components.MiniOnAirTransportBar
import com.example.ui.components.SmartAutoPopupDialog
import com.example.ui.screens.FieldTapesArchiveScreen
import com.example.ui.screens.GeofenceRadarScreen
import com.example.ui.screens.OnAirStudioScreen
import com.example.ui.screens.TourSummaryScreen
import com.example.ui.screens.WalkRewardsCollectionScreen
import com.example.ui.theme.WanderCastTheme
import com.example.util.AppLocaleHelper
import com.example.util.LanguagePickerDialog
import com.example.viewmodel.WanderCastViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_ON_AIR = "on_air_studio"
const val ROUTE_RADAR = "geofence_radar"
const val ROUTE_TOUR_SUMMARY = "tour_summary"
const val ROUTE_REWARDS = "walk_rewards"
const val ROUTE_ARCHIVE = "field_tapes"

enum class WanderCastDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    ON_AIR(
        route = ROUTE_ON_AIR,
        labelRes = R.string.nav_on_air,
        selectedIcon = Icons.Filled.CellTower,
        unselectedIcon = Icons.Outlined.CellTower,
        testTag = "nav_tab_on_air"
    ),
    RADAR(
        route = ROUTE_RADAR,
        labelRes = R.string.nav_radar,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_tab_radar"
    ),
    TOUR_SUMMARY(
        route = ROUTE_TOUR_SUMMARY,
        labelRes = R.string.nav_tour_summary,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_tab_tour_summary"
    ),
    REWARDS(
        route = ROUTE_REWARDS,
        labelRes = R.string.nav_rewards,
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        testTag = "nav_tab_rewards"
    ),
    ARCHIVE(
        route = ROUTE_ARCHIVE,
        labelRes = R.string.nav_archive,
        selectedIcon = Icons.Filled.Album,
        unselectedIcon = Icons.Outlined.Album,
        testTag = "nav_tab_archive"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge and initialize UI
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }

            SideEffect {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            WanderCastTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WanderCastApp(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

@Composable
fun WanderCastApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: WanderCastViewModel = viewModel()
) {
    val baseContext = LocalContext.current
    val selectedLanguageTag by viewModel.selectedLanguageTag.collectAsStateWithLifecycle()
    val localized = remember(baseContext, selectedLanguageTag) {
        AppLocaleHelper.createLocalizedContext(baseContext, selectedLanguageTag)
    }

    var currentDestination by rememberSaveable { mutableStateOf(WanderCastDestination.ON_AIR) }
    var showSplashOverlay by rememberSaveable { mutableStateOf(true) }
    var showLanguagePickerDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1250L)
        showSplashOverlay = false
    }

    var hasLocationPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    baseContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var pendingForegroundServiceStart by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            viewModel.startLiveGpsTracking()
            if (pendingForegroundServiceStart) {
                pendingForegroundServiceStart = false
                viewModel.toggleForegroundWalkService(true)
            }
        } else {
            pendingForegroundServiceStart = false
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startLiveGpsTracking()
        }
    }

    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val selectedLens by viewModel.selectedLens.collectAsStateWithLifecycle()
    val quotaStats by viewModel.quotaStats.collectAsStateWithLifecycle()
    val episodeState by viewModel.episodeState.collectAsStateWithLifecycle()
    val activeEpisode by viewModel.activeEpisode.collectAsStateWithLifecycle()
    val narratorState by viewModel.narratorState.collectAsStateWithLifecycle()
    val autoTriggerEnabled by viewModel.autoTriggerOnGeofence.collectAsStateWithLifecycle()
    val autoNarrateEnabled by viewModel.autoNarrateVoice.collectAsStateWithLifecycle()
    val liveHostQaState by viewModel.liveHostQaState.collectAsStateWithLifecycle()
    val visionScannerState by viewModel.visionScannerState.collectAsStateWithLifecycle()
    val walkingRouteState by viewModel.walkingRouteState.collectAsStateWithLifecycle()
    val lookaheadState by viewModel.lookaheadPrefetchState.collectAsStateWithLifecycle()
    val urbanTriviaState by viewModel.urbanTriviaState.collectAsStateWithLifecycle()
    val visitedHistoricalPointIds by viewModel.visitedHistoricalPointIds.collectAsStateWithLifecycle()
    val savedEpisodes by viewModel.savedEpisodes.collectAsStateWithLifecycle()
    val completedWalkRoutes by viewModel.completedWalkRoutes.collectAsStateWithLifecycle()
    val activeSessionWaypoints by viewModel.activeSessionWaypoints.collectAsStateWithLifecycle()
    val walkBadges by viewModel.walkBadges.collectAsStateWithLifecycle()
    val newlyUnlockedBadgeId by viewModel.newlyUnlockedBadgeId.collectAsStateWithLifecycle()
    val activeSmartPopup by viewModel.activeSmartPopup.collectAsStateWithLifecycle()
    val engagementTelemetryState by viewModel.engagementTelemetryState.collectAsStateWithLifecycle()
    val recentAppEvents by viewModel.recentAppEvents.collectAsStateWithLifecycle()
    val batteryPowerProfile by viewModel.batteryPowerProfileState.collectAsStateWithLifecycle()

    LaunchedEffect(currentDestination) {
        viewModel.logScreenView(currentDestination.route)
    }

    val unlockedBadgesCount = remember(walkBadges) {
        walkBadges.count { it.isUnlocked }
    }

    val ioScope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ioScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        baseContext.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, boundsOpts)
                        }
                        var sample = 1
                        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                        while (maxSide / sample > 1280) {
                            sample *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        baseContext.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOpts)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error decoding selected photo", e)
                        null
                    }
                }
                if (bitmap != null) {
                    viewModel.analyzeArchitectureOrPlaquePhoto(bitmap)
                }
            }
        }
    }

    val requestPermissionsAction = {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    val toggleForegroundServiceAction: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (hasLocationPermission) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        baseContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingForegroundServiceStart = true
                    requestPermissionsAction()
                } else {
                    viewModel.toggleForegroundWalkService(true)
                }
            } else {
                pendingForegroundServiceStart = true
                requestPermissionsAction()
            }
        } else {
            pendingForegroundServiceStart = false
            viewModel.toggleForegroundWalkService(false)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localized.context,
        LocalConfiguration provides localized.configuration
    ) {
        if (showLanguagePickerDialog) {
            LanguagePickerDialog(
                selectedLanguageTag = selectedLanguageTag,
                onSelectLanguage = { tag ->
                    viewModel.setAppLanguageTag(tag)
                },
                onDismiss = { showLanguagePickerDialog = false }
            )
        }

        val currentPopupProposal = activeSmartPopup
        if (currentPopupProposal != null) {
            SmartAutoPopupDialog(
                proposal = currentPopupProposal,
                onAcceptPrimaryAction = { customPayload ->
                    viewModel.acceptSmartPopup(customPayload)
                },
                onSnoozeWithBackoff = {
                    viewModel.snoozeSmartPopup()
                },
                onOptOutForever = {
                    viewModel.optOutSmartPopupForever()
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 720.dp
                val totalArchiveItems = savedEpisodes.size + completedWalkRoutes.size

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        Column {
                            // High-Value Persistent Media Transport Bar on secondary screens
                            if (currentDestination != WanderCastDestination.ON_AIR && activeEpisode != null) {
                                MiniOnAirTransportBar(
                                    activeEpisode = activeEpisode,
                                    narratorState = narratorState,
                                    onTogglePlayPause = { viewModel.toggleNarrationPlayPause() },
                                    onSkipForward = { viewModel.skipForwardSentence() },
                                    onOpenStudio = { currentDestination = WanderCastDestination.ON_AIR }
                                )
                            }
                            if (!useNavigationRail) {
                                NavigationBar {
                                    WanderCastDestination.entries.forEach { dest ->
                                        val selected = currentDestination == dest
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentDestination = dest },
                                            icon = {
                                                when {
                                                    dest == WanderCastDestination.REWARDS && unlockedBadgesCount > 0 -> {
                                                        BadgedBox(
                                                            badge = {
                                                                Badge { Text("$unlockedBadgesCount") }
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                                contentDescription = stringResource(dest.labelRes)
                                                            )
                                                        }
                                                    }
                                                    dest == WanderCastDestination.ARCHIVE && totalArchiveItems > 0 -> {
                                                        BadgedBox(
                                                            badge = {
                                                                Badge { Text("$totalArchiveItems") }
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                                contentDescription = stringResource(dest.labelRes)
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        Icon(
                                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                            contentDescription = stringResource(dest.labelRes)
                                                        )
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(dest.labelRes),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.testTag(dest.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail {
                                WanderCastDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) +
                                    scaleIn(
                                        initialScale = 0.985f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )) togetherWith fadeOut(animationSpec = tween(160))
                            },
                            label = "destination_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { destination ->
                            when (destination) {
                                WanderCastDestination.ON_AIR -> {
                                    OnAirStudioScreen(
                                        locationState = locationState,
                                        selectedLens = selectedLens,
                                        quotaStats = quotaStats,
                                        episodeState = episodeState,
                                        activeEpisode = activeEpisode,
                                        narratorState = narratorState,
                                        autoNarrateEnabled = autoNarrateEnabled,
                                        liveHostQaState = liveHostQaState,
                                        visionScannerState = visionScannerState,
                                        walkingRouteState = walkingRouteState,
                                        lookaheadState = lookaheadState,
                                        urbanTriviaState = urbanTriviaState,
                                        hasLocationPermission = hasLocationPermission,
                                        isDarkTheme = isDarkTheme,
                                        onRequestLocationPermission = requestPermissionsAction,
                                        onToggleForegroundService = toggleForegroundServiceAction,
                                        onToggleTheme = onToggleTheme,
                                        onSelectLens = { viewModel.selectDocumentaryLens(it) },
                                        onSelectUsageTier = { viewModel.setAiUsageTier(it) },
                                        onToggleSpatialCache = { viewModel.setSpatialCacheEnabled(it) },
                                        onChangeDailyBudget = { viewModel.setDailyApiBudget(it) },
                                        onSaveByokKey = { viewModel.saveUserByokGeminiKey(it) },
                                        onClearByokKey = { viewModel.clearUserByokGeminiKey() },
                                        onPrefetchNextBlock = { viewModel.prefetchNextBlockAlongHeading() },
                                        onPlayPrefetchedEpisode = { viewModel.playEpisodeNarration(it) },
                                        onBroadcastCurrentBlock = { viewModel.triggerDocumentaryForCurrentBlock() },
                                        onWalkOneBlockForward = { viewModel.stepWalkDirection(0.00086, 0.00035) },
                                        onTogglePlayPause = { viewModel.toggleNarrationPlayPause() },
                                        onSkipBackward = { viewModel.skipBackwardSentence() },
                                        onSkipForward = { viewModel.skipForwardSentence() },
                                        onChangeSpeed = { viewModel.setNarrationSpeed(it) },
                                        onToggleAutoNarrate = { viewModel.setAutoNarrateVoice(it) },
                                        onCycleVoicePersona = { viewModel.cycleNarratorVoicePersona() },
                                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                                        onAskHostQuestion = { viewModel.askRadioHostQuestion(it) },
                                        onPickArchitecturePhoto = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onGenerateWalkingRoute = { viewModel.generateWalkingTourRoute() },
                                        onGenerateUrbanTrivia = { viewModel.generateNeighborhoodTriviaChallenge() },
                                        onSubmitTriviaAnswer = { viewModel.submitTriviaAnswer(it) },
                                        currentLanguageTag = selectedLanguageTag,
                                        onOpenLanguagePicker = { showLanguagePickerDialog = true },
                                        batteryPowerProfile = batteryPowerProfile,
                                        onSelectBatteryOverrideMode = {
                                            viewModel.setBatteryPowerOverrideMode(it)
                                        },
                                        onSimulateLowBattery = {
                                            viewModel.setSimulatedBatteryLevel(it)
                                        }
                                    )
                                }

                                WanderCastDestination.RADAR -> {
                                    GeofenceRadarScreen(
                                        locationState = locationState,
                                        savedEpisodes = savedEpisodes,
                                        autoTriggerEnabled = autoTriggerEnabled,
                                        hasLocationPermission = hasLocationPermission,
                                        onToggleAutoTrigger = { viewModel.setAutoTriggerOnGeofence(it) },
                                        onChangeGeofenceRadius = { viewModel.setGeofenceRadiusMeters(it) },
                                        onStepDirection = { dLat, dLng -> viewModel.stepWalkDirection(dLat, dLng) },
                                        onTuneCustomNeighborhood = { name, lat, lng ->
                                            viewModel.tuneToCustomNeighborhood(name, lat, lng)
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        onStartLiveGps = { viewModel.startLiveGpsTracking() },
                                        onReplayNearbyEpisode = { episode ->
                                            viewModel.playEpisodeNarration(episode)
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        onNavigateBack = {
                                            currentDestination = WanderCastDestination.ON_AIR
                                        }
                                    )
                                }

                                WanderCastDestination.TOUR_SUMMARY -> {
                                    TourSummaryScreen(
                                        completedWalkRoutes = completedWalkRoutes,
                                        savedEpisodes = savedEpisodes,
                                        activeSessionWaypoints = activeSessionWaypoints,
                                        currentLocationState = locationState,
                                        activeEpisodeId = activeEpisode?.id,
                                        visitedHistoricalPointIds = visitedHistoricalPointIds,
                                        onPlayEpisode = { episode ->
                                            viewModel.playEpisodeNarration(episode)
                                        },
                                        onToggleEpisodeBookmark = { episode ->
                                            viewModel.toggleBookmark(episode)
                                        },
                                        onWalkToHistoricalPointGeofence = { pointEp, narrate ->
                                            viewModel.walkToHistoricalPointGeofence(pointEp, narrate)
                                        },
                                        onResetGeofenceProgress = {
                                            viewModel.resetTourGeofenceProgress()
                                        },
                                        onLogCurrentWalkSession = {
                                            viewModel.logCurrentWalkSessionToHistory()
                                        },
                                        onNavigateBack = {
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        unlockedBadgesCount = unlockedBadgesCount,
                                        totalBadgesCount = walkBadges.size.coerceAtLeast(8),
                                        onOpenRewards = {
                                            currentDestination = WanderCastDestination.REWARDS
                                        },
                                        batteryPowerProfile = batteryPowerProfile,
                                        onSelectBatteryOverrideMode = {
                                            viewModel.setBatteryPowerOverrideMode(it)
                                        },
                                        onSimulateLowBattery = {
                                            viewModel.setSimulatedBatteryLevel(it)
                                        }
                                    )
                                }

                                WanderCastDestination.REWARDS -> {
                                    WalkRewardsCollectionScreen(
                                        walkBadges = walkBadges,
                                        completedWalkRoutes = completedWalkRoutes,
                                        newlyUnlockedBadgeId = newlyUnlockedBadgeId,
                                        onDismissNewlyUnlockedBanner = {
                                            viewModel.dismissNewlyUnlockedBadgeBanner()
                                        },
                                        onSimulateCompleteBadgeMilestone = { badgeId, titleText, descText ->
                                            viewModel.simulateCompleteMilestoneForBadge(
                                                badgeId = badgeId,
                                                badgeTitleForSpeech = titleText,
                                                badgeDescriptionForSpeech = descText
                                            )
                                        },
                                        onNarrateBadgeAchievement = { titleText, descText, tourTitle, xp ->
                                            viewModel.narrateUnlockedBadgeStory(
                                                badgeTitle = titleText,
                                                badgeDescription = descText,
                                                unlockedTourTitle = tourTitle,
                                                xpReward = xp
                                            )
                                        },
                                        onOpenTourSummary = {
                                            currentDestination = WanderCastDestination.TOUR_SUMMARY
                                        },
                                        onNavigateBack = {
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        engagementTelemetryState = engagementTelemetryState,
                                        recentAppEvents = recentAppEvents,
                                        onSimulatePeakDelightAutoPopup = {
                                            viewModel.simulatePeakDelightAutoPopup()
                                        },
                                        onPreviewSmartPopup = { promptType ->
                                            viewModel.previewSmartPopup(promptType)
                                        },
                                        onResetSmartPromptCooldowns = {
                                            viewModel.resetSmartPromptCooldowns()
                                        },
                                        onClearLoggedAppEvents = {
                                            viewModel.clearLoggedAppEvents()
                                        }
                                    )
                                }

                                WanderCastDestination.ARCHIVE -> {
                                    FieldTapesArchiveScreen(
                                        episodes = savedEpisodes,
                                        completedWalkRoutes = completedWalkRoutes,
                                        activeEpisodeId = activeEpisode?.id,
                                        onReplayEpisode = { episode ->
                                            viewModel.playEpisodeNarration(episode)
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                                        onDeleteEpisode = { viewModel.deleteEpisode(it) },
                                        onLogCurrentWalkRoute = {
                                            viewModel.logCurrentWalkSessionToHistory()
                                        },
                                        onToggleRouteFavorite = { route ->
                                            viewModel.toggleWalkRouteFavorite(route)
                                        },
                                        onDeleteWalkRoute = { routeId ->
                                            viewModel.deleteWalkRoute(routeId)
                                        },
                                        onGoToStudioAndBroadcast = {
                                            currentDestination = WanderCastDestination.ON_AIR
                                            viewModel.triggerDocumentaryForCurrentBlock()
                                        },
                                        onNavigateBack = {
                                            currentDestination = WanderCastDestination.ON_AIR
                                        },
                                        unlockedBadgesCount = unlockedBadgesCount,
                                        totalBadgesCount = walkBadges.size.coerceAtLeast(8),
                                        onOpenRewards = {
                                            currentDestination = WanderCastDestination.REWARDS
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Animated Editorial Broadcast Splash Overlay
            AnimatedVisibility(
                visible = showSplashOverlay,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(380))
            ) {
                BroadcastSplashOverlay(
                    onDismiss = { showSplashOverlay = false }
                )
            }
        }
    }
}

@Composable
private fun BroadcastSplashOverlay(
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_wave_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onDismiss() }
            .testTag("broadcast_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxR = size.minDimension / 2f
                    drawCircle(
                        color = primaryColor.copy(alpha = (1f - pulseRadius) * 0.55f),
                        radius = maxR * pulseRadius,
                        style = Stroke(width = 4.5f)
                    )
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.42f),
                        radius = maxR * 0.74f,
                        style = Stroke(width = 2.5f)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(96.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_fg_1795177312795),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = stringResource(R.string.splash_station_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("splash_tune_in_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.CellTower,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.splash_tune_in_btn))
            }
        }
    }
}
