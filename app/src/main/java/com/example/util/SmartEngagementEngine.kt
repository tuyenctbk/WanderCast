package com.example.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import com.example.BuildConfig
import com.example.R
import kotlin.math.pow

enum class SmartPromptType(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val badgeTagRes: Int
) {
    APP_UPDATE(
        id = "APP_UPDATE",
        titleRes = R.string.smart_prompt_update_title,
        badgeTagRes = R.string.smart_prompt_update_badge
    ),
    RATE_APP(
        id = "RATE_APP",
        titleRes = R.string.smart_prompt_rate_title,
        badgeTagRes = R.string.smart_prompt_rate_badge
    ),
    SHARE_APP(
        id = "SHARE_APP",
        titleRes = R.string.smart_prompt_share_title,
        badgeTagRes = R.string.smart_prompt_share_badge
    )
}

enum class SmartEventType(
    val key: String,
    val category: String,
    val defaultDelightDelta: Int,
    val isPeakDelightMoment: Boolean
) {
    APP_SESSION_START("APP_SESSION_START", "LIFECYCLE", 0, false),
    SCREEN_VIEW("SCREEN_VIEW", "NAVIGATION", 2, false),
    DOCUMENTARY_GENERATED("DOCUMENTARY_GENERATED", "CORE_VALUE", 18, true),
    EPISODE_NARRATED("EPISODE_NARRATED", "CORE_VALUE", 10, false),
    EPISODE_BOOKMARKED("EPISODE_BOOKMARKED", "PEAK_DELIGHT", 18, true),
    WALK_TOUR_SAVED("WALK_TOUR_SAVED", "PEAK_DELIGHT", 32, true),
    BADGE_UNLOCKED("BADGE_UNLOCKED", "PEAK_DELIGHT", 38, true),
    TRIVIA_CORRECT_ANSWER("TRIVIA_CORRECT_ANSWER", "PEAK_DELIGHT", 22, true),
    ROUTE_PLANNED("ROUTE_PLANNED", "PEAK_DELIGHT", 20, true),
    PHOTO_VISION_SCANNED("PHOTO_VISION_SCANNED", "CORE_VALUE", 20, true),
    LANGUAGE_SWITCHED("LANGUAGE_SWITCHED", "PREFERENCE", 5, false),
    BROADCAST_ERROR("BROADCAST_ERROR", "FRICTION", -40, false),
    PROMPT_IMPRESSION("PROMPT_IMPRESSION", "GROWTH_PROMPT", 0, false),
    PROMPT_ACCEPTED("PROMPT_ACCEPTED", "GROWTH_PROMPT", 15, false),
    PROMPT_SNOOZED("PROMPT_SNOOZED", "GROWTH_PROMPT", 0, false),
    PROMPT_OPT_OUT("PROMPT_OPT_OUT", "GROWTH_PROMPT", 0, false)
}

data class SmartAutoPopupProposal(
    val promptType: SmartPromptType,
    val triggerEvent: String,
    val delightScoreAtTrigger: Int,
    val activeNeighborhood: String = "",
    val unlockedBadgesCount: Int = 0,
    val totalWalkedMeters: Int = 0,
    val installedVersion: String = BuildConfig.VERSION_NAME,
    val latestAvailableVersion: String = BuildConfig.VERSION_NAME
)

data class SmartEngagementTelemetryState(
    val sessionCount: Int = 1,
    val sessionAgeSeconds: Long = 0L,
    val sessionPositiveActionsCount: Int = 0,
    val lifetimePositiveActionsCount: Int = 0,
    val delightScore: Int = 0,
    val promptsShownThisSession: Int = 0,
    val maxPromptsPerSession: Int = 1,
    val isColdStartProtected: Boolean = true,
    val coldStartRemainingSeconds: Int = 45,
    val rateImpressionsCount: Int = 0,
    val rateMaxLifetimeImpressions: Int = 3,
    val rateCompletedOrOptedOut: Boolean = false,
    val rateCooldownRemainingHours: Int = 0,
    val shareImpressionsCount: Int = 0,
    val shareMaxLifetimeImpressions: Int = 5,
    val shareOptedOut: Boolean = false,
    val shareCooldownRemainingHours: Int = 0,
    val updateImpressionsCount: Int = 0,
    val updateInstalledVersion: String = BuildConfig.VERSION_NAME,
    val updateAvailableVersion: String = BuildConfig.VERSION_NAME,
    val updateSkippedVersion: String = "",
    val updateAppliedInSession: Boolean = false,
    val updateCooldownRemainingHours: Int = 0,
    val lastAlgorithmDecisionReason: String = "Cold-Start Guard Active: Auto-popups are suppressed during app startup."
)

