package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.NarratorState
import com.example.data.DocumentaryLens
import com.example.location.LocationBlockState
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.RadarTeal
import com.example.ui.theme.SignalCoral
import java.util.Locale
import kotlin.math.sin

@Composable
fun HeroBroadcastHeader(
    locationState: LocationBlockState,
    isSpeaking: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    currentLanguageTag: String = "en",
    onOpenLanguagePicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "on_air_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_broadcast_header"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_radio_walk_1795177312192),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Editorial atmospheric gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MidnightSlate.copy(alpha = 0.72f),
                                MidnightSlate.copy(alpha = 0.85f),
                                MidnightSlate.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Station Call Sign + GPS / Stepper Status + 20-Language Picker + Theme Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Live ON AIR pill
                        Surface(
                            color = if (isSpeaking) SignalCoral.copy(alpha = 0.24f) else BrassAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isSpeaking) SignalCoral else BrassAmber.copy(alpha = 0.7f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSpeaking) SignalCoral.copy(alpha = pulseAlpha)
                                            else BrassAmber
                                        )
                                )
                                Text(
                                    text = if (isSpeaking) {
                                        stringResource(R.string.header_on_air_live)
                                    } else {
                                        stringResource(R.string.station_frequency)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // GPS / Foreground Service vs Field Walk Stepper Badge
                        Surface(
                            color = RadarTeal.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, RadarTeal.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (locationState.isForegroundServiceActive || (locationState.isLiveGpsActive && !locationState.isFieldStepperMode)) {
                                        Icons.Default.GpsFixed
                                    } else {
                                        Icons.Default.Tune
                                    },
                                    contentDescription = null,
                                    tint = RadarTeal,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = when {
                                        locationState.isForegroundServiceActive -> "FGS • GPS"
                                        locationState.isLiveGpsActive && !locationState.isFieldStepperMode -> "GPS"
                                        else -> "STEPPER"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RadarTeal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 20-Language Switcher Pill
                        Surface(
                            color = BrassAmber.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, BrassAmber.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenLanguagePicker() }
                                .testTag("open_language_picker_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = stringResource(R.string.header_language_btn),
                                    tint = BrassAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = currentLanguageTag.take(2).uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(40.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = stringResource(R.string.app_name),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Section: Current Detected Block & Telemetry
                Column {
                    Text(
                        text = stringResource(R.string.header_current_block),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassAmber
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = locationState.streetBlockLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = locationState.neighborhoodName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "LAT %.4f  LNG %.4f",
                                locationState.latitude,
                                locationState.longitude
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = RadarTeal
                        )
                        val remainingMeters = (locationState.triggerThresholdMeters - locationState.distanceFromLastBroadcastMeters).coerceAtLeast(0)
                        Text(
                            text = "${stringResource(R.string.radar_block_progress)}: ${remainingMeters}m",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrassAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentaryLensSelector(
    selectedLens: DocumentaryLens,
    onSelectLens: (DocumentaryLens) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.lens_section_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = selectedLens.frequencyTag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DocumentaryLens.entries.forEach { lens ->
                val isSelected = lens == selectedLens
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectLens(lens) },
                    label = {
                        Text(
                            text = lens.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("lens_chip_${lens.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun LiveVoiceNarratorDeck(
    narratorState: NarratorState,
    autoNarrateEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onToggleAutoNarrate: (Boolean) -> Unit,
    onCycleVoicePersona: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_voice_narrator_deck"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (narratorState.isSpeaking) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row: Voice status + Persona selector + Auto-Narrate toggle
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
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = if (narratorState.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = when {
                            narratorState.isSpeaking -> stringResource(R.string.tts_narrating)
                            narratorState.isPaused -> stringResource(R.string.tts_paused)
                            else -> stringResource(R.string.tts_standby)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (narratorState.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Voice Persona Switcher Chip
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onCycleVoicePersona() }
                            .testTag("voice_persona_cycle_button")
                    ) {
                        Text(
                            text = narratorState.voicePersona.displayName.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }

                    // Auto-Voice Pill Toggle
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (autoNarrateEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onToggleAutoNarrate(!autoNarrateEnabled) }
                            .testTag("auto_narrate_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (autoNarrateEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = stringResource(R.string.auto_narrate_label),
                                tint = if (autoNarrateEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.auto_narrate_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoNarrateEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Audio Waveform Canvas
            val barColorActive = MaterialTheme.colorScheme.primary
            val barColorIdle = MaterialTheme.colorScheme.outline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                val barCount = 32
                val totalGap = (barCount - 1) * 4.dp.toPx()
                val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(3f)
                val centerY = size.height / 2f

                for (i in 0 until barCount) {
                    val normalizedRatio = i.toFloat() / barCount.toFloat()
                    val isPassed = normalizedRatio <= narratorState.progressFraction
                    val amplitude = if (narratorState.isSpeaking) {
                        val wave = sin(wavePhase + i * 0.45f)
                        (0.32f + 0.68f * kotlin.math.abs(wave)).coerceIn(0.2f, 1f)
                    } else {
                        0.22f + 0.15f * kotlin.math.abs(sin(i * 0.5f))
                    }

                    val barHeight = (size.height * amplitude).coerceAtLeast(6f)
                    val x = i * (barWidth + 4.dp.toPx())
                    drawRoundRect(
                        color = if (narratorState.isSpeaking || isPassed) barColorActive else barColorIdle,
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // Live Teleprompter Subtitle + Word Focus
            if (narratorState.activeSentence.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "“${narratorState.activeSentence}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (narratorState.activeWord.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${stringResource(R.string.tts_narrating)}: ${narratorState.activeWord.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { narratorState.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Transport Controls + Speed Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sentence counter
                Text(
                    text = if (narratorState.totalSentences > 0) {
                        "${(narratorState.currentSentenceIndex + 1).coerceAtMost(narratorState.totalSentences)}/${narratorState.totalSentences}"
                    } else {
                        stringResource(R.string.tts_ready_to_synthesize)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Play / Pause / Skip Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onSkipBackward,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("narrator_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.tts_narrating),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("narrator_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (narratorState.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (narratorState.isSpeaking) {
                                stringResource(R.string.tts_paused)
                            } else {
                                stringResource(R.string.tts_narrating)
                            }
                        )
                    }

                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("narrator_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.tts_narrating),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Speed cycle pill (0.9x -> 1.0x -> 1.2x)
                val nextSpeed = when {
                    narratorState.speechRate < 0.95f -> 1.0f
                    narratorState.speechRate < 1.1f -> 1.2f
                    else -> 0.9f
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChangeSpeed(nextSpeed) }
                        .testTag("narrator_speed_button")
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", narratorState.speechRate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ForegroundLocationServiceCard(
    isForegroundServiceActive: Boolean,
    streetBlockLabel: String,
    remainingMetersToTrigger: Int,
    onToggleForegroundService: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("foreground_location_service_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isForegroundServiceActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isForegroundServiceActive) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isForegroundServiceActive) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = if (isForegroundServiceActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = if (isForegroundServiceActive) {
                            stringResource(R.string.fgs_active_title)
                        } else {
                            stringResource(R.string.fgs_idle_title)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isForegroundServiceActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = if (isForegroundServiceActive) {
                            "$streetBlockLabel • ${remainingMetersToTrigger}m"
                        } else {
                            stringResource(R.string.fgs_idle_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = isForegroundServiceActive,
                onCheckedChange = onToggleForegroundService,
                modifier = Modifier.testTag("foreground_service_switch")
            )
        }
    }
}

