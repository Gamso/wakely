package com.gamso.wakely.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.SnoozeChips
import com.gamso.wakely.presentation.common.TimePickerDialog
import com.gamso.wakely.presentation.sound.SoundPickerDialog
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(viewModel: AlarmEditViewModel, state: AlarmEditUiState, onClose: () -> Unit) {
    LaunchedEffect(state.done) { if (state.done) onClose() }
    var pickMain by remember { mutableStateOf(false) }
    var pickOff by remember { mutableStateOf(false) }
    var pickSound by remember { mutableStateOf(false) }
    val alarm = state.alarm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Nouveau réveil" else "Modifier le réveil") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Annuler") } },
                actions = {
                    TextButton(onClick = { viewModel.save() }, enabled = state.loaded) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                Format.time(alarm.time),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { pickMain = true }.padding(8.dp),
            )

            OutlinedTextField(
                value = alarm.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Libellé") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Répétition", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Format.WEEK.forEach { day ->
                    DayToggle(Format.dayLetter(day), day in alarm.days) { viewModel.toggleDay(day) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { viewModel.setDays(Alarm.WORKDAYS) }, label = { Text("Semaine") })
                AssistChip(onClick = {
                    viewModel.setDays(DayOfWeek.entries.toSet())
                }, label = { Text("Tous les jours") })
                AssistChip(onClick = { viewModel.setDays(emptySet()) }, label = { Text("Une fois") })
            }
            Text(Format.daysSummary(alarm.days), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Jours fériés et congés (selon l'agenda)", style = MaterialTheme.typography.titleMedium)
            ModeOption("Ne pas sonner", alarm.dayOffMode == DayOffMode.SKIP) {
                viewModel.setDayOffMode(DayOffMode.SKIP)
            }
            ModeOption("Sonner à une autre heure", alarm.dayOffMode == DayOffMode.OTHER_TIME) {
                viewModel.setDayOffMode(DayOffMode.OTHER_TIME)
            }
            if (alarm.dayOffMode == DayOffMode.OTHER_TIME) {
                OutlinedButton(onClick = { pickOff = true }, modifier = Modifier.padding(start = 48.dp)) {
                    Text("Heure les jours non travaillés : ${Format.time(alarm.dayOffTime)}")
                }
            }
            ModeOption("Sonner normalement (ignorer l'agenda)", alarm.dayOffMode == DayOffMode.IGNORE) {
                viewModel.setDayOffMode(DayOffMode.IGNORE)
            }

            HorizontalDivider()
            Text("Sonnerie", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickSound = true }, modifier = Modifier.fillMaxWidth()) {
                Text((alarm.sound ?: viewModel.defaultSound).name)
            }

            HorizontalDivider()
            Text("Répéter (snooze)", style = MaterialTheme.typography.titleMedium)
            SnoozeChips(alarm.snoozeMinutes, viewModel::setSnoozeMinutes)

            if (!state.isNew) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Supprimer ce réveil") }
            }
        }
    }

    if (pickSound) {
        SoundPickerDialog(
            initialUri = alarm.sound?.uri,
            onDismiss = { pickSound = false },
            onConfirm = viewModel::setSound,
        )
    }
    if (pickMain) TimePickerDialog(alarm.time, { pickMain = false }) { viewModel.setTime(it) }
    if (pickOff) TimePickerDialog(alarm.dayOffTime, { pickOff = false }) { viewModel.setDayOffTime(it) }
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .background(if (selected) colors.primary else colors.surfaceVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (selected) colors.onPrimary else colors.onSurfaceVariant)
    }
}

@Composable
private fun ModeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(text)
    }
}
