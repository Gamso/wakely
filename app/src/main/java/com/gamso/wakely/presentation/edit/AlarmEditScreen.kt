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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
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
                title = { Text(stringResource(if (state.isNew) R.string.new_alarm else R.string.edit_alarm)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = {
                        viewModel.save()
                    }, enabled = state.loaded) { Text(stringResource(R.string.action_save)) }
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
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Format.WEEK.forEach { day ->
                    DayToggle(Format.dayLetter(day), day in alarm.days) { viewModel.toggleDay(day) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {
                    viewModel.setDays(Alarm.WORKDAYS)
                }, label = { Text(stringResource(R.string.chip_weekdays)) })
                AssistChip(onClick = {
                    viewModel.setDays(DayOfWeek.entries.toSet())
                }, label = { Text(stringResource(R.string.chip_every_day)) })
                AssistChip(onClick = {
                    viewModel.setDays(emptySet())
                }, label = { Text(stringResource(R.string.chip_once)) })
            }
            Text(Format.daysSummary(alarm.days), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text(stringResource(R.string.days_off_section), style = MaterialTheme.typography.titleMedium)
            ModeOption(stringResource(R.string.mode_skip), alarm.dayOffMode == DayOffMode.SKIP) {
                viewModel.setDayOffMode(DayOffMode.SKIP)
            }
            ModeOption(stringResource(R.string.mode_other_time), alarm.dayOffMode == DayOffMode.OTHER_TIME) {
                viewModel.setDayOffMode(DayOffMode.OTHER_TIME)
            }
            if (alarm.dayOffMode == DayOffMode.OTHER_TIME) {
                OutlinedButton(onClick = { pickOff = true }, modifier = Modifier.padding(start = 48.dp)) {
                    Text(stringResource(R.string.mode_other_time_button, Format.time(alarm.dayOffTime)))
                }
            }
            ModeOption(stringResource(R.string.mode_ignore), alarm.dayOffMode == DayOffMode.IGNORE) {
                viewModel.setDayOffMode(DayOffMode.IGNORE)
            }

            HorizontalDivider()
            Text(stringResource(R.string.ringtone), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickSound = true }, modifier = Modifier.fillMaxWidth()) {
                Text((alarm.sound ?: viewModel.defaultSound).name)
            }

            HorizontalDivider()
            Text(stringResource(R.string.snooze_title), style = MaterialTheme.typography.titleMedium)
            SnoozeChips(alarm.snoozeMinutes, viewModel::setSnoozeMinutes)

            if (!state.isNew) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_alarm)) }
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
