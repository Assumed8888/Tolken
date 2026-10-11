package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: SettingsState,
    onSaveSettings: (SettingsState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var maxSpeed by remember { mutableFloatStateOf(settings.maxSpeechRate) }
    var minSpeed by remember { mutableFloatStateOf(settings.minSpeechRate) }
    var clauseLength by remember { mutableFloatStateOf(settings.clauseWordThreshold.toFloat()) }
    var pauseThreshold by remember { mutableFloatStateOf(settings.pauseThresholdMs.toFloat()) }
    var skipThreshold by remember { mutableFloatStateOf(settings.queueSkipThresholdSec) }
    var duckingEnabled by remember { mutableStateOf(settings.audioDuckingEnabled) }
    var btRouting by remember { mutableStateOf(settings.lowLatencyBluetoothRouting) }
    var digitsCap by remember { mutableFloatStateOf(settings.digitsNamesSpeedCap) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Настройки конвейера",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Max Speech Rate
            SettingSlider(
                title = "Максимальная скорость речи",
                valueText = String.format("%.2fx", maxSpeed),
                description = "Максимальная скорость TTS при накоплении очереди",
                value = maxSpeed,
                valueRange = 1.3f..2.5f,
                onValueChange = {
                    maxSpeed = it
                    onSaveSettings(settings.copy(maxSpeechRate = it))
                }
            )

            // 2. Clause Length (4-6 words)
            SettingSlider(
                title = "Длина сегмента фразы",
                valueText = "${clauseLength.toInt()} слов",
                description = "Фиксировать фразу каждые N слов для снижения задержки",
                value = clauseLength,
                valueRange = 3f..8f,
                steps = 4,
                onValueChange = {
                    clauseLength = it
                    onSaveSettings(settings.copy(clauseWordThreshold = it.toInt()))
                }
            )

            // 3. Pause Threshold
            SettingSlider(
                title = "Порог паузы для фиксации",
                valueText = "${pauseThreshold.toInt()} мс",
                description = "Фиксировать фразу при паузе в речи speaker'а от N мс",
                value = pauseThreshold,
                valueRange = 150f..600f,
                onValueChange = {
                    pauseThreshold = it
                    onSaveSettings(settings.copy(pauseThresholdMs = it.toLong()))
                }
            )

            // 4. Queue Skip Threshold (> 4.0s)
            SettingSlider(
                title = "Порог пропуска перегрузки очереди",
                valueText = String.format("%.1fс", skipThreshold),
                description = "Пропускать озвучивание малозначимых фраз, если очередь превышает порог",
                value = skipThreshold,
                valueRange = 2.0f..6.0f,
                onValueChange = {
                    skipThreshold = it
                    onSaveSettings(settings.copy(queueSkipThresholdSec = it))
                }
            )

            // 5. Digits & Names Speed Cap (1.3x)
            SettingSlider(
                title = "Ограничение скорости для цифр и имён",
                valueText = String.format("%.2fx", digitsCap),
                description = "Ограничивает скорость речи для чисел и имён для четкой разборчивости",
                value = digitsCap,
                valueRange = 1.0f..1.5f,
                onValueChange = {
                    digitsCap = it
                    onSaveSettings(settings.copy(digitsNamesSpeedCap = it))
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = DividerDefaults.color
            )

            // 6. Audio Ducking Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Приглушение звука (Ducking)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Приглушать сторонние звуки во время синтеза русской речи",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = duckingEnabled,
                    onCheckedChange = {
                        duckingEnabled = it
                        onSaveSettings(settings.copy(audioDuckingEnabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Bluetooth Low Latency Routing Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Маршрутизация Bluetooth (Низкая задержка)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Направлять русскую речь напрямую в гарнитуру через коммуникационный профиль низкой задержки",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = btRouting,
                    onCheckedChange = {
                        btRouting = it
                        onSaveSettings(settings.copy(lowLatencyBluetoothRouting = it))
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = DividerDefaults.color
            )

            // 8. Voice Isolation / Near-Field Own Voice Filter
            Text(
                text = "Акустическая изоляция (Телефон в кармане)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Разделение речи собеседника (дальнее поле) и вашего голоса (близко к микрофону гарнитуры)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            var ownVoiceFiltering by remember { mutableStateOf(settings.ownVoiceFilteringEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Отсекать собственный голос",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Игнорировать громкие фразы в упор к гарнитуре",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ownVoiceFiltering,
                    onCheckedChange = {
                        ownVoiceFiltering = it
                        onSaveSettings(settings.copy(ownVoiceFilteringEnabled = it))
                    }
                )
            }

            var ownVoiceThreshold by remember { mutableFloatStateOf(settings.ownVoiceRmsThresholdDb) }
            SettingSlider(
                title = "Порог громкости своего голоса",
                valueText = String.format("%.1f dB", ownVoiceThreshold),
                description = "Звук выше этого уровня считается речью владельца и не переводится",
                value = ownVoiceThreshold,
                valueRange = 3.0f..12.0f,
                onValueChange = {
                    ownVoiceThreshold = it
                    onSaveSettings(settings.copy(ownVoiceRmsThresholdDb = it))
                }
            )

            var echoGate by remember { mutableStateOf(settings.echoGateTtsEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Эхо-гейт при синтезе речи",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Блокировать микрофон от самопрослушивания русского перевода из наушника",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = echoGate,
                    onCheckedChange = {
                        echoGate = it
                        onSaveSettings(settings.copy(echoGateTtsEnabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            var cyrillicFilter by remember { mutableStateOf(settings.cyrillicSelfFilterEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Фильтр русской речи владельца",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Отсекать фразы на русском языке при ваших ответах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = cyrillicFilter,
                    onCheckedChange = {
                        cyrillicFilter = it
                        onSaveSettings(settings.copy(cyrillicSelfFilterEnabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