/**
 * Implements an anti-annoyance, peak-delight heuristic algorithm to decide WHEN (and IF) to
 * display an automatic suggestion popup (Update App, Share App, Rate App).
 *
 * Key Anti-Annoyance Invariants:
 * 1. NEVER show Rate/Share/Update popups at app start (45-second cold-start grace period).
 * 2. NEVER show more than 1 auto-popup per session (`maxPromptsPerSession = 1`).
 * 3. NEVER show popups during an active loading/generation operation or within 2 minutes of an error.
 * 4. ONLY trigger auto-popups immediately after a "Peak Delight Moment" (e.g., unlocking a Walk Badge,
 *    completing & saving a Walk Tour, scoring a Trivia streak, or bookmarking an episode).
 * 5. Enforce multi-day exponential backoff cooldowns (`14d * 2^snoozes` for Rate, `5d * 2^snoozes` for Share,
 *    `7d` for Update) and strict lifetime impression caps (`max 3` for Rate) plus permanent 1-tap opt-out.
 */
class SmartEngagementEngine(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val KEY_SESSION_COUNT = "eng_session_count"
        private const val KEY_LIFETIME_POSITIVE_ACTIONS = "eng_lifetime_pos_actions"
        private const val KEY_DELIGHT_SCORE = "eng_delight_score"
        private const val KEY_LAST_GLOBAL_PROMPT_TS = "eng_last_global_prompt_ts"
        private const val KEY_LAST_ERROR_TS = "eng_last_error_ts"

        // Rate App persistence keys
        private const val KEY_RATE_IMPRESSIONS = "eng_rate_impressions"
        private const val KEY_RATE_SNOOZE_COUNT = "eng_rate_snooze_count"
        private const val KEY_RATE_LAST_SHOWN_TS = "eng_rate_last_shown_ts"
        private const val KEY_RATE_COMPLETED_OR_OPT_OUT = "eng_rate_done_or_opt_out"

        // Share App persistence keys
        private const val KEY_SHARE_IMPRESSIONS = "eng_share_impressions"
        private const val KEY_SHARE_SNOOZE_COUNT = "eng_share_snooze_count"
        private const val KEY_SHARE_LAST_SHOWN_TS = "eng_share_last_shown_ts"
        private const val KEY_SHARE_OPT_OUT = "eng_share_opt_out"

        // Update App persistence keys
        private const val KEY_UPDATE_IMPRESSIONS = "eng_update_impressions"
        private const val KEY_UPDATE_SNOOZE_COUNT = "eng_update_snooze_count"
        private const val KEY_UPDATE_LAST_SHOWN_TS = "eng_update_last_shown_ts"
        private const val KEY_UPDATE_SKIPPED_VERSION = "eng_update_skipped_version"
        private const val KEY_UPDATE_APPLIED_VERSION = "eng_update_applied_version"

        // Guardrail Constants
        const val COLD_START_GRACE_PERIOD_MS = 45_000L // 45s after app launch: NEVER show auto-popup
        const val ERROR_FRICTION_COOLDOWN_MS = 120_000L // 2 minutes after any error
        const val GLOBAL_PROMPT_COOLDOWN_MS = 24L * 3600_000L // 24 hours between any auto-popups
        const val RATE_BASE_COOLDOWN_MS = 14L * 24L * 3600_000L // 14 days base cooldown for Rate App
        const val SHARE_BASE_COOLDOWN_MS = 5L * 24L * 3600_000L // 5 days base cooldown for Share App
        const val UPDATE_BASE_COOLDOWN_MS = 7L * 24L * 3600_000L // 7 days base cooldown for Update App

        const val RATE_MAX_LIFETIME_IMPRESSIONS = 3
        const val SHARE_MAX_LIFETIME_IMPRESSIONS = 5
        const val MAX_PROMPTS_PER_SESSION = 1

        val DEFAULT_INSTALLED_VERSION: String = BuildConfig.VERSION_NAME
        val LATEST_AVAILABLE_VERSION: String = BuildConfig.VERSION_NAME

        fun launchShareAppIntent(context: Context, shareMessage: String) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "WanderCast 99.1 FM • AI GPS Neighborhood Documentary Radio")
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }
            val chooser = Intent.createChooser(sendIntent, "Share WanderCast 99.1 FM")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (_: Exception) {
            }
        }

        fun launchStoreReviewOrUpdateIntent(context: Context) {
            val pkg = context.packageName
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkg")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(webIntent)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val sessionStartTimestampMs: Long = System.currentTimeMillis()
    private var sessionPositiveActions: Int = 0
    private var promptsShownThisSession: Int = 0
    private var lastDecisionExplanation: String =
        "Cold-Start Guard Active: Auto-popups are suppressed during the first 45s of app start."

    init {
        val currentSessions = prefs.getInt(KEY_SESSION_COUNT, 0)
        val nextSession = currentSessions + 1
        prefs.edit().putInt(KEY_SESSION_COUNT, nextSession).apply()
    }

    fun getSessionNumber(): Int = prefs.getInt(KEY_SESSION_COUNT, 1)

    fun recordEventAndEvaluate(
        eventType: SmartEventType,
        metadataSummary: String,
        activeNeighborhood: String,
        unlockedBadgesCount: Int,
        totalWalkedMeters: Int,
        isCurrentlyGeneratingOrBusy: Boolean,
        bypassColdStartGuardForSimulation: Boolean = false
    ): SmartAutoPopupProposal? {
        val now = System.currentTimeMillis()

        // 1. Update Delight Score & Session Action Counters
        val currentDelight = prefs.getInt(KEY_DELIGHT_SCORE, 0)
        val newDelight = (currentDelight + eventType.defaultDelightDelta).coerceIn(0, 100)
        val editor = prefs.edit().putInt(KEY_DELIGHT_SCORE, newDelight)

        if (eventType.defaultDelightDelta > 0) {
            sessionPositiveActions += 1
            val lifetimeActions = prefs.getInt(KEY_LIFETIME_POSITIVE_ACTIONS, 0) + 1
            editor.putInt(KEY_LIFETIME_POSITIVE_ACTIONS, lifetimeActions)
        } else if (eventType == SmartEventType.BROADCAST_ERROR) {
            editor.putLong(KEY_LAST_ERROR_TS, now)
        }
        editor.apply()

        // Only evaluate auto-popup triggers on positive / delight events
        if (eventType.defaultDelightDelta <= 0) {
            if (eventType == SmartEventType.BROADCAST_ERROR) {
                lastDecisionExplanation =
                    "Suppressed: Recent broadcast error logged (-40 delight). Auto-popups paused for 2 minutes."
            }
            return null
        }

        // 2. Anti-Annoyance Guardrail #1: Cold-Start Grace Period (Never show at app start!)
        val sessionAgeMs = now - sessionStartTimestampMs
        if (!bypassColdStartGuardForSimulation && sessionAgeMs < COLD_START_GRACE_PERIOD_MS) {
            val remSec = ((COLD_START_GRACE_PERIOD_MS - sessionAgeMs) / 1000L).coerceAtLeast(1L)
            lastDecisionExplanation =
                "Blocked by Cold-Start Grace Guard (${sessionAgeMs / 1000}s elapsed, ${remSec}s remaining) — never interrupt app launch."
            return null
        }

        // 3. Anti-Annoyance Guardrail #2: Active Task & Error Friction Guard
        if (isCurrentlyGeneratingOrBusy) {
            lastDecisionExplanation =
                "Blocked by Active Task Guard — never interrupt while Gemini AI is generating an episode."
            return null
        }
        val lastErrorTs = prefs.getLong(KEY_LAST_ERROR_TS, 0L)
        if (lastErrorTs > 0L && (now - lastErrorTs) < ERROR_FRICTION_COOLDOWN_MS) {
            lastDecisionExplanation =
                "Blocked by Friction Guard — error occurred recently; waiting for smooth user experience."
            return null
        }

        // 4. Anti-Annoyance Guardrail #3: Single-Prompt-Per-Session Cap
        if (!bypassColdStartGuardForSimulation && promptsShownThisSession >= MAX_PROMPTS_PER_SESSION) {
            lastDecisionExplanation =
                "Blocked by Session Frequency Cap ($promptsShownThisSession/$MAX_PROMPTS_PER_SESSION shown) — max 1 popup per session."
            return null
        }

        // 5. Anti-Annoyance Guardrail #4: Global Cross-Prompt Cooldown (24h between any two auto-popups)
        val lastGlobalPromptTs = prefs.getLong(KEY_LAST_GLOBAL_PROMPT_TS, 0L)
        if (!bypassColdStartGuardForSimulation &&
            lastGlobalPromptTs > 0L &&
            (now - lastGlobalPromptTs) < GLOBAL_PROMPT_COOLDOWN_MS
        ) {
            val hoursLeft = ((GLOBAL_PROMPT_COOLDOWN_MS - (now - lastGlobalPromptTs)) / 3600_000L).coerceAtLeast(1L)
            lastDecisionExplanation =
                "Blocked by Global 24h Cooldown (${hoursLeft}h remaining since last popup)."
            return null
        }

        // 6. Anti-Annoyance Guardrail #5: Require a genuine Peak Delight Moment
        if (!eventType.isPeakDelightMoment) {
            lastDecisionExplanation =
                "Waiting for a Peak Delight Moment (e.g., Badge Unlock, Walk Tour Saved, Episode Bookmarked, Trivia Win). Current Delight: $newDelight/100."
            return null
        }

        // 7. Priority Evaluation across Rate App, Share App, and Update App
        // Priority A: RATE_APP (Highest delight threshold: >= 65, Peak Delight Moment, not rated/opted-out, < 3 lifetime impressions)
        if (isRateAppEligible(now, newDelight, bypassColdStartGuardForSimulation)) {
            val proposal = buildProposal(
                promptType = SmartPromptType.RATE_APP,
                triggerEvent = "${eventType.key} ($metadataSummary)",
                delightScore = newDelight,
                activeNeighborhood = activeNeighborhood,
                unlockedBadgesCount = unlockedBadgesCount,
                totalWalkedMeters = totalWalkedMeters
            )
            markPromptDisplayed(SmartPromptType.RATE_APP, now)
            lastDecisionExplanation =
                "Selected RATE_APP after Peak Delight (${eventType.key}, Delight $newDelight/100 >= 65, Session #${getSessionNumber()})."
            return proposal
        }

        // Priority B: SHARE_APP (Delight threshold: >= 48, especially after Badge Unlock, Walk Tour Saved, or Route Planned)
        val isShareTriggerEvent = eventType in setOf(
            SmartEventType.BADGE_UNLOCKED,
            SmartEventType.WALK_TOUR_SAVED,
            SmartEventType.ROUTE_PLANNED,
            SmartEventType.EPISODE_BOOKMARKED
        )
        if (isShareTriggerEvent && isShareAppEligible(now, newDelight, bypassColdStartGuardForSimulation)) {
            val proposal = buildProposal(
                promptType = SmartPromptType.SHARE_APP,
                triggerEvent = "${eventType.key} ($metadataSummary)",
                delightScore = newDelight,
                activeNeighborhood = activeNeighborhood,
                unlockedBadgesCount = unlockedBadgesCount,
                totalWalkedMeters = totalWalkedMeters
            )
            markPromptDisplayed(SmartPromptType.SHARE_APP, now)
            lastDecisionExplanation =
                "Selected SHARE_APP after Achievement Milestone (${eventType.key}, Delight $newDelight/100 >= 48)."
            return proposal
        }

        // Priority C: APP_UPDATE (Non-intrusive version 1.5.0 suggestion after user completes a core action & Delight >= 25)
        if (isUpdateAppEligible(now, newDelight, bypassColdStartGuardForSimulation)) {
            val proposal = buildProposal(
                promptType = SmartPromptType.APP_UPDATE,
                triggerEvent = "${eventType.key} ($metadataSummary)",
                delightScore = newDelight,
                activeNeighborhood = activeNeighborhood,
                unlockedBadgesCount = unlockedBadgesCount,
                totalWalkedMeters = totalWalkedMeters
            )
            markPromptDisplayed(SmartPromptType.APP_UPDATE, now)
            lastDecisionExplanation =
                "Selected APP_UPDATE (v$DEFAULT_INSTALLED_VERSION → v$LATEST_AVAILABLE_VERSION) after positive walk activity."
            return proposal
        }

        lastDecisionExplanation =
            "All eligible prompts are currently on multi-day cooldown or completed. Delight Score: $newDelight/100."
        return null
    }

    /**
     * Allows the user or reviewer to manually preview any of the 3 smart popups (Update, Share, Rate)
     * from the Smart Engagement & Event Telemetry Inspector card.
     */
    fun buildManualPreviewProposal(
        promptType: SmartPromptType,
        activeNeighborhood: String,
        unlockedBadgesCount: Int,
        totalWalkedMeters: Int
    ): SmartAutoPopupProposal {
        val currentDelight = prefs.getInt(KEY_DELIGHT_SCORE, 0)
        lastDecisionExplanation =
            "Manual Inspector Preview triggered for ${promptType.id} (Delight Score: $currentDelight/100)."
        return buildProposal(
            promptType = promptType,
            triggerEvent = "INSPECTOR_PREVIEW",
            delightScore = currentDelight,
            activeNeighborhood = activeNeighborhood,
            unlockedBadgesCount = unlockedBadgesCount,
            totalWalkedMeters = totalWalkedMeters
        )
    }

    private fun isRateAppEligible(
        now: Long,
        delightScore: Int,
        ignoreCooldownForSimulation: Boolean
    ): Boolean {
        val completedOrOptOut = prefs.getBoolean(KEY_RATE_COMPLETED_OR_OPT_OUT, false)
        if (completedOrOptOut) return false

        val impressions = prefs.getInt(KEY_RATE_IMPRESSIONS, 0)
        if (impressions >= RATE_MAX_LIFETIME_IMPRESSIONS) return false

        val lifetimePositive = prefs.getInt(KEY_LIFETIME_POSITIVE_ACTIONS, 0)
        if (delightScore < 65 || lifetimePositive < 2) return false

        if (!ignoreCooldownForSimulation) {
            val lastShown = prefs.getLong(KEY_RATE_LAST_SHOWN_TS, 0L)
            val snoozeCount = prefs.getInt(KEY_RATE_SNOOZE_COUNT, 0)
            val backoffMultiplier = 2.0.pow(snoozeCount.coerceAtMost(3).toDouble()).toLong()
            val requiredCooldownMs = RATE_BASE_COOLDOWN_MS * backoffMultiplier
            if (lastShown > 0L && (now - lastShown) < requiredCooldownMs) {
                return false
            }
        }
        return true
    }

    private fun isShareAppEligible(
        now: Long,
        delightScore: Int,
        ignoreCooldownForSimulation: Boolean
    ): Boolean {
        val optedOut = prefs.getBoolean(KEY_SHARE_OPT_OUT, false)
        if (optedOut) return false

        val impressions = prefs.getInt(KEY_SHARE_IMPRESSIONS, 0)
        if (impressions >= SHARE_MAX_LIFETIME_IMPRESSIONS) return false

        if (delightScore < 48) return false

        if (!ignoreCooldownForSimulation) {
            val lastShown = prefs.getLong(KEY_SHARE_LAST_SHOWN_TS, 0L)
            val snoozeCount = prefs.getInt(KEY_SHARE_SNOOZE_COUNT, 0)
            val backoffMultiplier = 2.0.pow(snoozeCount.coerceAtMost(3).toDouble()).toLong()
            val requiredCooldownMs = SHARE_BASE_COOLDOWN_MS * backoffMultiplier
            if (lastShown > 0L && (now - lastShown) < requiredCooldownMs) {
                return false
            }
        }
        return true
    }

    private fun isUpdateAppEligible(
        now: Long,
        delightScore: Int,
        ignoreCooldownForSimulation: Boolean
    ): Boolean {
        val appliedVersion = prefs.getString(KEY_UPDATE_APPLIED_VERSION, "") ?: ""
        if (appliedVersion == LATEST_AVAILABLE_VERSION) return false

        val skippedVersion = prefs.getString(KEY_UPDATE_SKIPPED_VERSION, "") ?: ""
        if (skippedVersion == LATEST_AVAILABLE_VERSION) return false

        if (delightScore < 25) return false

        if (!ignoreCooldownForSimulation) {
            val lastShown = prefs.getLong(KEY_UPDATE_LAST_SHOWN_TS, 0L)
            val snoozeCount = prefs.getInt(KEY_UPDATE_SNOOZE_COUNT, 0)
            val backoffMultiplier = 2.0.pow(snoozeCount.coerceAtMost(2).toDouble()).toLong()
            val requiredCooldownMs = UPDATE_BASE_COOLDOWN_MS * backoffMultiplier
            if (lastShown > 0L && (now - lastShown) < requiredCooldownMs) {
                return false
            }
        }
        return true
    }

    private fun markPromptDisplayed(promptType: SmartPromptType, now: Long) {
        promptsShownThisSession += 1
        val editor = prefs.edit().putLong(KEY_LAST_GLOBAL_PROMPT_TS, now)
        when (promptType) {
            SmartPromptType.RATE_APP -> {
                val count = prefs.getInt(KEY_RATE_IMPRESSIONS, 0) + 1
                editor.putInt(KEY_RATE_IMPRESSIONS, count)
                editor.putLong(KEY_RATE_LAST_SHOWN_TS, now)
            }
            SmartPromptType.SHARE_APP -> {
                val count = prefs.getInt(KEY_SHARE_IMPRESSIONS, 0) + 1
                editor.putInt(KEY_SHARE_IMPRESSIONS, count)
                editor.putLong(KEY_SHARE_LAST_SHOWN_TS, now)
            }
            SmartPromptType.APP_UPDATE -> {
                val count = prefs.getInt(KEY_UPDATE_IMPRESSIONS, 0) + 1
                editor.putInt(KEY_UPDATE_IMPRESSIONS, count)
                editor.putLong(KEY_UPDATE_LAST_SHOWN_TS, now)
            }
        }
        editor.apply()
    }

    fun onPromptAccepted(promptType: SmartPromptType) {
        val editor = prefs.edit()
        val currentDelight = prefs.getInt(KEY_DELIGHT_SCORE, 60)
        editor.putInt(KEY_DELIGHT_SCORE, (currentDelight + 10).coerceAtMost(100))

        when (promptType) {
            SmartPromptType.RATE_APP -> {
                // Once rated, NEVER bother the user with Rate App again
                editor.putBoolean(KEY_RATE_COMPLETED_OR_OPT_OUT, true)
                lastDecisionExplanation =
                    "Listener rated WanderCast! Rate App auto-popup is now permanently completed."
            }
            SmartPromptType.SHARE_APP -> {
                editor.putLong(KEY_SHARE_LAST_SHOWN_TS, System.currentTimeMillis())
                lastDecisionExplanation =
                    "Listener shared WanderCast! Next Share suggestion placed on 5-day cooldown."
            }
            SmartPromptType.APP_UPDATE -> {
                editor.putString(KEY_UPDATE_APPLIED_VERSION, LATEST_AVAILABLE_VERSION)
                lastDecisionExplanation =
                    "Updated to WanderCast v$LATEST_AVAILABLE_VERSION! Update prompt marked up-to-date."
            }
        }
        editor.apply()
    }

    fun onPromptSnoozed(promptType: SmartPromptType) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit().putLong(KEY_LAST_GLOBAL_PROMPT_TS, now)
        when (promptType) {
            SmartPromptType.RATE_APP -> {
                val snoozes = prefs.getInt(KEY_RATE_SNOOZE_COUNT, 0) + 1
                editor.putInt(KEY_RATE_SNOOZE_COUNT, snoozes)
                editor.putLong(KEY_RATE_LAST_SHOWN_TS, now)
                val cooldownDays = 14 * (2.0.pow(snoozes.coerceAtMost(3).toDouble()).toInt())
                lastDecisionExplanation =
                    "Rate App snoozed (#$snoozes). Exponential backoff applied: ${cooldownDays}-day cooldown."
            }
            SmartPromptType.SHARE_APP -> {
                val snoozes = prefs.getInt(KEY_SHARE_SNOOZE_COUNT, 0) + 1
                editor.putInt(KEY_SHARE_SNOOZE_COUNT, snoozes)
                editor.putLong(KEY_SHARE_LAST_SHOWN_TS, now)
                val cooldownDays = 5 * (2.0.pow(snoozes.coerceAtMost(3).toDouble()).toInt())
                lastDecisionExplanation =
                    "Share App snoozed (#$snoozes). Exponential backoff applied: ${cooldownDays}-day cooldown."
            }
            SmartPromptType.APP_UPDATE -> {
                val snoozes = prefs.getInt(KEY_UPDATE_SNOOZE_COUNT, 0) + 1
                editor.putInt(KEY_UPDATE_SNOOZE_COUNT, snoozes)
                editor.putLong(KEY_UPDATE_LAST_SHOWN_TS, now)
                lastDecisionExplanation =
                    "App Update snoozed (#$snoozes). Cooldown applied for 7 days."
            }
        }
        editor.apply()
    }

    fun onPromptOptOutForever(promptType: SmartPromptType) {
        val editor = prefs.edit()
        when (promptType) {
            SmartPromptType.RATE_APP -> {
                editor.putBoolean(KEY_RATE_COMPLETED_OR_OPT_OUT, true)
                lastDecisionExplanation =
                    "Permanent Opt-Out recorded for RATE_APP. Will never auto-popup again."
            }
            SmartPromptType.SHARE_APP -> {
                editor.putBoolean(KEY_SHARE_OPT_OUT, true)
                lastDecisionExplanation =
                    "Permanent Opt-Out recorded for SHARE_APP. Will never auto-popup again."
            }
            SmartPromptType.APP_UPDATE -> {
                editor.putString(KEY_UPDATE_SKIPPED_VERSION, LATEST_AVAILABLE_VERSION)
                lastDecisionExplanation =
                    "Version $LATEST_AVAILABLE_VERSION skipped permanently."
            }
        }
        editor.apply()
    }

    fun resetAllPromptCooldownsForTesting() {
        promptsShownThisSession = 0
        prefs.edit()
            .putInt(KEY_DELIGHT_SCORE, 70)
            .putLong(KEY_LAST_GLOBAL_PROMPT_TS, 0L)
            .putLong(KEY_LAST_ERROR_TS, 0L)
            .putInt(KEY_RATE_IMPRESSIONS, 0)
            .putInt(KEY_RATE_SNOOZE_COUNT, 0)
            .putLong(KEY_RATE_LAST_SHOWN_TS, 0L)
            .putBoolean(KEY_RATE_COMPLETED_OR_OPT_OUT, false)
            .putInt(KEY_SHARE_IMPRESSIONS, 0)
            .putInt(KEY_SHARE_SNOOZE_COUNT, 0)
            .putLong(KEY_SHARE_LAST_SHOWN_TS, 0L)
            .putBoolean(KEY_SHARE_OPT_OUT, false)
            .putInt(KEY_UPDATE_IMPRESSIONS, 0)
            .putInt(KEY_UPDATE_SNOOZE_COUNT, 0)
            .putLong(KEY_UPDATE_LAST_SHOWN_TS, 0L)
            .putString(KEY_UPDATE_SKIPPED_VERSION, "")
            .putString(KEY_UPDATE_APPLIED_VERSION, "")
            .apply()
        lastDecisionExplanation =
            "All prompt cooldowns, opt-outs & session caps reset. Delight Score set to 70/100."
    }

    fun buildTelemetrySnapshot(): SmartEngagementTelemetryState {
        val now = System.currentTimeMillis()
        val sessionAgeMs = (now - sessionStartTimestampMs).coerceAtLeast(0L)
        val isColdStart = sessionAgeMs < COLD_START_GRACE_PERIOD_MS
        val coldStartRemSec = if (isColdStart) {
            ((COLD_START_GRACE_PERIOD_MS - sessionAgeMs) / 1000L).toInt().coerceAtLeast(1)
        } else 0

        val rateLastTs = prefs.getLong(KEY_RATE_LAST_SHOWN_TS, 0L)
        val rateSnoozes = prefs.getInt(KEY_RATE_SNOOZE_COUNT, 0)
        val rateCooldownMs = RATE_BASE_COOLDOWN_MS * 2.0.pow(rateSnoozes.coerceAtMost(3).toDouble()).toLong()
        val rateRemHours = if (rateLastTs > 0L && (now - rateLastTs) < rateCooldownMs) {
            ((rateCooldownMs - (now - rateLastTs)) / 3600_000L).toInt()
        } else 0

        val shareLastTs = prefs.getLong(KEY_SHARE_LAST_SHOWN_TS, 0L)
        val shareSnoozes = prefs.getInt(KEY_SHARE_SNOOZE_COUNT, 0)
        val shareCooldownMs = SHARE_BASE_COOLDOWN_MS * 2.0.pow(shareSnoozes.coerceAtMost(3).toDouble()).toLong()
        val shareRemHours = if (shareLastTs > 0L && (now - shareLastTs) < shareCooldownMs) {
            ((shareCooldownMs - (now - shareLastTs)) / 3600_000L).toInt()
        } else 0

        val updateLastTs = prefs.getLong(KEY_UPDATE_LAST_SHOWN_TS, 0L)
        val updateSnoozes = prefs.getInt(KEY_UPDATE_SNOOZE_COUNT, 0)
        val updateCooldownMs = UPDATE_BASE_COOLDOWN_MS * 2.0.pow(updateSnoozes.coerceAtMost(2).toDouble()).toLong()
        val updateRemHours = if (updateLastTs > 0L && (now - updateLastTs) < updateCooldownMs) {
            ((updateCooldownMs - (now - updateLastTs)) / 3600_000L).toInt()
        } else 0

        val appliedVersion = prefs.getString(KEY_UPDATE_APPLIED_VERSION, "") ?: ""
        val installedVer = if (appliedVersion == LATEST_AVAILABLE_VERSION) {
            LATEST_AVAILABLE_VERSION
        } else {
            DEFAULT_INSTALLED_VERSION
        }

        return SmartEngagementTelemetryState(
            sessionCount = getSessionNumber(),
            sessionAgeSeconds = sessionAgeMs / 1000L,
            sessionPositiveActionsCount = sessionPositiveActions,
            lifetimePositiveActionsCount = prefs.getInt(KEY_LIFETIME_POSITIVE_ACTIONS, 0),
            delightScore = prefs.getInt(KEY_DELIGHT_SCORE, 0),
            promptsShownThisSession = promptsShownThisSession,
            maxPromptsPerSession = MAX_PROMPTS_PER_SESSION,
            isColdStartProtected = isColdStart,
            coldStartRemainingSeconds = coldStartRemSec,
            rateImpressionsCount = prefs.getInt(KEY_RATE_IMPRESSIONS, 0),
            rateMaxLifetimeImpressions = RATE_MAX_LIFETIME_IMPRESSIONS,
            rateCompletedOrOptedOut = prefs.getBoolean(KEY_RATE_COMPLETED_OR_OPT_OUT, false),
            rateCooldownRemainingHours = rateRemHours,
            shareImpressionsCount = prefs.getInt(KEY_SHARE_IMPRESSIONS, 0),
            shareMaxLifetimeImpressions = SHARE_MAX_LIFETIME_IMPRESSIONS,
            shareOptedOut = prefs.getBoolean(KEY_SHARE_OPT_OUT, false),
            shareCooldownRemainingHours = shareRemHours,
            updateImpressionsCount = prefs.getInt(KEY_UPDATE_IMPRESSIONS, 0),
            updateInstalledVersion = installedVer,
            updateAvailableVersion = LATEST_AVAILABLE_VERSION,
            updateSkippedVersion = prefs.getString(KEY_UPDATE_SKIPPED_VERSION, "") ?: "",
            updateAppliedInSession = appliedVersion == LATEST_AVAILABLE_VERSION,
            updateCooldownRemainingHours = updateRemHours,
            lastAlgorithmDecisionReason = lastDecisionExplanation
        )
    }

    private fun buildProposal(
        promptType: SmartPromptType,
        triggerEvent: String,
        delightScore: Int,
        activeNeighborhood: String,
        unlockedBadgesCount: Int,
        totalWalkedMeters: Int
    ): SmartAutoPopupProposal {
        return SmartAutoPopupProposal(
            promptType = promptType,
            triggerEvent = triggerEvent,
            delightScoreAtTrigger = delightScore,
            activeNeighborhood = activeNeighborhood,
            unlockedBadgesCount = unlockedBadgesCount,
            totalWalkedMeters = totalWalkedMeters,
            installedVersion = DEFAULT_INSTALLED_VERSION,
            latestAvailableVersion = LATEST_AVAILABLE_VERSION
        )
    }
}
