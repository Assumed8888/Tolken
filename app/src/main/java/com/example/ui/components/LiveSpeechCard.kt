package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.SpeakerSource
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.TealAccent

@Composable
fun LiveSpeechCard(
    isListening: Boolean,
    liveDanishPartial: String,
    liveRussianLatest: String,
    micRmsDb: Float,
    speakerSource: SpeakerSource = SpeakerSource.SILENCE,
    ownVoiceThresholdDb: Float = 6.5f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_speech_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with language flags & live audio wave
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isListening -> MaterialTheme.colorScheme.outline
                                    speakerSource == SpeakerSource.USER_NEAR_FIELD -> AmberWarning
                                    speakerSource == SpeakerSource.INTERLOCUTOR_DANISH -> EmeraldSuccess
                                    else -> TealAccent
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Синхронный перевод активен" else "Готов к прослушиванию",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isListening) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Audio level waveform bars
                if (isListening) {
                    RmsWaveform(
                        rmsDb = micRmsDb,
                        isUserSpeaking = speakerSource == SpeakerSource.USER_NEAR_FIELD
                    )
                }
            }

            // Real-Time Acoustic Voice Isolation Badge & Level Meter
            if (isListening) {
                Spacer(modifier = Modifier.height(10.dp))
                VoiceIsolationStatusRow(
                    speakerSource = speakerSource,
                    micRmsDb = micRmsDb,
                    ownVoiceThresholdDb = ownVoiceThresholdDb
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // DANISH INPUT SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (speakerSource == SpeakerSource.USER_NEAR_FIELD)
                            AmberWarning.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Датская речь",
                                tint = if (speakerSource == SpeakerSource.USER_NEAR_FIELD) AmberWarning else TealAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ДАТСКИЙ (da-DK)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (speakerSource == SpeakerSource.USER_NEAR_FIELD) AmberWarning else TealAccent,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (speakerSource == SpeakerSource.USER_NEAR_FIELD) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AmberWarning.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ВАШ ГОЛОС (ПОДАВЛЕНО)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (speakerSource == SpeakerSource.USER_NEAR_FIELD) {
                        Text(
                            text = "Обнаружен голос рядом с гарнитурой: игнорируется, чтобы не засорять перевод.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = AmberWarning
                        )
                    } else if (liveDanishPartial.isNotBlank()) {
                        Text(
                            text = liveDanishPartial,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("live_danish_text")
                        )
                    } else {
                        Text(
                            text = if (isListening) "Taler du dansk? (Слушаю собеседника через гарнитуру...)" else "Нажмите «Начать перевод» для захвата речи",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RUSSIAN OUTPUT SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Русский перевод",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "РУССКИЙ (ru-RU) \u2022 ВЫВОД В НАУШНИКИ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (liveRussianLatest.isNotBlank()) {
                        Text(
                            text = liveRussianLatest,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("live_russian_text")
                        )
                    } else {
                        Text(
                            text = "Синтез русской речи направляется в наушники...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceIsolationStatusRow(
    speakerSource: SpeakerSource,
    micRmsDb: Float,
    ownVoiceThresholdDb: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (speakerSource) {
                SpeakerSource.USER_NEAR_FIELD -> {
                    Text(
                        text = "▲ Голос владельца (подавлен)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                }
                SpeakerSource.INTERLOCUTOR_DANISH -> {
                    Text(
                        text = "● Собеседник (Датский)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
                SpeakerSource.TTS_PLAYBACK_ECHO -> {
                    Text(
                        text = "🎧 Воспроизведение (Эхо-гейт)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = IndigoPrimaryLight
                    )
                }
                SpeakerSource.SILENCE -> {
                    Text(
                        text = "Жду речь собеседника",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Live RMS Level Badge with Threshold Marker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${String.format("%.1f", micRmsDb.coerceAtLeast(0f))} dB",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (micRmsDb >= ownVoiceThresholdDb) AmberWarning else TealAccent
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "(порог ${String.format("%.1f", ownVoiceThresholdDb)})",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RmsWaveform(
    rmsDb: Float,
    isUserSpeaking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val normalizedLevel = ((rmsDb + 2.0f) / 12.0f).coerceIn(0.15f, 1.0f)
    val barColor = if (isUserSpeaking) AmberWarning else TealAccent

    Canvas(
        modifier = Modifier
            .width(44.dp)
            .height(18.dp)
    ) {
        val barCount = 5
        val barWidth = 4.dp.toPx()
        val spacing = 4.dp.toPx()
        val canvasHeight = size.height

        for (i in 0 until barCount) {
            val factor = when (i) {
                0, 4 -> 0.45f * normalizedLevel
                1, 3 -> 0.75f * normalizedLevel * pulse
                else -> 1.0f * normalizedLevel * (0.8f + pulse * 0.2f)
            }
            val barHeight = (canvasHeight * factor).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = (canvasHeight - barHeight) / 2

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}
