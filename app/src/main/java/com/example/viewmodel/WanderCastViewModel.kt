package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.audio.NarratorState
import com.example.audio.TtsManager
import com.example.audio.TtsVoicePersona
import com.example.data.AppEventEntity
import com.example.data.DocumentaryLens
import com.example.data.EpisodeEntity
import com.example.data.EpisodeRepository
import com.example.data.RouteWaypoint
import com.example.data.WalkBadgeEntity
import com.example.data.WalkRouteEntity
import com.example.data.WalkRouteWithNarratives
import com.example.data.WanderCastDatabase
import com.example.location.BatteryPowerProfileState
import com.example.location.BatteryPowerSavingOverride
import com.example.location.LocationBlockState
import com.example.location.LocationGeofenceManager
import com.example.location.LocationService
import com.example.util.AppLocaleHelper
import com.example.util.SUPPORTED_20_LANGUAGES
import com.example.util.SmartAutoPopupProposal
import com.example.util.SmartEngagementEngine
import com.example.util.SmartEngagementTelemetryState
import com.example.util.SmartEventType
import com.example.util.SmartPromptType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "WanderCastViewModel"
private const val PREFS_NAME = "wandercast_smart_ai_prefs"
private const val KEY_BYOK_GEMINI = "user_byok_gemini_api_key"
private const val KEY_APP_LOCALE_TAG = "wandercast_app_locale_tag"
private const val KEY_DAILY_API_DATE = "wandercast_daily_api_date"
private const val KEY_DAILY_API_USED = "wandercast_daily_api_used"
private const val KEY_DAILY_API_BUDGET = "wandercast_daily_api_budget"
private const val KEY_CACHE_HITS_SAVED = "wandercast_cache_hits_saved"
private const val KEY_SPATIAL_CACHE_ENABLED = "wandercast_spatial_cache_enabled"
private const val KEY_SELECTED_AI_TIER = "wandercast_selected_ai_tier"

enum class AiUsageTier(
    @StringRes val titleRes: Int,
    val modelId: String,
    @StringRes val badgeRes: Int,
    @StringRes val descriptionRes: Int
) {
    ECO_FLASH_LITE(
        titleRes = R.string.ai_tier_eco_title,
        modelId = "gemini-3.5-flash-lite",
        badgeRes = R.string.ai_tier_eco_badge,
        descriptionRes = R.string.ai_tier_eco_desc
    ),
    STUDIO_FLASH(
        titleRes = R.string.ai_tier_studio_title,
        modelId = "gemini-3.8-flash",
        badgeRes = R.string.ai_tier_studio_badge,
        descriptionRes = R.string.ai_tier_studio_desc
    ),
    BYOK_USER_KEY(
        titleRes = R.string.ai_tier_byok_title,
        modelId = "gemini-2.5-flash (BYOK)",
        badgeRes = R.string.ai_tier_byok_badge,
        descriptionRes = R.string.ai_tier_byok_desc
    ),
    CACHE_FIRST_SAVER(
        titleRes = R.string.ai_tier_cache_title,
        modelId = "local-cache",
        badgeRes = R.string.ai_tier_cache_badge,
        descriptionRes = R.string.ai_tier_cache_desc
    )
}

data class QuotaAndCacheStats(
    val selectedTier: AiUsageTier = AiUsageTier.STUDIO_FLASH,
    val spatialCacheEnabled: Boolean = true,
    val spatialCacheRadiusMeters: Int = 65,
    val dailyApiCallsUsed: Int = 0,
    val dailyApiCallBudget: Int = 20,
    val cacheHitsSavedCount: Int = 0,
    val gracefulFallbacksCount: Int = 0,
    val hasUserByokKey: Boolean = false,
    val maskedByokKey: String = "",
    val lastDispatchFromCache: Boolean = false
)

data class WalkingTourStop(
    val stopNumber: Int,
    val name: String,
    val streetIntersection: String,
    val walkingMinutes: Int,
    val storyTeaser: String,
    val architecturalHighlight: String
)

data class WalkingTourItinerary(
    val tourTitle: String,
    val neighborhood: String,
    val totalMinutes: Int,
    val totalDistanceLabel: String,
    val stops: List<WalkingTourStop>,
    val googleMapsWalkingUrl: String
)

data class UrbanTriviaChallenge(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val radioRevealExplanation: String,
    val sourceLabel: String
)

sealed interface UrbanTriviaState {
    data object Idle : UrbanTriviaState
    data object Generating : UrbanTriviaState
    data class ActiveQuestion(
        val challenge: UrbanTriviaChallenge,
        val selectedOptionIndex: Int? = null,
        val score: Int = 0,
        val streak: Int = 0
    ) : UrbanTriviaState
    data class Error(val message: String) : UrbanTriviaState
}

sealed interface EpisodeGenerationState {
    data object Idle : EpisodeGenerationState
    data class Generating(val blockName: String, val lensLabel: String, val modelId: String) : EpisodeGenerationState
    data class Success(val episode: EpisodeEntity, val fromSpatialCache: Boolean = false) : EpisodeGenerationState
    data class Error(val message: String) : EpisodeGenerationState
}

sealed interface LiveHostQaState {
    data object Idle : LiveHostQaState
    data class Loading(val question: String) : LiveHostQaState
    data class Streaming(val question: String, val partialReply: String) : LiveHostQaState
    data class Success(val question: String, val reply: String) : LiveHostQaState
    data class Error(val message: String) : LiveHostQaState
}

sealed interface VisionScannerState {
    data object Idle : VisionScannerState
    data object Analyzing : VisionScannerState
    data class Success(
        val landmarkTitle: String,
        val architecturalStyle: String,
        val radioCommentary: String,
        val eraEstimate: String
    ) : VisionScannerState
    data class Error(val message: String) : VisionScannerState
}

sealed interface WalkingRouteState {
    data object Idle : WalkingRouteState
    data object Generating : WalkingRouteState
    data class Success(val itinerary: WalkingTourItinerary) : WalkingRouteState
    data class Error(val message: String) : WalkingRouteState
}

sealed interface LookaheadPrefetchState {
    data object Idle : LookaheadPrefetchState
    data class Prefetching(val targetBlockHint: String, val bearingCardinal: String) : LookaheadPrefetchState
    data class WarmedInCache(val episode: EpisodeEntity, val targetBlockHint: String) : LookaheadPrefetchState
    data class Error(val message: String) : LookaheadPrefetchState
}

class WanderCastViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = WanderCastDatabase.getInstance(application)
    private val repository = EpisodeRepository(database.episodeDao())
    val ttsManager = TtsManager(application)

    private val _selectedLanguageTag = MutableStateFlow(
        prefs.getString(KEY_APP_LOCALE_TAG, "en") ?: "en"
    )
    val selectedLanguageTag: StateFlow<String> = _selectedLanguageTag.asStateFlow()

    private val _selectedLens = MutableStateFlow(DocumentaryLens.ALL_ROUNDER)
    val selectedLens: StateFlow<DocumentaryLens> = _selectedLens.asStateFlow()

    private val _autoTriggerOnGeofence = MutableStateFlow(true)
    val autoTriggerOnGeofence: StateFlow<Boolean> = _autoTriggerOnGeofence.asStateFlow()

    private val _autoNarrateVoice = MutableStateFlow(true)
    val autoNarrateVoice: StateFlow<Boolean> = _autoNarrateVoice.asStateFlow()

    private val _quotaStats = MutableStateFlow(loadInitialQuotaStats())
    val quotaStats: StateFlow<QuotaAndCacheStats> = _quotaStats.asStateFlow()

    private val _episodeState = MutableStateFlow<EpisodeGenerationState>(EpisodeGenerationState.Idle)
    val episodeState: StateFlow<EpisodeGenerationState> = _episodeState.asStateFlow()

    private val _activeEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val activeEpisode: StateFlow<EpisodeEntity?> = _activeEpisode.asStateFlow()

    private val _liveHostQaState = MutableStateFlow<LiveHostQaState>(LiveHostQaState.Idle)
    val liveHostQaState: StateFlow<LiveHostQaState> = _liveHostQaState.asStateFlow()

    private val _visionScannerState = MutableStateFlow<VisionScannerState>(VisionScannerState.Idle)
    val visionScannerState: StateFlow<VisionScannerState> = _visionScannerState.asStateFlow()

    private val _walkingRouteState = MutableStateFlow<WalkingRouteState>(WalkingRouteState.Idle)
    val walkingRouteState: StateFlow<WalkingRouteState> = _walkingRouteState.asStateFlow()

    private val _lookaheadPrefetchState = MutableStateFlow<LookaheadPrefetchState>(LookaheadPrefetchState.Idle)
    val lookaheadPrefetchState: StateFlow<LookaheadPrefetchState> = _lookaheadPrefetchState.asStateFlow()

    private val _urbanTriviaState = MutableStateFlow<UrbanTriviaState>(UrbanTriviaState.Idle)
    val urbanTriviaState: StateFlow<UrbanTriviaState> = _urbanTriviaState.asStateFlow()
    private var triviaScore = 0
    private var triviaStreak = 0

    // Tracks historical point IDs that have been explicitly visited via GPS proximity or geofence trigger
    private val _visitedHistoricalPointIds = MutableStateFlow<Set<Long>>(emptySet())
    val visitedHistoricalPointIds: StateFlow<Set<Long>> = _visitedHistoricalPointIds.asStateFlow()
    private val _progressResetExplicitly = MutableStateFlow(false)

    // Tracks the latest unlocked digital badge id for celebration toast / banner on the rewards screen
    private val _newlyUnlockedBadgeId = MutableStateFlow<String?>(null)
    val newlyUnlockedBadgeId: StateFlow<String?> = _newlyUnlockedBadgeId.asStateFlow()

    val narratorState: StateFlow<NarratorState> = ttsManager.state

    val savedEpisodes: StateFlow<List<EpisodeEntity>> = repository.allEpisodes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val completedWalkRoutes: StateFlow<List<WalkRouteWithNarratives>> =
        repository.allWalkRoutesWithNarratives.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val walkBadges: StateFlow<List<WalkBadgeEntity>> =
        repository.allWalkBadges.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentAppEvents: StateFlow<List<AppEventEntity>> =
        repository.recentAppEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val engagementEngine = SmartEngagementEngine(prefs)

    private val _activeSmartPopup = MutableStateFlow<SmartAutoPopupProposal?>(null)
    val activeSmartPopup: StateFlow<SmartAutoPopupProposal?> = _activeSmartPopup.asStateFlow()

    private val _engagementTelemetryState = MutableStateFlow(engagementEngine.buildTelemetrySnapshot())
    val engagementTelemetryState: StateFlow<SmartEngagementTelemetryState> = _engagementTelemetryState.asStateFlow()

    private var lastLoggedScreenRoute: String = ""

    private var sessionStartTimestamp: Long = System.currentTimeMillis()
    private val sessionWaypoints = mutableListOf<RouteWaypoint>()
    private val _activeSessionWaypoints = MutableStateFlow<List<RouteWaypoint>>(emptyList())
    val activeSessionWaypoints: StateFlow<List<RouteWaypoint>> = _activeSessionWaypoints.asStateFlow()

    private val sessionEpisodeIds = mutableListOf<Long>()

    private val locationManager = LocationGeofenceManager(
        context = application,
        scope = viewModelScope,
        onGeofenceBlockEntered = { newBlockState ->
            recordWaypointInSession(
                lat = newBlockState.latitude,
                lng = newBlockState.longitude,
                label = newBlockState.streetBlockLabel
            )
            if (_autoTriggerOnGeofence.value) {
                generateBlockDocumentary(
                    blockState = newBlockState,
                    lens = _selectedLens.value,
                    forceBypassCache = false
                )
            }
        }
    )

    val locationState: StateFlow<LocationBlockState> = locationManager.locationState
    val batteryPowerProfileState: StateFlow<BatteryPowerProfileState> =
        locationManager.batteryPowerProfileState

    fun setBatteryPowerOverrideMode(mode: BatteryPowerSavingOverride) {
        locationManager.setBatteryPowerOverrideMode(mode)
    }

    fun setSimulatedBatteryLevel(simulatedPercentage: Int?) {
        locationManager.setSimulatedBatteryLevel(simulatedPercentage)
    }

    init {
        ttsManager.setLocale(AppLocaleHelper.parseLocale(_selectedLanguageTag.value))
        restoreLatestEpisodeAndInitBadges()
        observeGpsGeofenceVisitsForHistoricalPoints()
        observeWalkHistoryForBadgeRewards()
        // Log session start (Cold-Start Guard inside SmartEngagementEngine guarantees 0 popups at app start!)
        logAppEvent(
            eventType = SmartEventType.APP_SESSION_START,
            screenName = "live_broadcast",
            metadataSummary = "Session #${engagementEngine.getSessionNumber()} started"
        )
    }

    private fun localizedString(@StringRes resId: Int, vararg args: Any): String {
        val localizedCtx = AppLocaleHelper.createLocalizedContext(
            getApplication(),
            _selectedLanguageTag.value
        ).context
        return if (args.isEmpty()) localizedCtx.getString(resId) else localizedCtx.getString(resId, *args)
    }

    private fun currentLanguageNameForPrompt(): String {
        val tag = _selectedLanguageTag.value
        return SUPPORTED_20_LANGUAGES.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?.englishName ?: "English"
    }

    fun setAppLanguageTag(languageTag: String) {
        _selectedLanguageTag.value = languageTag
        prefs.edit().putString(KEY_APP_LOCALE_TAG, languageTag).apply()
        ttsManager.setLocale(com.example.util.AppLocaleHelper.parseLocale(languageTag))
        logAppEvent(
            eventType = SmartEventType.LANGUAGE_SWITCHED,
            screenName = lastLoggedScreenRoute.ifBlank { "live_broadcast" },
            metadataSummary = "App language switched to '$languageTag'"
        )
    }

    fun logScreenView(routeName: String) {
        if (routeName.isBlank() || routeName == lastLoggedScreenRoute) return
        lastLoggedScreenRoute = routeName
        logAppEvent(
            eventType = SmartEventType.SCREEN_VIEW,
            screenName = routeName,
            metadataSummary = "Navigated to $routeName screen"
        )
    }

    /**
     * Records a structured user/system event into the local Room `app_events` table and runs
     * the Peak-Delight & Anti-Fatigue Decision Algorithm to evaluate whether an auto-popup
     * suggestion (Rate App, Share App, Update App) should be presented.
     */
    fun logAppEvent(
        eventType: SmartEventType,
        screenName: String,
        metadataSummary: String,
        bypassColdStartGuardForSimulation: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val sessionNum = engagementEngine.getSessionNumber()
                repository.insertAppEvent(
                    AppEventEntity(
                        eventType = eventType.key,
                        eventCategory = eventType.category,
                        screenName = screenName,
                        metadataSummary = metadataSummary,
                        delightScoreDelta = eventType.defaultDelightDelta,
                        sessionNumber = sessionNum
                    )
                )

                val loc = locationState.value
                val unlockedCount = walkBadges.value.count { it.isUnlocked }
                val totalWalked = completedWalkRoutes.value.sumOf { it.route.totalDistanceMeters } +
                    loc.totalWalkedMeters
                val isBusy = _episodeState.value is EpisodeGenerationState.Generating ||
                    _walkingRouteState.value is WalkingRouteState.Generating ||
                    _visionScannerState.value is VisionScannerState.Analyzing

                val proposal = engagementEngine.recordEventAndEvaluate(
                    eventType = eventType,
                    metadataSummary = metadataSummary,
                    activeNeighborhood = loc.neighborhoodName,
                    unlockedBadgesCount = unlockedCount,
                    totalWalkedMeters = totalWalked,
                    isCurrentlyGeneratingOrBusy = isBusy,
                    bypassColdStartGuardForSimulation = bypassColdStartGuardForSimulation
                )
                _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()

                if (proposal != null && _activeSmartPopup.value == null) {
                    _activeSmartPopup.value = proposal
                    repository.insertAppEvent(
                        AppEventEntity(
                            eventType = SmartEventType.PROMPT_IMPRESSION.key,
                            eventCategory = SmartEventType.PROMPT_IMPRESSION.category,
                            screenName = screenName,
                            metadataSummary = "Auto-popup shown: ${proposal.promptType.id} (Delight ${proposal.delightScoreAtTrigger}/100)",
                            delightScoreDelta = 0,
                            sessionNumber = sessionNum
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log app event: ${e.message}")
            }
        }
    }

    fun acceptSmartPopup(customPayload: String? = null) {
        val current = _activeSmartPopup.value ?: return
        _activeSmartPopup.value = null
        engagementEngine.onPromptAccepted(current.promptType)
        _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()

        val appContext = getApplication<Application>()
        when (current.promptType) {
            SmartPromptType.SHARE_APP -> {
                val fallbackShare = localizedString(
                    R.string.smart_share_message_format,
                    current.activeNeighborhood.ifBlank { localizedString(R.string.smart_default_neighborhood) },
                    current.unlockedBadgesCount,
                    current.totalWalkedMeters
                )
                SmartEngagementEngine.launchShareAppIntent(
                    context = appContext,
                    shareMessage = customPayload?.takeIf { it.isNotBlank() } ?: fallbackShare
                )
            }
            SmartPromptType.RATE_APP -> {
                val isPositiveStoreRating = customPayload == null || customPayload.startsWith("STORE_RATING")
                if (isPositiveStoreRating) {
                    SmartEngagementEngine.launchStoreReviewOrUpdateIntent(appContext)
                }
            }
            SmartPromptType.APP_UPDATE -> {
                SmartEngagementEngine.launchStoreReviewOrUpdateIntent(appContext)
            }
        }

        viewModelScope.launch {
            repository.insertAppEvent(
                AppEventEntity(
                    eventType = SmartEventType.PROMPT_ACCEPTED.key,
                    eventCategory = SmartEventType.PROMPT_ACCEPTED.category,
                    screenName = lastLoggedScreenRoute.ifBlank { "live_broadcast" },
                    metadataSummary = "Accepted ${current.promptType.id} (${customPayload ?: "OK"})",
                    delightScoreDelta = SmartEventType.PROMPT_ACCEPTED.defaultDelightDelta,
                    sessionNumber = engagementEngine.getSessionNumber()
                )
            )
            _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()
        }
    }

    fun snoozeSmartPopup() {
        val current = _activeSmartPopup.value ?: return
        _activeSmartPopup.value = null
        engagementEngine.onPromptSnoozed(current.promptType)
        _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()
        viewModelScope.launch {
            repository.insertAppEvent(
                AppEventEntity(
                    eventType = SmartEventType.PROMPT_SNOOZED.key,
                    eventCategory = SmartEventType.PROMPT_SNOOZED.category,
                    screenName = lastLoggedScreenRoute.ifBlank { "live_broadcast" },
                    metadataSummary = "Snoozed ${current.promptType.id} with exponential backoff cooldown",
                    delightScoreDelta = 0,
                    sessionNumber = engagementEngine.getSessionNumber()
                )
            )
        }
    }

    fun optOutSmartPopupForever() {
        val current = _activeSmartPopup.value ?: return
        _activeSmartPopup.value = null
        engagementEngine.onPromptOptOutForever(current.promptType)
        _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()
        viewModelScope.launch {
            repository.insertAppEvent(
                AppEventEntity(
                    eventType = SmartEventType.PROMPT_OPT_OUT.key,
                    eventCategory = SmartEventType.PROMPT_OPT_OUT.category,
                    screenName = lastLoggedScreenRoute.ifBlank { "live_broadcast" },
                    metadataSummary = "Permanent opt-out recorded for ${current.promptType.id}",
                    delightScoreDelta = 0,
                    sessionNumber = engagementEngine.getSessionNumber()
                )
            )
        }
    }

    fun previewSmartPopup(promptType: SmartPromptType) {
        val loc = locationState.value
        val unlockedCount = walkBadges.value.count { it.isUnlocked }
        val totalWalked = completedWalkRoutes.value.sumOf { it.route.totalDistanceMeters } +
            loc.totalWalkedMeters
        val proposal = engagementEngine.buildManualPreviewProposal(
            promptType = promptType,
            activeNeighborhood = loc.neighborhoodName,
            unlockedBadgesCount = unlockedCount,
            totalWalkedMeters = totalWalked
        )
        _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()
        _activeSmartPopup.value = proposal
    }

    fun simulatePeakDelightAutoPopup() {
        logAppEvent(
            eventType = SmartEventType.BADGE_UNLOCKED,
            screenName = "walk_rewards",
            metadataSummary = "Simulated Peak Delight Milestone (+38 delight) • Evaluating Rate/Share/Update eligibility",
            bypassColdStartGuardForSimulation = true
        )
    }

    fun resetSmartPromptCooldowns() {
        engagementEngine.resetAllPromptCooldownsForTesting()
        _engagementTelemetryState.value = engagementEngine.buildTelemetrySnapshot()
    }

    fun clearLoggedAppEvents() {
        viewModelScope.launch {
            repository.clearAppEvents()
        }
    }

    fun dismissNewlyUnlockedBadgeBanner() {
        _newlyUnlockedBadgeId.value = null
    }

    private fun currentCalendarDayKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun persistQuotaStats(stats: QuotaAndCacheStats) {
        prefs.edit()
            .putString(KEY_DAILY_API_DATE, currentCalendarDayKey())
            .putInt(KEY_DAILY_API_USED, stats.dailyApiCallsUsed)
            .putInt(KEY_DAILY_API_BUDGET, stats.dailyApiCallBudget)
            .putInt(KEY_CACHE_HITS_SAVED, stats.cacheHitsSavedCount)
            .putBoolean(KEY_SPATIAL_CACHE_ENABLED, stats.spatialCacheEnabled)
            .putString(KEY_SELECTED_AI_TIER, stats.selectedTier.name)
            .apply()
    }

    private fun updateQuotaStats(transform: (QuotaAndCacheStats) -> QuotaAndCacheStats) {
        val updated = transform(_quotaStats.value)
        _quotaStats.value = updated
        persistQuotaStats(updated)
    }

    private fun loadInitialQuotaStats(): QuotaAndCacheStats {
        val savedKey = prefs.getString(KEY_BYOK_GEMINI, "")?.trim().orEmpty()
        val hasKey = savedKey.length >= 12
        val masked = if (hasKey) {
            "${savedKey.take(4)}••••${savedKey.takeLast(4)}"
        } else {
            ""
        }
        val today = currentCalendarDayKey()
        val savedDate = prefs.getString(KEY_DAILY_API_DATE, "") ?: ""
        val dailyUsed = if (savedDate == today) prefs.getInt(KEY_DAILY_API_USED, 0) else 0
        val dailyBudget = prefs.getInt(KEY_DAILY_API_BUDGET, 20).coerceIn(5, 100)
        val cacheHits = prefs.getInt(KEY_CACHE_HITS_SAVED, 0)
        val spatialEnabled = prefs.getBoolean(KEY_SPATIAL_CACHE_ENABLED, true)
        val savedTierName = prefs.getString(KEY_SELECTED_AI_TIER, null)
        val savedTier = savedTierName?.let { name ->
            AiUsageTier.entries.firstOrNull { it.name == name }
        }
        val resolvedTier = when {
            savedTier == AiUsageTier.BYOK_USER_KEY && !hasKey -> AiUsageTier.STUDIO_FLASH
            savedTier != null -> savedTier
            hasKey -> AiUsageTier.BYOK_USER_KEY
            else -> AiUsageTier.STUDIO_FLASH
        }
        return QuotaAndCacheStats(
            selectedTier = resolvedTier,
            spatialCacheEnabled = spatialEnabled,
            dailyApiCallsUsed = dailyUsed,
            dailyApiCallBudget = dailyBudget,
            cacheHitsSavedCount = cacheHits,
            hasUserByokKey = hasKey,
            maskedByokKey = masked
        )
    }

    /**
     * Automatically evaluates the user's live GPS coordinates and geofence trigger threshold
     * against all historical points in the Room database, marking points within the geofence
     * radius as visited.
     */
    private fun observeGpsGeofenceVisitsForHistoricalPoints() {
        viewModelScope.launch {
            combine(locationState, savedEpisodes, _progressResetExplicitly) { loc, episodes, isReset ->
                Triple(loc, episodes, isReset)
            }.collectLatest { (loc, episodes, isReset) ->
                if (episodes.isEmpty()) return@collectLatest
                val effectiveRadius = loc.triggerThresholdMeters.coerceAtLeast(45)
                val newlyTriggeredIds = episodes.filter { ep ->
                    val dist = LocationGeofenceManager.haversineMeters(
                        loc.latitude,
                        loc.longitude,
                        ep.latitude,
                        ep.longitude
                    )
                    dist <= effectiveRadius
                }.map { it.id }.toSet()

                if (!isReset && newlyTriggeredIds.isNotEmpty()) {
                    _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + newlyTriggeredIds
                }
            }
        }
    }

    /**
     * Moves the walker's GPS receiver / stepper position directly into the specified historical
     * point's 42m geofence zone, marking it visited on the Tour Summary progress bar and
     * optionally starting audio narration.
     */
    fun walkToHistoricalPointGeofence(point: EpisodeEntity, narrateOnArrival: Boolean = false) {
        _progressResetExplicitly.value = false
        _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + point.id
        recordWaypointInSession(
            lat = point.latitude,
            lng = point.longitude,
            label = point.title
        )
        locationManager.jumpToCustomLocation(
            customQueryLabel = point.locationLabel.substringBefore(" •").ifBlank { point.title },
            lat = point.latitude,
            lng = point.longitude,
            triggerEpisode = false
        )
        if (narrateOnArrival) {
            playEpisodeNarration(point)
        }
        evaluateAndUnlockWalkBadges(triggeringTourTitle = point.title)
    }

    /**
     * Resets the visited historical points check-ins on the Tour Summary screen so the user
     * can test walking through the tour checkpoints from 0%.
     */
    fun resetTourGeofenceProgress() {
        _progressResetExplicitly.value = true
        _visitedHistoricalPointIds.value = emptySet()
    }

    fun saveUserByokGeminiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        prefs.edit().putString(KEY_BYOK_GEMINI, cleaned).apply()
        val hasKey = cleaned.length >= 12
        val masked = if (hasKey) {
            "${cleaned.take(4)}••••${cleaned.takeLast(4)}"
        } else {
            ""
        }
        updateQuotaStats {
            it.copy(
                hasUserByokKey = hasKey,
                maskedByokKey = masked,
                selectedTier = if (hasKey) AiUsageTier.BYOK_USER_KEY else AiUsageTier.STUDIO_FLASH
            )
        }
    }

    fun clearUserByokGeminiKey() {
        prefs.edit().remove(KEY_BYOK_GEMINI).apply()
        updateQuotaStats {
            it.copy(
                hasUserByokKey = false,
                maskedByokKey = "",
                selectedTier = if (it.selectedTier == AiUsageTier.BYOK_USER_KEY) {
                    AiUsageTier.STUDIO_FLASH
                } else {
                    it.selectedTier
                }
            )
        }
    }

    private fun getSavedByokKey(): String? {
        val k = prefs.getString(KEY_BYOK_GEMINI, "")?.trim().orEmpty()
        return k.takeIf { it.length >= 12 }
    }

    // Structured JSON Schema for 60-second neighborhood radio documentary episodes
    private val documentaryEpisodeSchema = Schema.obj(
        properties = mapOf(
            "episodeTitle" to Schema.string(
                description = "Compelling NPR / 99% Invisible style radio episode title for this block"
            ),
            "stationCallSign" to Schema.string(
                description = "Short radio dispatch tag, e.g. 'WDR-99.1 • BLOCK DISPATCH'"
            ),
            "hookLine" to Schema.string(
                description = "A vivid 1-sentence radio opening hook that draws the walker's attention to their surroundings"
            ),
            "narrativeScript" to Schema.string(
                description = "A 60-second (~135 to 160 words) localized radio documentary narrative explaining neighborhood history, architectural details, bridges, or quirky local diner/street trivia with warm storytelling pacing"
            ),
            "fieldFacts" to Schema.array(
                items = Schema.string(),
                description = "3 specific, fascinating historical, architectural, or cultural facts about this neighborhood or street grid"
            ),
            "explorerPrompt" to Schema.string(
                description = "A concrete visual observation cue telling the urban explorer what detail to notice around them right now"
            ),
            "estimatedDurationSeconds" to Schema.integer(
                description = "Estimated spoken duration in seconds, typically between 55 and 70"
            )
        )
    )

    // Structured JSON Schema for 3-Stop Walking Tour Route Planner
    private val walkingRouteSchema = Schema.obj(
        properties = mapOf(
            "tourTitle" to Schema.string(description = "Evocative title for this 3-stop neighborhood walking route"),
            "totalMinutes" to Schema.integer(description = "Estimated total walking time in minutes (15 to 35)"),
            "totalDistanceLabel" to Schema.string(description = "Approximate walking distance, e.g. '1.1 km loop'"),
            "stops" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "stopNumber" to Schema.integer(description = "1, 2, or 3"),
                        "name" to Schema.string(description = "Landmark, corner, or architectural stop name"),
                        "streetIntersection" to Schema.string(description = "Exact street intersection or address in this neighborhood"),
                        "walkingMinutes" to Schema.integer(description = "Minutes to walk to this stop"),
                        "storyTeaser" to Schema.string(description = "1-sentence radio teaser about why this stop is fascinating"),
                        "architecturalHighlight" to Schema.string(description = "Specific detail to spot at this stop")
                    )
                ),
                description = "3 sequential walking tour stops in this neighborhood"
            )
        )
    )

    // Structured JSON Schema for Multimodal Architecture / Plaque Vision Scanner
    private val visionLandmarkSchema = Schema.obj(
        properties = mapOf(
            "landmarkTitle" to Schema.string(description = "Name or architectural descriptor of the photographed building, plaque, or street feature"),
            "architecturalStyle" to Schema.string(description = "Architectural style, material, or signage category"),
            "eraEstimate" to Schema.string(description = "Estimated historical era or decade"),
            "radioCommentary" to Schema.string(description = "A 45-second (~100 words) public-radio style audio commentary explaining the visual details, craftsmanship, or plaque inscription in the photo")
        )
    )

    // Structured JSON Schema for Urban Trivia Challenge
    private val triviaChallengeSchema = Schema.obj(
        properties = mapOf(
            "question" to Schema.string(description = "Engaging neighborhood history or architecture trivia question for this street block"),
            "options" to Schema.array(
                items = Schema.string(),
                description = "Exactly 3 plausible answer options"
            ),
            "correctIndex" to Schema.integer(description = "0, 1, or 2 indicating the correct option"),
            "radioRevealExplanation" to Schema.string(description = "2-sentence radio host explanation revealing the true historical story behind the answer")
        )
    )

    // Lazily initialized free-tier Gemini 3.8 Flash and 3.5 Flash-Lite models
    private val studioDocumentaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = documentaryEpisodeSchema
            }
        )
    }

    private val ecoDocumentaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = documentaryEpisodeSchema
            }
        )
    }

    private val walkingRouteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = walkingRouteSchema
            }
        )
    }

    private val visionLandmarkModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = visionLandmarkSchema
            }
        )
    }

    private val triviaChallengeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = triviaChallengeSchema
            }
        )
    }

    private val liveHostStreamingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val ecoHostStreamingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    fun startLiveGpsTracking() {
        locationManager.startLiveGpsUpdates()
    }

    fun stopLiveGpsTracking() {
        locationManager.stopLiveGpsUpdates()
    }

    fun startForegroundWalkService() {
        locationManager.startForegroundServiceTracking()
    }

    fun stopForegroundWalkService() {
        locationManager.stopForegroundServiceTracking()
    }

    fun toggleForegroundWalkService(enable: Boolean) {
        if (enable) {
            locationManager.startForegroundServiceTracking()
        } else {
            locationManager.stopForegroundServiceTracking()
        }
    }

    fun selectDocumentaryLens(lens: DocumentaryLens) {
        _selectedLens.value = lens
    }

    fun setAiUsageTier(tier: AiUsageTier) {
        updateQuotaStats { it.copy(selectedTier = tier) }
    }

    fun setSpatialCacheEnabled(enabled: Boolean) {
        updateQuotaStats { it.copy(spatialCacheEnabled = enabled) }
    }

    fun setDailyApiBudget(budget: Int) {
        updateQuotaStats { it.copy(dailyApiCallBudget = budget.coerceIn(5, 100)) }
    }

    fun setAutoTriggerOnGeofence(enabled: Boolean) {
        _autoTriggerOnGeofence.value = enabled
    }

    fun setAutoNarrateVoice(enabled: Boolean) {
        _autoNarrateVoice.value = enabled
        if (!enabled) {
            ttsManager.stop()
        }
    }

    fun setGeofenceRadiusMeters(meters: Int) {
        locationManager.setTriggerThresholdMeters(meters)
    }

    fun stepWalkDirection(deltaLat: Double, deltaLng: Double) {
        val current = locationState.value
        if (!current.hasLocationFix) {
            _episodeState.value = EpisodeGenerationState.Error(localizedString(R.string.error_no_gps_fix))
            return
        }
        _progressResetExplicitly.value = false
        val nextLat = (current.latitude + deltaLat).coerceIn(-89.9, 89.9)
        val nextLng = ((current.longitude + deltaLng + 540.0) % 360.0) - 180.0
        recordWaypointInSession(
            lat = nextLat,
            lng = nextLng,
            label = String.format(Locale.US, "%.4f, %.4f", nextLat, nextLng)
        )
        locationManager.stepOneBlock(deltaLat, deltaLng)
    }

    fun tuneToCustomNeighborhood(customName: String, lat: Double, lng: Double) {
        _progressResetExplicitly.value = false
        recordWaypointInSession(lat = lat, lng = lng, label = customName)
        locationManager.jumpToCustomLocation(
            customQueryLabel = customName,
            lat = lat,
            lng = lng,
            triggerEpisode = true
        )
    }

    fun triggerDocumentaryForCurrentBlock(
        customFocusNote: String? = null,
        forceBypassCache: Boolean = false
    ) {
        val current = locationState.value
        if (!current.hasLocationFix) {
            _episodeState.value = EpisodeGenerationState.Error(localizedString(R.string.error_no_gps_fix))
            return
        }
        locationManager.markCurrentBlockBroadcasted()
        generateBlockDocumentary(
            blockState = current,
            lens = _selectedLens.value,
            customFocusNote = customFocusNote,
            forceBypassCache = forceBypassCache
        )
    }

    private fun findSpatialCachedEpisode(
        lat: Double,
        lng: Double,
        lensLabel: String,
        radiusMeters: Int
    ): EpisodeEntity? {
        return savedEpisodes.value.firstOrNull { ep ->
            val dist = LocationGeofenceManager.haversineMeters(lat, lng, ep.latitude, ep.longitude)
            dist <= radiusMeters && ep.category.equals(lensLabel, ignoreCase = true)
        } ?: savedEpisodes.value.firstOrNull { ep ->
            val dist = LocationGeofenceManager.haversineMeters(lat, lng, ep.latitude, ep.longitude)
            dist <= radiusMeters
        }
    }

    private fun buildOfflineFallbackEpisode(
        blockState: LocationBlockState,
        lens: DocumentaryLens,
        reasonBadge: String = "LOCAL CACHE"
    ): EpisodeEntity {
        val nearbySummary = blockState.activeGeofenceNodes.joinToString(", ") {
            "${it.title} (${it.distanceMeters}m)"
        }
        val currentNarrator = ttsManager.state.value
        return EpisodeEntity(
            title = "Field Dispatch: ${blockState.streetBlockLabel}",
            stationCallSign = "${lens.frequencyTag} • $reasonBadge",
            locationLabel = "${blockState.streetBlockLabel} • ${blockState.neighborhoodName}",
            latitude = blockState.latitude,
            longitude = blockState.longitude,
            category = lens.label,
            hookLine = "Entering the ${blockState.streetBlockLabel} architectural corridor in ${blockState.neighborhoodName}.",
            narrativeScript = "You are walking along ${blockState.streetBlockLabel} in the heart of ${blockState.neighborhoodName}. " +
                "Even as WanderCast runs in zero-token offline archive mode, the street grid around coordinates ${String.format(Locale.US, "%.4f, %.4f", blockState.latitude, blockState.longitude)} reveals layers of urban design. " +
                "Notice how the corner lot geometry and cornice lines frame the intersection, while active geofence beacons track $nearbySummary. " +
                "Every block in ${blockState.cityRegion} carries its own stratigraphy of early transit corridors, masonry craft, and neighborhood gathering places.",
            fieldFactsJoined = listOf(
                "Current street sector: ${blockState.streetBlockLabel} (${blockState.cityRegion})",
                "Active geofence perimeter: ${blockState.triggerThresholdMeters}m radius • Heading ${blockState.headingCardinal}",
                "Zero API tokens consumed — synthesized locally via Android TextToSpeech ($reasonBadge)"
            ).joinToString("||"),
            explorerPrompt = "Observe the roofline elevations, ground-floor transom windows, and corner street signage as you approach the next intersection.",
            durationSeconds = 52,
            voicePersona = currentNarrator.voicePersona.displayName,
            speechRate = currentNarrator.speechRate,
            aiModelUsed = "On-Device Synthesizer ($reasonBadge)",
            wordCount = 118
        )
    }

    /**
     * Executes a direct REST request to Google's Gemini API when the user provides their own
     * optional BYOK API key ($0 developer cost).
     */
    private suspend fun callUserByokGeminiJson(
        apiKey: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json")
            )
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("BYOK Gemini call returned HTTP $code")
        }

        val responseBody = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
            it.readText()
        }
        val root = JSONObject(responseBody)
        root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    fun generateBlockDocumentary(
        blockState: LocationBlockState,
        lens: DocumentaryLens,
        customFocusNote: String? = null,
        forceBypassCache: Boolean = false
    ) {
        viewModelScope.launch {
            val stats = _quotaStats.value

            // 1. Check Spatial Geohash / Block Cache first to save 100% of API cost on visited blocks
            if (!forceBypassCache && stats.spatialCacheEnabled && customFocusNote.isNullOrBlank()) {
                val cached = findSpatialCachedEpisode(
                    lat = blockState.latitude,
                    lng = blockState.longitude,
                    lensLabel = lens.label,
                    radiusMeters = stats.spatialCacheRadiusMeters
                )
                if (cached != null) {
                    updateQuotaStats {
                        it.copy(
                            cacheHitsSavedCount = it.cacheHitsSavedCount + 1,
                            lastDispatchFromCache = true
                        )
                    }
                    _activeEpisode.value = cached
                    _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + cached.id
                    _episodeState.value = EpisodeGenerationState.Success(
                        episode = cached,
                        fromSpatialCache = true
                    )
                    if (_autoNarrateVoice.value) {
                        ttsManager.synthesizeEpisodeNarrative(
                            title = cached.title,
                            hookLine = cached.hookLine,
                            script = cached.narrativeScript,
                            explorerPrompt = cached.explorerPrompt,
                            fieldFacts = cached.fieldFacts
                        )
                    }
                    return@launch
                }
            }

            // 2. Check if user selected Zero-API Offline/Cache mode or hit their daily budget cap (unless BYOK is active)
            val byokKey = getSavedByokKey()
            val usingByok = stats.selectedTier == AiUsageTier.BYOK_USER_KEY && byokKey != null
            val isOverDailyBudget = !usingByok && stats.dailyApiCallsUsed >= stats.dailyApiCallBudget

            if (stats.selectedTier == AiUsageTier.CACHE_FIRST_SAVER || isOverDailyBudget) {
                val fallbackEp = buildOfflineFallbackEpisode(
                    blockState = blockState,
                    lens = lens,
                    reasonBadge = if (isOverDailyBudget) "QUOTA SAVER" else "OFFLINE CACHE"
                )
                val id = repository.insertEpisode(fallbackEp)
                val savedFallback = fallbackEp.copy(id = id)
                recordWaypointInSession(blockState.latitude, blockState.longitude, blockState.streetBlockLabel)
                synchronized(sessionEpisodeIds) { sessionEpisodeIds.add(id) }
                _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + id
                _quotaStats.value = stats.copy(lastDispatchFromCache = true)
                _activeEpisode.value = savedFallback
                _episodeState.value = EpisodeGenerationState.Success(
                    episode = savedFallback,
                    fromSpatialCache = true
                )
                if (_autoNarrateVoice.value) {
                    ttsManager.synthesizeEpisodeNarrative(
                        title = savedFallback.title,
                        hookLine = savedFallback.hookLine,
                        script = savedFallback.narrativeScript,
                        explorerPrompt = savedFallback.explorerPrompt,
                        fieldFacts = savedFallback.fieldFacts
                    )
                }
                return@launch
            }

            // 3. Route to free-tier Gemini model or user's BYOK key
            _episodeState.value = EpisodeGenerationState.Generating(
                blockName = blockState.streetBlockLabel,
                lensLabel = lens.label,
                modelId = stats.selectedTier.modelId
            )
            _liveHostQaState.value = LiveHostQaState.Idle

            val coordText = String.format(
                Locale.US,
                "%.5f, %.5f",
                blockState.latitude,
                blockState.longitude
            )
            val nearbyZones = blockState.activeGeofenceNodes.joinToString("; ") {
                "${it.zoneType}: ${it.title} (${it.distanceMeters}m away)"
            }

            val prompt = buildString {
                appendLine("You are the host and producer of 'WanderCast', an award-winning localized public radio walking documentary (in the style of 99% Invisible and This American Life).")
                appendLine("Write all text fields in ${currentLanguageNameForPrompt()}.")
                appendLine("The listener is currently walking on foot at:")
                appendLine("- Street / Block: ${blockState.streetBlockLabel}")
                appendLine("- Neighborhood & City: ${blockState.neighborhoodName}")
                appendLine("- GPS Coordinates: $coordText")
                if (nearbyZones.isNotBlank()) {
                    appendLine("- Detected Nearby Geofence Zones: $nearbyZones")
                }
                appendLine("- Documentary Editorial Lens: ${lens.label} (${lens.promptFocus})")
                if (!customFocusNote.isNullOrBlank()) {
                    appendLine("- Listener Special Curiosity Focus: $customFocusNote")
                }
                appendLine()
                appendLine("Return a JSON object with keys: episodeTitle, stationCallSign, hookLine, narrativeScript (~145 words), fieldFacts (array of 3 strings), explorerPrompt, estimatedDurationSeconds (int).")
            }

            try {
                val activeByokKey = if (usingByok) byokKey else null
                val jsonString = withTimeout(50_000L) {
                    if (activeByokKey != null) {
                        callUserByokGeminiJson(activeByokKey, prompt)
                    } else {
                        val targetModel = if (stats.selectedTier == AiUsageTier.ECO_FLASH_LITE) {
                            ecoDocumentaryModel
                        } else {
                            studioDocumentaryModel
                        }
                        targetModel.generateContent(prompt).text
                            ?: throw IllegalStateException("Model returned an empty documentary response.")
                    }
                }

                val json = JSONObject(jsonString)
                val title = json.getString("episodeTitle")
                val callSign = json.optString("stationCallSign", lens.frequencyTag)
                val hook = json.getString("hookLine")
                val script = json.getString("narrativeScript")
                val factsArray = json.getJSONArray("fieldFacts")
                val factsList = (0 until factsArray.length()).map { factsArray.getString(it) }
                val explorerPrompt = json.getString("explorerPrompt")
                val durationSec = json.optInt("estimatedDurationSeconds", 60).coerceIn(45, 90)

                val wordCount = script.split(Regex("\\s+")).count { it.isNotBlank() }
                val currentNarrator = ttsManager.state.value

                val entity = EpisodeEntity(
                    title = title,
                    stationCallSign = callSign,
                    locationLabel = "${blockState.streetBlockLabel} • ${blockState.neighborhoodName}",
                    latitude = blockState.latitude,
                    longitude = blockState.longitude,
                    category = lens.label,
                    hookLine = hook,
                    narrativeScript = script,
                    fieldFactsJoined = factsList.joinToString("||"),
                    explorerPrompt = explorerPrompt,
                    durationSeconds = durationSec,
                    voicePersona = currentNarrator.voicePersona.displayName,
                    speechRate = currentNarrator.speechRate,
                    aiModelUsed = stats.selectedTier.modelId,
                    wordCount = wordCount,
                    playCount = 1,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )

                val newId = repository.insertEpisode(entity)
                val savedEntity = entity.copy(id = newId)

                synchronized(sessionEpisodeIds) {
                    sessionEpisodeIds.add(newId)
                }
                recordWaypointInSession(
                    lat = blockState.latitude,
                    lng = blockState.longitude,
                    label = blockState.streetBlockLabel
                )
                _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + newId

                updateQuotaStats {
                    it.copy(
                        dailyApiCallsUsed = it.dailyApiCallsUsed + 1,
                        lastDispatchFromCache = false
                    )
                }

                _activeEpisode.value = savedEntity
                _episodeState.value = EpisodeGenerationState.Success(
                    episode = savedEntity,
                    fromSpatialCache = false
                )

                logAppEvent(
                    eventType = SmartEventType.DOCUMENTARY_GENERATED,
                    screenName = "live_broadcast",
                    metadataSummary = "Generated '${savedEntity.title}' (${lens.label})"
                )

                LocationService.updateNotificationStatus(
                    context = getApplication(),
                    blockTitle = blockState.streetBlockLabel,
                    statusSubtitle = localizedString(R.string.notif_on_air_format, savedEntity.title)
                )

                if (_autoNarrateVoice.value) {
                    ttsManager.synthesizeEpisodeNarrative(
                        title = savedEntity.title,
                        hookLine = savedEntity.hookLine,
                        script = savedEntity.narrativeScript,
                        explorerPrompt = savedEntity.explorerPrompt,
                        fieldFacts = savedEntity.fieldFacts
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Smart $0-Cost Graceful Fallback: Never strand the walker on an API/quota/network error
                Log.w(TAG, "Cloud AI generation unavailable (${e.message}); activating graceful on-device radio fallback.")
                val fallbackEp = buildOfflineFallbackEpisode(
                    blockState = blockState,
                    lens = lens,
                    reasonBadge = "GRACEFUL FALLBACK"
                )
                val fallbackId = repository.insertEpisode(fallbackEp)
                val savedFallback = fallbackEp.copy(id = fallbackId)
                recordWaypointInSession(blockState.latitude, blockState.longitude, blockState.streetBlockLabel)
                synchronized(sessionEpisodeIds) { sessionEpisodeIds.add(fallbackId) }
                _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + fallbackId

                _quotaStats.value = _quotaStats.value.copy(
                    gracefulFallbacksCount = _quotaStats.value.gracefulFallbacksCount + 1,
                    lastDispatchFromCache = true
                )
                _activeEpisode.value = savedFallback
                _episodeState.value = EpisodeGenerationState.Success(
                    episode = savedFallback,
                    fromSpatialCache = true
                )
                if (_autoNarrateVoice.value) {
                    ttsManager.synthesizeEpisodeNarrative(
                        title = savedFallback.title,
                        hookLine = savedFallback.hookLine,
                        script = savedFallback.narrativeScript,
                        explorerPrompt = savedFallback.explorerPrompt,
                        fieldFacts = savedFallback.fieldFacts
                    )
                }
            }
        }
    }

    /**
     * High-Value Interactive AI Feature: Generates a localized 3-choice Neighborhood Trivia &
     * Time-Travel Challenge for the current block, with automatic 0-token fallback derived from
     * the Room database's historical field facts when in Offline/Cache mode.
     */
    fun generateNeighborhoodTriviaChallenge() {
        val currentBlock = locationState.value
        val stats = _quotaStats.value
        val activeEp = _activeEpisode.value ?: savedEpisodes.value.firstOrNull()

        if (!currentBlock.hasLocationFix && activeEp == null) {
            _urbanTriviaState.value = UrbanTriviaState.Error(localizedString(R.string.error_trivia_unavailable))
            return
        }

        viewModelScope.launch {
            _urbanTriviaState.value = UrbanTriviaState.Generating

            val shouldUseLocalZeroTokenTrivia =
                stats.selectedTier == AiUsageTier.CACHE_FIRST_SAVER ||
                    (stats.selectedTier != AiUsageTier.BYOK_USER_KEY &&
                        stats.dailyApiCallsUsed >= stats.dailyApiCallBudget)

            if (shouldUseLocalZeroTokenTrivia) {
                val localChallenge = buildZeroTokenTriviaChallenge(currentBlock, activeEp)
                if (localChallenge != null) {
                    _urbanTriviaState.value = UrbanTriviaState.ActiveQuestion(
                        challenge = localChallenge,
                        score = triviaScore,
                        streak = triviaStreak
                    )
                } else {
                    _urbanTriviaState.value = UrbanTriviaState.Error(localizedString(R.string.error_trivia_unavailable))
                }
                return@launch
            }

            val locationName = currentBlock.streetBlockLabel.ifBlank { activeEp?.locationLabel.orEmpty() }
            val prompt = buildString {
                appendLine("Create a fun, intriguing 3-option urban history & architecture trivia question in ${currentLanguageNameForPrompt()} for an explorer walking at $locationName (${currentBlock.neighborhoodName}, ${currentBlock.cityRegion}).")
                if (activeEp != null) {
                    appendLine("Context from current radio dispatch '${activeEp.title}': ${activeEp.fieldFacts.joinToString("; ")}")
                }
                appendLine("Return JSON with keys: question, options (array of 3 strings), correctIndex (0, 1, or 2), radioRevealExplanation (2 vivid radio host sentences).")
            }

            try {
                val byokKey = getSavedByokKey()
                val jsonStr = withTimeout(35_000L) {
                    if (stats.selectedTier == AiUsageTier.BYOK_USER_KEY && byokKey != null) {
                        callUserByokGeminiJson(byokKey, prompt)
                    } else {
                        triviaChallengeModel.generateContent(prompt).text
                            ?: throw IllegalStateException("Empty trivia response")
                    }
                }
                val json = JSONObject(jsonStr)
                val q = json.getString("question")
                val optsArr = json.getJSONArray("options")
                val opts = (0 until optsArr.length()).map { optsArr.getString(it) }.take(3)
                if (opts.size < 3) {
                    throw IllegalStateException("Incomplete trivia options")
                }
                val correctIdx = json.optInt("correctIndex", 0).coerceIn(0, opts.size - 1)
                val explanation = json.getString("radioRevealExplanation")

                updateQuotaStats {
                    it.copy(dailyApiCallsUsed = it.dailyApiCallsUsed + 1)
                }

                _urbanTriviaState.value = UrbanTriviaState.ActiveQuestion(
                    challenge = UrbanTriviaChallenge(
                        question = q,
                        options = opts,
                        correctIndex = correctIdx,
                        radioRevealExplanation = explanation,
                        sourceLabel = stats.selectedTier.modelId
                    ),
                    score = triviaScore,
                    streak = triviaStreak
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Trivia AI call failed: ${e.message}")
                val fallbackChallenge = buildZeroTokenTriviaChallenge(currentBlock, activeEp)
                if (fallbackChallenge != null) {
                    _quotaStats.value = _quotaStats.value.copy(
                        gracefulFallbacksCount = _quotaStats.value.gracefulFallbacksCount + 1
                    )
                    _urbanTriviaState.value = UrbanTriviaState.ActiveQuestion(
                        challenge = fallbackChallenge,
                        score = triviaScore,
                        streak = triviaStreak
                    )
                } else {
                    _urbanTriviaState.value = UrbanTriviaState.Error(localizedString(R.string.error_trivia_unavailable))
                }
            }
        }
    }

    fun submitTriviaAnswer(optionIndex: Int) {
        val current = _urbanTriviaState.value
        if (current is UrbanTriviaState.ActiveQuestion && current.selectedOptionIndex == null) {
            val isCorrect = optionIndex == current.challenge.correctIndex
            if (isCorrect) {
                triviaScore += 100
                triviaStreak += 1
            } else {
                triviaStreak = 0
            }
            _urbanTriviaState.value = current.copy(
                selectedOptionIndex = optionIndex,
                score = triviaScore,
                streak = triviaStreak
            )
            if (_autoNarrateVoice.value) {
                ttsManager.synthesizeText(
                    title = localizedString(R.string.trivia_challenge_title),
                    text = current.challenge.radioRevealExplanation
                )
            }
            if (isCorrect) {
                logAppEvent(
                    eventType = SmartEventType.TRIVIA_CORRECT_ANSWER,
                    screenName = "live_broadcast",
                    metadataSummary = "Answered neighborhood trivia correctly (Score: $triviaScore, Streak: $triviaStreak)"
                )
            }
            evaluateAndUnlockWalkBadges()
        }
    }

    private fun buildZeroTokenTriviaChallenge(
        blockState: LocationBlockState,
        episode: EpisodeEntity?
    ): UrbanTriviaChallenge? {
        val otherEpisodes = savedEpisodes.value.filter { it.id != episode?.id }
        val trueFact = episode?.fieldFacts?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val distractors = otherEpisodes
            .flatMap { it.fieldFacts }
            .filter { it.isNotBlank() && it != trueFact }
            .distinct()
            .take(2)
        if (distractors.size < 2) return null

        val blockLabel = blockState.streetBlockLabel.ifBlank { episode.locationLabel }
        return UrbanTriviaChallenge(
            question = "${episode.title} — $blockLabel",
            options = listOf(trueFact, distractors[0], distractors[1]),
            correctIndex = 0,
            radioRevealExplanation = "$trueFact ${episode.hookLine}",
            sourceLabel = "Room DB"
        )
    }

    /**
     * Multimodal Vision Feature: Analyzes a photo of a building facade, historical plaque,
     * monument, or storefront taken on the walk and narrates a 45-second architectural commentary.
     */
    fun analyzeArchitectureOrPlaquePhoto(bitmap: Bitmap) {
        val currentBlock = locationState.value
        viewModelScope.launch {
            _visionScannerState.value = VisionScannerState.Analyzing
            try {
                val scaledBitmap = scaleBitmapDown(bitmap, 1024)
                val locationContext = if (currentBlock.hasLocationFix) {
                    "${currentBlock.streetBlockLabel} in ${currentBlock.neighborhoodName}"
                } else {
                    "their current walking location"
                }
                val prompt = buildString {
                    appendLine("You are WanderCast's architectural historian and public radio host.")
                    appendLine("Write all JSON string values in ${currentLanguageNameForPrompt()}.")
                    appendLine("The walker is currently at $locationContext and photographed this building, plaque, or street artifact.")
                    appendLine("Identify the architectural style, materials, or plaque details, estimate the era, and provide a vivid 45-second (~95-110 words) radio commentary.")
                }

                val response = withTimeout(60_000L) {
                    visionLandmarkModel.generateContent(
                        content {
                            image(scaledBitmap)
                            text(prompt)
                        }
                    )
                }

                val jsonString = response.text
                    ?: throw IllegalStateException("Vision model returned an empty response.")
                val json = JSONObject(jsonString)
                val title = json.getString("landmarkTitle")
                val style = json.getString("architecturalStyle")
                val era = json.getString("eraEstimate")
                val commentary = json.getString("radioCommentary")

                updateQuotaStats {
                    it.copy(dailyApiCallsUsed = it.dailyApiCallsUsed + 1)
                }

                _visionScannerState.value = VisionScannerState.Success(
                    landmarkTitle = title,
                    architecturalStyle = style,
                    radioCommentary = commentary,
                    eraEstimate = era
                )

                logAppEvent(
                    eventType = SmartEventType.PHOTO_VISION_SCANNED,
                    screenName = "live_broadcast",
                    metadataSummary = "Scanned landmark photo: $title ($style)"
                )

                if (_autoNarrateVoice.value) {
                    ttsManager.synthesizeText(
                        title = title,
                        text = "$title. $style, $era. $commentary"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Vision cloud call unavailable: ${e.message}")
                _visionScannerState.value = VisionScannerState.Error(
                    localizedString(R.string.error_vision_unavailable)
                )
            }
        }
    }

    /**
     * Generates a curated 3-stop neighborhood walking route using ultra-fast free-tier
     * gemini-3.5-flash-lite (or BYOK) and builds a deep-link URL to open
     * turn-by-turn walking directions in Google Maps.
     */
    fun generateWalkingTourRoute() {
        val currentBlock = locationState.value
        if (!currentBlock.hasLocationFix) {
            _walkingRouteState.value = WalkingRouteState.Error(localizedString(R.string.error_no_gps_fix))
            return
        }
        val lens = _selectedLens.value
        viewModelScope.launch {
            _walkingRouteState.value = WalkingRouteState.Generating
            val prompt = buildString {
                appendLine("Design a fascinating 3-stop neighborhood walking audio tour in ${currentLanguageNameForPrompt()} starting from ${currentBlock.streetBlockLabel} in ${currentBlock.neighborhoodName} (GPS: ${currentBlock.latitude}, ${currentBlock.longitude}).")
                appendLine("Editorial Theme: ${lens.label} (${lens.promptFocus}).")
                appendLine("Provide 3 real, walkable nearby street intersections or landmarks within 15-30 minutes on foot.")
                appendLine("Return JSON with keys: tourTitle, totalMinutes (int), totalDistanceLabel, stops (array of 3 objects with stopNumber, name, streetIntersection, walkingMinutes, storyTeaser, architecturalHighlight).")
            }

            try {
                val byokKey = getSavedByokKey()
                val jsonString = withTimeout(50_000L) {
                    if (_quotaStats.value.selectedTier == AiUsageTier.BYOK_USER_KEY && byokKey != null) {
                        callUserByokGeminiJson(byokKey, prompt)
                    } else {
                        walkingRouteModel.generateContent(prompt).text
                            ?: throw IllegalStateException("Route planner returned empty response.")
                    }
                }
                val json = JSONObject(jsonString)
                val tourTitle = json.getString("tourTitle")
                val totalMinutes = json.optInt("totalMinutes", 25)
                val totalDistanceLabel = json.optString("totalDistanceLabel", "1.2 km")
                val stopsArray = json.getJSONArray("stops")
                val stops = (0 until stopsArray.length()).map { idx ->
                    val obj = stopsArray.getJSONObject(idx)
                    WalkingTourStop(
                        stopNumber = obj.optInt("stopNumber", idx + 1),
                        name = obj.getString("name"),
                        streetIntersection = obj.getString("streetIntersection"),
                        walkingMinutes = obj.optInt("walkingMinutes", 8),
                        storyTeaser = obj.getString("storyTeaser"),
                        architecturalHighlight = obj.getString("architecturalHighlight")
                    )
                }

                persistAndPublishWalkingItinerary(
                    currentBlock = currentBlock,
                    lens = lens,
                    tourTitle = tourTitle,
                    totalMinutes = totalMinutes,
                    totalDistanceLabel = totalDistanceLabel,
                    stops = stops
                )

                updateQuotaStats {
                    it.copy(dailyApiCallsUsed = it.dailyApiCallsUsed + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Walking route cloud call unavailable: ${e.message}")
                _walkingRouteState.value = WalkingRouteState.Error(
                    localizedString(R.string.error_route_unavailable)
                )
            }
        }
    }

    private suspend fun persistAndPublishWalkingItinerary(
        currentBlock: LocationBlockState,
        lens: DocumentaryLens,
        tourTitle: String,
        totalMinutes: Int,
        totalDistanceLabel: String,
        stops: List<WalkingTourStop>
    ) {
        val origin = "${currentBlock.latitude},${currentBlock.longitude}"
        val destination = android.net.Uri.encode(
            "${stops.lastOrNull()?.streetIntersection ?: currentBlock.streetBlockLabel}, ${currentBlock.cityRegion}"
        )
        val waypoints = stops.dropLast(1).joinToString("|") {
            android.net.Uri.encode("${it.streetIntersection}, ${currentBlock.cityRegion}")
        }
        val mapsUrl = if (waypoints.isNotEmpty()) {
            "https://www.google.com/maps/dir/?api=1&travelmode=walking&origin=$origin&destination=$destination&waypoints=$waypoints"
        } else {
            "https://www.google.com/maps/dir/?api=1&travelmode=walking&origin=$origin&destination=$destination"
        }

        val itinerary = WalkingTourItinerary(
            tourTitle = tourTitle,
            neighborhood = currentBlock.neighborhoodName,
            totalMinutes = totalMinutes,
            totalDistanceLabel = totalDistanceLabel,
            stops = stops,
            googleMapsWalkingUrl = mapsUrl
        )
        _walkingRouteState.value = WalkingRouteState.Success(itinerary)

        val routeWaypoints = stops.mapIndexed { i, stop ->
            RouteWaypoint(
                latitude = currentBlock.latitude + (i + 1) * 0.0008,
                longitude = currentBlock.longitude + (i + 1) * 0.0006,
                label = "${stop.name} (${stop.streetIntersection})"
            )
        }
        val routeEntity = WalkRouteEntity(
            routeTitle = tourTitle,
            neighborhoodName = currentBlock.neighborhoodName,
            cityRegion = currentBlock.cityRegion,
            startBlockLabel = currentBlock.streetBlockLabel,
            endBlockLabel = stops.lastOrNull()?.streetIntersection ?: currentBlock.streetBlockLabel,
            startLatitude = currentBlock.latitude,
            startLongitude = currentBlock.longitude,
            endLatitude = routeWaypoints.lastOrNull()?.latitude ?: currentBlock.latitude,
            endLongitude = routeWaypoints.lastOrNull()?.longitude ?: currentBlock.longitude,
            waypointsEncoded = WalkRouteEntity.encodeWaypoints(routeWaypoints),
            totalDistanceMeters = (totalMinutes * 65).coerceAtLeast(350),
            durationSeconds = totalMinutes * 60,
            blocksVisitedCount = stops.size.coerceAtLeast(1),
            narrativesGeneratedCount = sessionEpisodeIds.size.coerceAtLeast(if (_activeEpisode.value != null) 1 else 0),
            editorialLens = lens.label,
            googleMapsUrl = mapsUrl,
            startedAtTimestamp = sessionStartTimestamp,
            completedAtTimestamp = System.currentTimeMillis()
        )
        val linkedIds = synchronized(sessionEpisodeIds) {
            if (sessionEpisodeIds.isNotEmpty()) sessionEpisodeIds.toList()
            else listOfNotNull(_activeEpisode.value?.id)
        }
        repository.saveCompletedWalkRoute(routeEntity, linkedIds)

        logAppEvent(
            eventType = SmartEventType.ROUTE_PLANNED,
            screenName = "live_broadcast",
            metadataSummary = "Planned 3-stop walking route: $tourTitle ($totalDistanceLabel)"
        )

        if (_autoNarrateVoice.value) {
            val routeIntro = buildString {
                append("New walking route ready: $tourTitle. ")
                stops.forEach { stop ->
                    append("Stop ${stop.stopNumber}: ${stop.name} at ${stop.streetIntersection}. ${stop.storyTeaser} ")
                }
            }
            ttsManager.synthesizeText(title = tourTitle, text = routeIntro)
        }
    }

    fun askRadioHostQuestion(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        val currentBlock = locationState.value
        val currentEp = _activeEpisode.value
        val stats = _quotaStats.value

        viewModelScope.launch {
            _liveHostQaState.value = LiveHostQaState.Loading(trimmed)
            val accumulated = StringBuilder()

            val prompt = buildString {
                appendLine("You are the live on-air radio documentary host of WanderCast (99.1 FM).")
                appendLine("Respond in ${currentLanguageNameForPrompt()}.")
                if (currentBlock.hasLocationFix) {
                    appendLine("The walker is currently standing at ${currentBlock.streetBlockLabel} in ${currentBlock.neighborhoodName} (GPS: ${currentBlock.latitude}, ${currentBlock.longitude}).")
                }
                if (currentEp != null) {
                    appendLine("Current Episode Context: '${currentEp.title}' — ${currentEp.hookLine}")
                }
                appendLine("The walker just pressed their field microphone button and asked: \"$trimmed\"")
                appendLine("Respond in 3 to 4 crisp, engaging radio-narrator sentences (~65-85 words) answering their question with specific historical, architectural, or local neighborhood insight.")
            }

            val byokKey = getSavedByokKey()
            val activeByokKey = if (stats.selectedTier == AiUsageTier.BYOK_USER_KEY) byokKey else null
            val useOfflineQa = stats.selectedTier == AiUsageTier.CACHE_FIRST_SAVER ||
                (activeByokKey == null && stats.dailyApiCallsUsed >= stats.dailyApiCallBudget)

            try {
                if (useOfflineQa) {
                    throw IllegalStateException("Offline cache mode or daily budget reached")
                }
                if (activeByokKey != null) {
                    val jsonReply = withTimeout(45_000L) {
                        callUserByokGeminiJson(
                            activeByokKey,
                            "$prompt\nReturn JSON with key: reply (string)."
                        )
                    }
                    val parsedReply = runCatching { JSONObject(jsonReply).optString("reply", jsonReply) }
                        .getOrDefault(jsonReply)
                    accumulated.append(parsedReply)
                } else {
                    val streamingModel = if (stats.selectedTier == AiUsageTier.ECO_FLASH_LITE) {
                        ecoHostStreamingModel
                    } else {
                        liveHostStreamingModel
                    }
                    withTimeout(50_000L) {
                        streamingModel.generateContentStream(prompt).collect { chunk ->
                            val chunkText = chunk.text.orEmpty()
                            accumulated.append(chunkText)
                            _liveHostQaState.value = LiveHostQaState.Streaming(
                                question = trimmed,
                                partialReply = accumulated.toString()
                            )
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    updateQuotaStats {
                        it.copy(dailyApiCallsUsed = it.dailyApiCallsUsed + 1)
                    }
                    _liveHostQaState.value = LiveHostQaState.Success(
                        question = trimmed,
                        reply = finalReply
                    )
                    if (_autoNarrateVoice.value) {
                        ttsManager.synthesizeText(
                            title = "Live Field Q&A • ${currentBlock.streetBlockLabel}",
                            text = finalReply
                        )
                    }
                } else {
                    throw IllegalStateException("Empty Q&A response")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Live host Q&A cloud stream unavailable; providing graceful local archive reply", e)
                val fallbackReply = buildString {
                    append("Standing here at ${currentBlock.streetBlockLabel} in ${currentBlock.neighborhoodName}, ")
                    if (currentEp != null && currentEp.fieldFacts.isNotEmpty()) {
                        append(currentEp.fieldFacts.joinToString(". "))
                        append(". ")
                        append(currentEp.explorerPrompt)
                    } else {
                        append("this block reflects the layered street grid evolution of ${currentBlock.cityRegion}, blending early masonry foundations with neighborhood storefront culture.")
                    }
                }
                _quotaStats.value = _quotaStats.value.copy(
                    gracefulFallbacksCount = _quotaStats.value.gracefulFallbacksCount + 1
                )
                _liveHostQaState.value = LiveHostQaState.Success(
                    question = trimmed,
                    reply = fallbackReply
                )
                if (_autoNarrateVoice.value) {
                    ttsManager.synthesizeText(
                        title = "Live Field Q&A • ${currentBlock.streetBlockLabel}",
                        text = fallbackReply
                    )
                }
            }
        }
    }

    fun playEpisodeNarration(episode: EpisodeEntity) {
        _activeEpisode.value = episode
        _visitedHistoricalPointIds.value = _visitedHistoricalPointIds.value + episode.id
        _episodeState.value = EpisodeGenerationState.Success(episode)
        viewModelScope.launch {
            val currentNarrator = ttsManager.state.value
            repository.recordNarrativePlayback(
                episodeId = episode.id,
                voicePersona = currentNarrator.voicePersona.displayName,
                speechRate = currentNarrator.speechRate
            )
        }
        ttsManager.synthesizeEpisodeNarrative(
            title = episode.title,
            hookLine = episode.hookLine,
            script = episode.narrativeScript,
            explorerPrompt = episode.explorerPrompt,
            fieldFacts = episode.fieldFacts
        )
        logAppEvent(
            eventType = SmartEventType.EPISODE_NARRATED,
            screenName = lastLoggedScreenRoute.ifBlank { "live_broadcast" },
            metadataSummary = "Narrated episode '${episode.title}'"
        )
    }

    fun toggleNarrationPlayPause() {
        val currentEp = _activeEpisode.value
        val nState = narratorState.value
        if (!nState.isSpeaking && !nState.isPaused && currentEp != null) {
            ttsManager.synthesizeEpisodeNarrative(
                title = currentEp.title,
                hookLine = currentEp.hookLine,
                script = currentEp.narrativeScript,
                explorerPrompt = currentEp.explorerPrompt,
                fieldFacts = currentEp.fieldFacts
            )
        } else {
            ttsManager.togglePlayPause()
        }
    }

    fun skipForwardSentence() {
        ttsManager.skipForwardSentence()
    }

    fun skipBackwardSentence() {
        ttsManager.skipBackwardSentence()
    }

    fun setNarrationSpeed(rate: Float) {
        ttsManager.setSpeechRate(rate)
    }

    fun cycleNarratorVoicePersona() {
        ttsManager.cycleVoicePersona()
    }

    fun selectNarratorVoicePersona(persona: TtsVoicePersona) {
        ttsManager.setVoicePersona(persona)
    }

    fun toggleBookmark(episode: EpisodeEntity) {
        val willBeBookmarked = !episode.isBookmarked
        viewModelScope.launch {
            repository.toggleBookmark(episode)
            if (_activeEpisode.value?.id == episode.id) {
                _activeEpisode.value = episode.copy(isBookmarked = willBeBookmarked)
            }
            if (willBeBookmarked) {
                logAppEvent(
                    eventType = SmartEventType.EPISODE_BOOKMARKED,
                    screenName = lastLoggedScreenRoute.ifBlank { "field_tapes" },
                    metadataSummary = "Bookmarked favorite episode '${episode.title}'"
                )
            }
        }
    }

    fun deleteEpisode(episodeId: Long) {
        viewModelScope.launch {
            repository.deleteEpisode(episodeId)
            if (_activeEpisode.value?.id == episodeId) {
                _activeEpisode.value = null
            }
        }
    }

    /**
     * Snapshots and persists the user's current walking session (blocks visited, GPS waypoints,
     * distance walked, and all generated audio narratives) into the Room [WalkRouteEntity] table.
     */
    fun logCurrentWalkSessionToHistory(customTitle: String? = null) {
        val currentLoc = locationState.value
        val existingWaypoints = synchronized(sessionWaypoints) { sessionWaypoints.toList() }
        if (!currentLoc.hasLocationFix && existingWaypoints.isEmpty()) return

        val lens = _selectedLens.value
        viewModelScope.launch {
            val waypointsList = if (existingWaypoints.isNotEmpty()) {
                existingWaypoints
            } else {
                listOf(
                    RouteWaypoint(
                        latitude = currentLoc.latitude,
                        longitude = currentLoc.longitude,
                        label = currentLoc.streetBlockLabel
                    )
                )
            }
            val firstPoint = waypointsList.first()
            val lastPoint = waypointsList.last()
            val linkedIds = synchronized(sessionEpisodeIds) {
                sessionEpisodeIds.toList()
            }

            val elapsedSec = ((System.currentTimeMillis() - sessionStartTimestamp) / 1000L)
                .toInt()
                .coerceAtLeast(1)
            val distMeters = currentLoc.totalWalkedMeters
            val mapsUrl = "https://www.google.com/maps/dir/?api=1&travelmode=walking" +
                "&origin=${firstPoint.latitude},${firstPoint.longitude}" +
                "&destination=${lastPoint.latitude},${lastPoint.longitude}"

            val route = WalkRouteEntity(
                routeTitle = customTitle?.takeIf { it.isNotBlank() }
                    ?: "${currentLoc.streetBlockLabel} • ${localizedString(lens.labelRes)}",
                neighborhoodName = currentLoc.neighborhoodName,
                cityRegion = currentLoc.cityRegion,
                startBlockLabel = firstPoint.label,
                endBlockLabel = lastPoint.label,
                startLatitude = firstPoint.latitude,
                startLongitude = firstPoint.longitude,
                endLatitude = lastPoint.latitude,
                endLongitude = lastPoint.longitude,
                waypointsEncoded = WalkRouteEntity.encodeWaypoints(waypointsList),
                totalDistanceMeters = distMeters,
                durationSeconds = elapsedSec,
                blocksVisitedCount = currentLoc.blocksExploredCount.coerceAtLeast(waypointsList.size),
                narrativesGeneratedCount = linkedIds.size,
                editorialLens = lens.label,
                googleMapsUrl = mapsUrl,
                startedAtTimestamp = sessionStartTimestamp,
                completedAtTimestamp = System.currentTimeMillis()
            )
            repository.saveCompletedWalkRoute(route, linkedIds)
            sessionStartTimestamp = System.currentTimeMillis()
            synchronized(sessionWaypoints) {
                sessionWaypoints.clear()
                if (currentLoc.hasLocationFix) {
                    sessionWaypoints.add(
                        RouteWaypoint(
                            currentLoc.latitude,
                            currentLoc.longitude,
                            currentLoc.streetBlockLabel
                        )
                    )
                }
                _activeSessionWaypoints.value = sessionWaypoints.toList()
            }
            synchronized(sessionEpisodeIds) {
                sessionEpisodeIds.clear()
            }
            logAppEvent(
                eventType = SmartEventType.WALK_TOUR_SAVED,
                screenName = lastLoggedScreenRoute.ifBlank { "tour_summary" },
                metadataSummary = "Saved completed walk tour '${route.routeTitle}' (${route.totalDistanceMeters}m)"
            )
            evaluateAndUnlockWalkBadges(triggeringTourTitle = route.routeTitle)
        }
    }

    fun toggleWalkRouteFavorite(route: WalkRouteEntity) {
        viewModelScope.launch {
            repository.toggleRouteFavorite(route)
        }
    }

    fun deleteWalkRoute(routeId: Long) {
        viewModelScope.launch {
            repository.deleteWalkRoute(routeId)
        }
    }

    private fun recordWaypointInSession(lat: Double, lng: Double, label: String) {
        synchronized(sessionWaypoints) {
            val last = sessionWaypoints.lastOrNull()
            if (last == null ||
                LocationGeofenceManager.haversineMeters(last.latitude, last.longitude, lat, lng) >= 8.0
            ) {
                sessionWaypoints.add(RouteWaypoint(lat, lng, label))
                _activeSessionWaypoints.value = sessionWaypoints.toList()
            }
        }
    }

    /**
     * High-Value Feature: Pre-Fetch Lookahead Buffer Along Heading Vector.
     * Projects 95m ahead along the walker's current compass heading (`headingDegrees`) and
     * pre-warms the Room 65m Spatial Block Cache in the background so that when the user
     * crosses into the next intersection, playback starts with 0ms latency.
     */
    fun prefetchNextBlockAlongHeading() {
        val currentLoc = locationState.value
        if (!currentLoc.hasLocationFix) {
            _lookaheadPrefetchState.value = LookaheadPrefetchState.Error(localizedString(R.string.error_no_gps_fix))
            return
        }
        val lens = _selectedLens.value
        val targetLat = currentLoc.predictedNextIntersectionLat
        val targetLng = currentLoc.predictedNextIntersectionLng
        val targetHint = currentLoc.predictedNextBlockHint

        viewModelScope.launch {
            // Check if the predicted intersection is already warmed in the 65m spatial cache
            val existingCached = findSpatialCachedEpisode(
                lat = targetLat,
                lng = targetLng,
                lensLabel = lens.label,
                radiusMeters = _quotaStats.value.spatialCacheRadiusMeters
            )
            if (existingCached != null) {
                _lookaheadPrefetchState.value = LookaheadPrefetchState.WarmedInCache(
                    episode = existingCached,
                    targetBlockHint = targetHint
                )
                return@launch
            }

            _lookaheadPrefetchState.value = LookaheadPrefetchState.Prefetching(
                targetBlockHint = targetHint,
                bearingCardinal = currentLoc.headingCardinal
            )

            try {
                val stats = _quotaStats.value
                val activeByokKey = if (stats.selectedTier == AiUsageTier.BYOK_USER_KEY) getSavedByokKey() else null
                val entity = if (stats.selectedTier == AiUsageTier.CACHE_FIRST_SAVER ||
                    (activeByokKey == null && stats.dailyApiCallsUsed >= stats.dailyApiCallBudget)
                ) {
                    val lookaheadState = currentLoc.copy(
                        latitude = targetLat,
                        longitude = targetLng,
                        streetBlockLabel = targetHint
                    )
                    buildOfflineFallbackEpisode(lookaheadState, lens, "LOOKAHEAD CACHE")
                } else {
                    val prompt = buildString {
                        appendLine("You are the host of 'WanderCast', a localized public radio walking documentary.")
                        appendLine("Write all JSON string values in ${currentLanguageNameForPrompt()}.")
                        appendLine("The walker is currently heading ${currentLoc.headingCardinal} (${currentLoc.headingDegrees.roundToInt()}°) toward the upcoming intersection at:")
                        appendLine("- Upcoming Block Sector: $targetHint")
                        appendLine("- Neighborhood: ${currentLoc.neighborhoodName}")
                        appendLine("- GPS Coordinates: ${String.format(Locale.US, "%.5f, %.5f", targetLat, targetLng)}")
                        appendLine("- Editorial Lens: ${lens.label} (${lens.promptFocus})")
                        appendLine("Pre-generate a vivid 60-second (~140 words) radio documentary episode for this upcoming block so it can play with 0ms latency as soon as the walker arrives.")
                        appendLine("Return JSON with keys: episodeTitle, stationCallSign, hookLine, narrativeScript, fieldFacts (array of 3 strings), explorerPrompt, estimatedDurationSeconds (int).")
                    }
                    val jsonString = withTimeout(45_000L) {
                        if (activeByokKey != null) {
                            callUserByokGeminiJson(activeByokKey, prompt)
                        } else {
                            ecoDocumentaryModel.generateContent(prompt).text
                                ?: throw IllegalStateException("Empty lookahead response.")
                        }
                    }
                    val json = JSONObject(jsonString)
                    val title = json.getString("episodeTitle")
                    val callSign = json.optString("stationCallSign", "${lens.frequencyTag} • LOOKAHEAD")
                    val hook = json.getString("hookLine")
                    val script = json.getString("narrativeScript")
                    val factsArray = json.getJSONArray("fieldFacts")
                    val factsList = (0 until factsArray.length()).map { factsArray.getString(it) }
                    val explorerPrompt = json.getString("explorerPrompt")
                    val durationSec = json.optInt("estimatedDurationSeconds", 60).coerceIn(45, 90)
                    val wordCount = script.split(Regex("\\s+")).count { it.isNotBlank() }

                    _quotaStats.value = _quotaStats.value.copy(
                        dailyApiCallsUsed = _quotaStats.value.dailyApiCallsUsed + 1
                    )

                    EpisodeEntity(
                        title = title,
                        stationCallSign = callSign,
                        locationLabel = "$targetHint • ${currentLoc.neighborhoodName}",
                        latitude = targetLat,
                        longitude = targetLng,
                        category = lens.label,
                        hookLine = hook,
                        narrativeScript = script,
                        fieldFactsJoined = factsList.joinToString("||"),
                        explorerPrompt = explorerPrompt,
                        durationSeconds = durationSec,
                        voicePersona = ttsManager.state.value.voicePersona.displayName,
                        speechRate = ttsManager.state.value.speechRate,
                        aiModelUsed = "gemini-3.5-flash-lite (Lookahead)",
                        wordCount = wordCount,
                        playCount = 0
                    )
                }

                val savedId = repository.insertEpisode(entity)
                val savedEp = entity.copy(id = savedId)
                synchronized(sessionEpisodeIds) {
                    sessionEpisodeIds.add(savedId)
                }
                _lookaheadPrefetchState.value = LookaheadPrefetchState.WarmedInCache(
                    episode = savedEp,
                    targetBlockHint = targetHint
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lookahead prefetch fell back to local synthesizer: ${e.message}")
                val lookaheadLoc = currentLoc.copy(
                    latitude = targetLat,
                    longitude = targetLng,
                    streetBlockLabel = targetHint
                )
                val fallbackEntity = buildOfflineFallbackEpisode(lookaheadLoc, lens, "LOOKAHEAD FALLBACK")
                val savedId = repository.insertEpisode(fallbackEntity)
                val savedEp = fallbackEntity.copy(id = savedId)
                _quotaStats.value = _quotaStats.value.copy(
                    gracefulFallbacksCount = _quotaStats.value.gracefulFallbacksCount + 1
                )
                _lookaheadPrefetchState.value = LookaheadPrefetchState.WarmedInCache(
                    episode = savedEp,
                    targetBlockHint = targetHint
                )
            }
        }
    }

    /**
     * Restores the most recent real user-recorded episode on relaunch (if any exist in Room)
     * and initializes the 8 achievement definitions at 0 progress on a clean install.
     */
    private fun restoreLatestEpisodeAndInitBadges() {
        viewModelScope.launch {
            try {
                seedInitialWalkBadgesIfEmpty()
                savedEpisodes.collect { list ->
                    if (_activeEpisode.value == null && list.isNotEmpty()) {
                        _activeEpisode.value = list.first()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to initialize local Room state: ${e.message}")
            }
        }
    }

    /**
     * Registers the 8 digital badge achievement definitions in the Room `walk_badges` table on
     * first install. All badges start cleanly at 0 progress and locked until earned by real walks.
     */
    private suspend fun seedInitialWalkBadgesIfEmpty() {
        if (repository.countWalkBadges() > 0) return
        val initialBadges = listOf(
            WalkBadgeEntity(
                badgeId = "first_transmission",
                category = "TOUR_MILESTONE",
                iconType = "RADIO_TOWER",
                xpReward = 150,
                currentProgress = 0,
                targetProgress = 1,
                progressUnit = "tours",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 1
            ),
            WalkBadgeEntity(
                badgeId = "early_bird",
                category = "TIME_OF_DAY",
                iconType = "SUNRISE",
                xpReward = 250,
                currentProgress = 0,
                targetProgress = 1,
                progressUnit = "dawn_walks",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 2
            ),
            WalkBadgeEntity(
                badgeId = "marathoner",
                category = "ENDURANCE",
                iconType = "MARATHON_SHOE",
                xpReward = 400,
                currentProgress = 0,
                targetProgress = 1500,
                progressUnit = "meters",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 3
            ),
            WalkBadgeEntity(
                badgeId = "architecture_critic",
                category = "HISTORIANS_LENS",
                iconType = "ARCH_COLUMN",
                xpReward = 200,
                currentProgress = 0,
                targetProgress = 3,
                progressUnit = "dispatches",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 4
            ),
            WalkBadgeEntity(
                badgeId = "geofence_pioneer",
                category = "GEOFENCE_EXPLORER",
                iconType = "RADAR_COMPASS",
                xpReward = 300,
                currentProgress = 0,
                targetProgress = 3,
                progressUnit = "checkpoints",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 5
            ),
            WalkBadgeEntity(
                badgeId = "deep_historian",
                category = "TOUR_MILESTONE",
                iconType = "ARCHIVE_SCROLL",
                xpReward = 350,
                currentProgress = 0,
                targetProgress = 3,
                progressUnit = "tours",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 6
            ),
            WalkBadgeEntity(
                badgeId = "night_owl",
                category = "TIME_OF_DAY",
                iconType = "CRESCENT_MOON",
                xpReward = 225,
                currentProgress = 0,
                targetProgress = 1,
                progressUnit = "night_walks",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 7
            ),
            WalkBadgeEntity(
                badgeId = "trivia_virtuoso",
                category = "HISTORIANS_LENS",
                iconType = "TROPHY_STAR",
                xpReward = 275,
                currentProgress = 0,
                targetProgress = 200,
                progressUnit = "pts",
                isUnlocked = false,
                unlockedAtTimestamp = null,
                unlockedTourTitle = null,
                sortOrder = 8
            )
        )
        repository.upsertWalkBadges(initialBadges)
    }

    /**
     * Reactively monitors completed Room walk routes, saved episodes, visited geofence checkpoints,
     * and live walk telemetry to update badge progress and unlock digital badges automatically.
     */
    private fun observeWalkHistoryForBadgeRewards() {
        viewModelScope.launch {
            seedInitialWalkBadgesIfEmpty()
            combine(
                completedWalkRoutes,
                savedEpisodes,
                visitedHistoricalPointIds,
                locationState
            ) { routes, episodes, visitedIds, loc ->
                WalkRewardSnapshot(routes, episodes, visitedIds, loc)
            }.collectLatest {
                evaluateAndUnlockWalkBadges()
            }
        }
    }

    private data class WalkRewardSnapshot(
        val routes: List<WalkRouteWithNarratives>,
        val episodes: List<EpisodeEntity>,
        val visitedIds: Set<Long>,
        val loc: LocationBlockState
    )

    fun evaluateAndUnlockWalkBadges(triggeringTourTitle: String? = null) {
        viewModelScope.launch {
            try {
                seedInitialWalkBadgesIfEmpty()
                val currentBadges = repository.getWalkBadgesSnapshot()
                if (currentBadges.isEmpty()) return@launch

                val routes = completedWalkRoutes.value
                val episodes = savedEpisodes.value
                val visitedIds = visitedHistoricalPointIds.value
                val loc = locationState.value
                val now = System.currentTimeMillis()

                val totalCompletedTours = routes.size
                val totalWalkedMeters = routes.sumOf { it.route.totalDistanceMeters } +
                    loc.totalWalkedMeters
                val totalDispatches = episodes.size
                val visitedGeofenceCount = visitedIds.size

                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isMorningHour = currentHour in 5..10
                val isEveningHour = currentHour >= 18 || currentHour <= 4
                val hasNightArchivistEpisode = episodes.any {
                    it.voicePersona.contains("Night", ignoreCase = true)
                }

                val latestTourName = triggeringTourTitle
                    ?: routes.firstOrNull()?.route?.routeTitle
                    ?: loc.streetBlockLabel.takeIf { it.isNotBlank() }

                var newlyUnlockedId: String? = null

                val updatedBadges = currentBadges.map { badge ->
                    val (newCurrent, shouldUnlock) = when (badge.badgeId) {
                        "first_transmission" -> {
                            val prog = totalCompletedTours.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        "early_bird" -> {
                            val prog = if (badge.isUnlocked || (isMorningHour && totalCompletedTours >= 1)) 1 else badge.currentProgress
                            prog to (prog >= badge.targetProgress)
                        }
                        "marathoner" -> {
                            val prog = totalWalkedMeters.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        "architecture_critic" -> {
                            val prog = totalDispatches.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        "geofence_pioneer" -> {
                            val prog = visitedGeofenceCount.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        "deep_historian" -> {
                            val prog = totalCompletedTours.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        "night_owl" -> {
                            val prog = if (badge.isUnlocked || (isEveningHour && totalCompletedTours >= 1) || hasNightArchivistEpisode) 1 else badge.currentProgress
                            prog to (prog >= badge.targetProgress)
                        }
                        "trivia_virtuoso" -> {
                            val prog = triviaScore.coerceAtLeast(badge.currentProgress)
                            prog to (prog >= badge.targetProgress)
                        }
                        else -> badge.currentProgress to badge.isUnlocked
                    }

                    val unlockedNow = !badge.isUnlocked && shouldUnlock
                    if (unlockedNow) {
                        newlyUnlockedId = badge.badgeId
                    }

                    badge.copy(
                        currentProgress = if (badge.isUnlocked || shouldUnlock) {
                            newCurrent.coerceAtLeast(badge.targetProgress)
                        } else {
                            newCurrent
                        },
                        isUnlocked = badge.isUnlocked || shouldUnlock,
                        unlockedAtTimestamp = if (unlockedNow) now else badge.unlockedAtTimestamp,
                        unlockedTourTitle = if (unlockedNow) latestTourName else badge.unlockedTourTitle
                    )
                }

                if (updatedBadges != currentBadges) {
                    repository.upsertWalkBadges(updatedBadges)
                }

                if (newlyUnlockedId != null) {
                    _newlyUnlockedBadgeId.value = newlyUnlockedId
                    logAppEvent(
                        eventType = SmartEventType.BADGE_UNLOCKED,
                        screenName = lastLoggedScreenRoute.ifBlank { "walk_rewards" },
                        metadataSummary = "Unlocked digital walk badge '$newlyUnlockedId' on tour '$latestTourName'"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error evaluating walk badges: ${e.message}")
            }
        }
    }

    /**
     * Allows the explorer to complete a tour milestone and unlock a specific digital badge
     * (e.g., 'Early Bird' or 'Marathoner') in Room storage with immediate audio narration celebration.
     */
    fun simulateCompleteMilestoneForBadge(
        badgeId: String,
        badgeTitleForSpeech: String,
        badgeDescriptionForSpeech: String
    ) {
        viewModelScope.launch {
            seedInitialWalkBadgesIfEmpty()
            val snapshot = repository.getWalkBadgesSnapshot()
            val target = snapshot.firstOrNull { it.badgeId == badgeId } ?: return@launch
            val now = System.currentTimeMillis()
            val activeTourTitle = completedWalkRoutes.value.firstOrNull()?.route?.routeTitle
                ?: "${locationState.value.streetBlockLabel} Historical Tour"

            val unlockedBadge = target.copy(
                currentProgress = target.targetProgress,
                isUnlocked = true,
                unlockedAtTimestamp = now,
                unlockedTourTitle = activeTourTitle
            )
            repository.upsertWalkBadge(unlockedBadge)
            _newlyUnlockedBadgeId.value = badgeId

            logAppEvent(
                eventType = SmartEventType.BADGE_UNLOCKED,
                screenName = "walk_rewards",
                metadataSummary = "Unlocked '$badgeTitleForSpeech' (+${target.xpReward} XP) on '$activeTourTitle'",
                bypassColdStartGuardForSimulation = true
            )

            if (_autoNarrateVoice.value) {
                ttsManager.synthesizeText(
                    title = "Badge Unlocked • $badgeTitleForSpeech",
                    text = "Congratulations urban explorer! You unlocked the $badgeTitleForSpeech digital badge on $activeTourTitle, earning ${target.xpReward} explorer experience points. $badgeDescriptionForSpeech"
                )
            }
        }
    }

    fun narrateUnlockedBadgeStory(
        badgeTitle: String,
        badgeDescription: String,
        unlockedTourTitle: String?,
        xpReward: Int
    ) {
        val tourPart = if (!unlockedTourTitle.isNullOrBlank()) {
            " Earned during your $unlockedTourTitle tour."
        } else {
            ""
        }
        ttsManager.synthesizeText(
            title = "Walk Reward • $badgeTitle",
            text = "$badgeTitle digital badge. Worth $xpReward explorer XP. $badgeDescription.$tourPart"
        )
    }

    private fun scaleBitmapDown(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (ratio > 1f) {
            maxDimension to (maxDimension / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).roundToInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    override fun onCleared() {
        super.onCleared()
        locationManager.cleanup()
        ttsManager.shutdown()
    }
}
