package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AppEventEntity
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.RadarTeal
import com.example.ui.theme.SignalCoral
import com.example.util.SmartAutoPopupProposal
import com.example.util.SmartEngagementTelemetryState
import com.example.util.SmartPromptType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial, non-intrusive modal dialog for Smart Auto-Popup suggestions:
 * - RATE_APP (with 1–5 star sentiment gate: 4–5 stars -> Google Play rating; 1–3 stars -> private feedback note)
 * - SHARE_APP (with live walk telemetry share preview & native Android chooser)
 * - APP_UPDATE (with v1.4.0 -> v1.5.0 changelog & 1-tap apply/skip)
 *
 * Includes transparent anti-annoyance badges showing WHY the popup was triggered now,
 * plus 1-tap Exponential Backoff Snooze ("Remind Me Later") and Permanent Opt-Out ("Never Ask Again").
 */
@Composable
fun SmartAutoPopupDialog(
    proposal: SmartAutoPopupProposal,
    onAcceptPrimaryAction: (customFeedbackOrShareNote: String?) -> Unit,
    onSnoozeWithBackoff: () -> Unit,
    onOptOutForever: () -> Unit
) {
    var selectedStars by rememberSaveable(proposal.promptType) { mutableIntStateOf(5) }
    var privateFeedbackText by rememberSaveable(proposal.promptType) { mutableStateOf("") }

    val accentColor = when (proposal.promptType) {
        SmartPromptType.RATE_APP -> BrassAmber
        SmartPromptType.SHARE_APP -> RadarTeal
        SmartPromptType.APP_UPDATE -> BrassAmber
    }

    val headerIcon: ImageVector = when (proposal.promptType) {
        SmartPromptType.RATE_APP -> Icons.Default.Star
        SmartPromptType.SHARE_APP -> Icons.Default.Share
        SmartPromptType.APP_UPDATE -> Icons.Default.SystemUpdateAlt
    }

    Dialog(
        onDismissRequest = onSnoozeWithBackoff,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .testTag("smart_auto_popup_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Badge Row + Close (Snooze) Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = proposal.promptType.badgeTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onSnoozeWithBackoff,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("smart_popup_close_snooze_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Snooze suggestion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Headline & Context Subtitle
                Text(
                    text = proposal.contextHeadline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = proposal.contextSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Transparent "Why You're Seeing This Now" Anti-Annoyance Telemetry Pill
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = RadarTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.smart_popup_timing_guard_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadarTeal,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Trigger: ${proposal.triggerEvent} • Delight ${proposal.delightScoreAtTrigger}/100 • Max 1/session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Prompt-Specific Interactive Body
                when (proposal.promptType) {
                    SmartPromptType.RATE_APP -> {
                        Surface(
                            color = BrassAmber.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BrassAmber.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.smart_rate_star_prompt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (star in 1..5) {
                                        val isFilled = star <= selectedStars
                                        IconButton(
                                            onClick = { selectedStars = star },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("smart_rate_star_$star")
                                        ) {
                                            Icon(
                                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "$star Stars",
                                                tint = if (isFilled) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                }

                                val sentimentCaption = when (selectedStars) {
                                    5 -> "5/5 • Studio Masterpiece! We'll open Google Play Review & never ask again."
                                    4 -> "4/5 • Great Frequency! Share your review on Google Play."
                                    else -> "$selectedStars/5 • Thank you for your honesty. Tell us what to improve directly below:"
                                }
                                Text(
                                    text = sentimentCaption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedStars >= 4) BrassAmber else SignalCoral,
                                    fontWeight = FontWeight.Medium
                                )

                                AnimatedVisibility(visible = selectedStars <= 3) {
                                    OutlinedTextField(
                                        value = privateFeedbackText,
                                        onValueChange = { privateFeedbackText = it },
                                        label = { Text("What can we improve in WanderCast?") },
                                        placeholder = { Text("e.g., voice speed, map tiles, neighborhood depth...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("smart_rate_private_feedback_input"),
                                        minLines = 2,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }

                    SmartPromptType.SHARE_APP -> {
                        Surface(
                            color = RadarTeal.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, RadarTeal.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = RadarTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "WALK TELEMETRY DISPATCH CARD",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RadarTeal,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = proposal.sharePreviewMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    SmartPromptType.APP_UPDATE -> {
                        Surface(
                            color = BrassAmber.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BrassAmber.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "WHAT'S NEW IN VERSION ${proposal.latestAvailableVersion}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BrassAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = RadarTeal.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "v${proposal.installedVersion} → v${proposal.latestAvailableVersion}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RadarTeal,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                proposal.releaseNotes.forEach { note ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = RadarTeal,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(15.dp)
                                        )
                                        Text(
                                            text = note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Primary CTA Button
                val primaryButtonLabel = when (proposal.promptType) {
                    SmartPromptType.RATE_APP -> if (selectedStars >= 4) {
                        "${stringResource(R.string.smart_rate_google_play_btn)} ($selectedStars★)"
                    } else {
                        "${stringResource(R.string.smart_rate_private_feedback_btn)} ($selectedStars★)"
                    }
                    SmartPromptType.SHARE_APP -> stringResource(R.string.smart_share_walk_card_btn)
                    SmartPromptType.APP_UPDATE -> "${stringResource(R.string.smart_update_install_btn)} v${proposal.latestAvailableVersion}"
                }

                Button(
                    onClick = {
                        val payload = when (proposal.promptType) {
                            SmartPromptType.RATE_APP -> if (selectedStars >= 4) {
                                "STORE_RATING_${selectedStars}_STARS"
                            } else {
                                "PRIVATE_FEEDBACK_${selectedStars}_STARS:${privateFeedbackText.ifBlank { "General improvement note" }}"
                            }
                            SmartPromptType.SHARE_APP -> proposal.sharePreviewMessage
                            SmartPromptType.APP_UPDATE -> "APPLY_UPDATE_${proposal.latestAvailableVersion}"
                        }
                        onAcceptPrimaryAction(payload)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("smart_popup_primary_accept_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF0E131A)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = primaryButtonLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Secondary Snooze & Permanent Opt-Out Row
                val snoozeLabel = stringResource(R.string.smart_popup_snooze_later_btn)
                val optOutLabel = stringResource(R.string.smart_popup_never_ask_btn)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onSnoozeWithBackoff,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("smart_popup_snooze_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = snoozeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = onOptOutForever,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("smart_popup_opt_out_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = optOutLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Smart Engagement & App Event Telemetry Card embedded in the Rewards & Telemetry tab.
 * Lets users inspect:
 * - Live Delight Score (0–100) and algorithm decision rationale
 * - Anti-Annoyance Guardrails (45s Cold-Start Protection, 1-Per-Session Cap, Exponential Backoff Cooldowns, Lifetime Caps)
 * - 1-tap Simulation of a Peak Delight Auto-Trigger or Manual Preview of Rate / Share / Update popups
 * - Live Room DB `app_events` Event Stream with timestamps, categories, and delight deltas
 */
@Composable
fun SmartEngagementTelemetryCard(
    telemetryState: SmartEngagementTelemetryState,
    recentAppEvents: List<AppEventEntity>,
    onSimulatePeakDelightTrigger: () -> Unit,
    onPreviewPopup: (SmartPromptType) -> Unit,
    onResetCooldowns: () -> Unit,
    onClearEvents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedDelightProgress by animateFloatAsState(
        targetValue = (telemetryState.delightScore / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "delightScoreProgress"
    )

    val delightColor by animateColorAsState(
        targetValue = when {
            telemetryState.delightScore >= 65 -> BrassAmber
            telemetryState.delightScore >= 40 -> RadarTeal
            else -> SignalCoral
        },
        label = "delightColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_engagement_telemetry_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, RadarTeal.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            RadarTeal.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(RadarTeal.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = RadarTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.smart_telemetry_card_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = RadarTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.smart_telemetry_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Surface(
                    color = delightColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, delightColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Delight ${telemetryState.delightScore}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = delightColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 2. Delight Score Progress Meter + Threshold Markers
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { animatedDelightProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = delightColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Session #${telemetryState.sessionCount} • ${telemetryState.lifetimePositiveActionsCount} positive walk actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Thresholds: Update ≥25 • Share ≥48 • Rate ≥65",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 3. Anti-Annoyance Guardrails Status Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuardrailStatusChip(
                    icon = Icons.Default.Security,
                    label = if (telemetryState.isColdStartProtected) {
                        "Startup Guard: ${telemetryState.coldStartRemainingSeconds}s Blocked"
                    } else {
                        "Startup Guard: Clear (>45s)"
                    },
                    accentColor = if (telemetryState.isColdStartProtected) BrassAmber else RadarTeal
                )
                GuardrailStatusChip(
                    icon = Icons.Default.Block,
                    label = "Session Cap: ${telemetryState.promptsShownThisSession}/${telemetryState.maxPromptsPerSession} Shown",
                    accentColor = if (telemetryState.promptsShownThisSession >= telemetryState.maxPromptsPerSession) {
                        SignalCoral
                    } else {
                        RadarTeal
                    }
                )
                GuardrailStatusChip(
                    icon = Icons.Default.Star,
                    label = if (telemetryState.rateCompletedOrOptedOut) {
                        "Rate: Done/Opt-Out"
                    } else {
                        "Rate: ${telemetryState.rateImpressionsCount}/${telemetryState.rateMaxLifetimeImpressions} (14d×2ⁿ)"
                    },
                    accentColor = BrassAmber
                )
                GuardrailStatusChip(
                    icon = Icons.Default.Share,
                    label = if (telemetryState.shareOptedOut) {
                        "Share: Opted Out"
                    } else {
                        "Share: ${telemetryState.shareImpressionsCount}/${telemetryState.shareMaxLifetimeImpressions} (5d×2ⁿ)"
                    },
                    accentColor = RadarTeal
                )
                GuardrailStatusChip(
                    icon = Icons.Default.SystemUpdateAlt,
                    label = if (telemetryState.updateAppliedInSession) {
                        "Update: v${telemetryState.updateAvailableVersion} Installed"
                    } else if (telemetryState.updateSkippedVersion == telemetryState.updateAvailableVersion) {
                        "Update: v${telemetryState.updateAvailableVersion} Skipped"
                    } else {
                        "Update: v${telemetryState.updateInstalledVersion}→v${telemetryState.updateAvailableVersion}"
                    },
                    accentColor = BrassAmber
                )
            }

            // 4. Algorithm Decision Rationale Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = BrassAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LATEST ALGORITHM DECISION",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = telemetryState.lastAlgorithmDecisionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 5. Interactive Algorithm Test & Popup Preview Controls
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSimulatePeakDelightTrigger,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("simulate_peak_delight_auto_popup_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrassAmber,
                            contentColor = Color(0xFF0F141C)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.smart_simulate_peak_delight_btn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onResetCooldowns,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("reset_smart_cooldowns_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset cooldowns",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.smart_reset_caps_btn),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Direct Preview Buttons for each of the 3 Smart Popups
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onPreviewPopup(SmartPromptType.RATE_APP) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("preview_rate_app_popup_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = BrassAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rate Popup",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    FilledTonalButton(
                        onClick = { onPreviewPopup(SmartPromptType.SHARE_APP) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("preview_share_app_popup_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = RadarTeal,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Share Popup",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    FilledTonalButton(
                        onClick = { onPreviewPopup(SmartPromptType.APP_UPDATE) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("preview_update_app_popup_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = BrassAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Update Popup",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 6. Live Room DB App Event Logging Stream (`app_events` table)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = RadarTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ROOM DB EVENT LOG (${recentAppEvents.size} TRACKED EVENTS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadarTeal,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (recentAppEvents.isNotEmpty()) {
                    TextButton(
                        onClick = onClearEvents,
                        modifier = Modifier.testTag("clear_logged_app_events_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear Log",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            val timeFormatter = remember {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                recentAppEvents.take(12).forEach { event ->
                    val deltaColor = when {
                        event.delightScoreDelta >= 15 -> BrassAmber
                        event.delightScoreDelta > 0 -> RadarTeal
                        event.delightScoreDelta < 0 -> SignalCoral
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val deltaPrefix = if (event.delightScoreDelta > 0) "+${event.delightScoreDelta}" else "${event.delightScoreDelta}"

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = event.eventType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "• ${event.screenName} • ${timeFormatter.format(Date(event.timestamp))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = event.metadataSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                color = deltaColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "$deltaPrefix pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = deltaColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardrailStatusChip(
    icon: ImageVector,
    label: String,
    accentColor: Color
) {
    Surface(
        color = accentColor.copy(alpha = 0.13f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
