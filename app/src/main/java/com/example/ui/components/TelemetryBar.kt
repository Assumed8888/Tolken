package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PipelineMetrics
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TealAccent

@Composable
fun TelemetryBar(
    metrics: PipelineMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_bar"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Adaptive Speed Rate
            TelemetryItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "TTS Rate",
                        tint = when {
                            metrics.currentRate > 1.7f -> CrimsonError
                            metrics.currentRate > 1.4f -> AmberWarning
                            else -> TealAccent
                        },
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Скорость TTS",
                value = String.format("%.2fx", metrics.currentRate),
                subtext = when {
                    metrics.currentRate > 1.7f -> "Догон"
                    metrics.currentRate > 1.35f -> "Адаптив"
                    else -> "Оптимально"
                },
                subtextColor = when {
                    metrics.currentRate > 1.7f -> CrimsonError
                    metrics.currentRate > 1.4f -> AmberWarning
                    else -> EmeraldSuccess
                }
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )

            // 2. Playback Queue Length
            val queueColor = when {
                metrics.queueDurationSec >= 3.5f -> CrimsonError
                metrics.queueDurationSec >= 1.5f -> AmberWarning
                else -> EmeraldSuccess
            }
            TelemetryItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Очередь воспроизведения",
                        tint = queueColor,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Очередь",
                value = String.format("%.1fс", metrics.queueDurationSec),
                subtext = "${metrics.queueClausesCount} фраз",
                subtextColor = queueColor
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )

            // 3. Latency (Median & p90)
            TelemetryItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.AvTimer,
                        contentDescription = "Задержка конвейера",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Задержка",
                value = if (metrics.medianLatencyMs > 0) "${metrics.medianLatencyMs}мс" else "--",
                subtext = if (metrics.p90LatencyMs > 0) "p90: ${metrics.p90LatencyMs}мс" else "p90: --",
                subtextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    subtext: String,
    subtextColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = subtextColor,
            fontSize = 10.sp
        )
    }
}
