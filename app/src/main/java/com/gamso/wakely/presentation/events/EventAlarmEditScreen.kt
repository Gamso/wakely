package com.gamso.wakely.presentation.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.SnoozeChips
import com.gamso.wakely.presentation.sound.SoundPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventAlarmEditScreen(viewModel: EventAlarmEditViewModel, state: EventAlarmEditUiState, onClose: () -> Unit) {
    LaunchedEffect(state.done) { if (state.done) onClose() }
    var pickSound by remember { mutableStateOf(false) }
    var pickEvent by remember { mutableStateOf(false) }
    val rule = state.alarm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_event_alarm else R.string.edit_rule)) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = rule.label,
                onValueChange = viewModel::setLabel,
                label = { Text(stringResource(R.string.label_optional)) },
                placeholder = { Text(stringResource(R.string.label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.how_long_before), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventAlarm.MINUTES_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = rule.minutesBefore == minutes,
                        onClick = { viewModel.setMinutesBefore(minutes) },
                        label = { Text(Format.duration(minutes)) },
                    )
                }
            }
            MinutesField(rule.minutesBefore, viewModel::setMinutesBefore)

            HorizontalDivider()
            Text(stringResource(R.string.which_events), style = MaterialTheme.typography.titleMedium)
            EventSelection(state, viewModel, onPickEvent = { pickEvent = true })

            HorizontalDivider()
            UpcomingRings(state)

            HorizontalDivider()
            Text(stringResource(R.string.ringtone), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickSound = true }, modifier = Modifier.fillMaxWidth()) {
                Text((rule.sound ?: viewModel.defaultSound).name)
            }
            Text(stringResource(R.string.snooze_title), style = MaterialTheme.typography.titleMedium)
            SnoozeChips(rule.snoozeMinutes, viewModel::setSnoozeMinutes)

            if (!state.isNew) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_rule)) }
            }
        }
    }

    if (pickSound) {
        SoundPickerDialog(
            initialUri = rule.sound?.uri,
            onDismiss = { pickSound = false },
            onConfirm = viewModel::setSound,
        )
    }
    if (pickEvent) {
        EventPickerDialog(
            state = state,
            onPick = {
                viewModel.setSingleEvent(it)
                pickEvent = false
            },
            onDismiss = { pickEvent = false },
        )
    }
}

@Composable
private fun MinutesField(minutes: Int, onChange: (Int) -> Unit) {
    var text by remember(minutes) { mutableStateOf(minutes.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(stringResource(R.string.minutes_field)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.width(220.dp),
    )
}

@Composable
private fun dateTime(at: java.time.LocalDateTime) =
    stringResource(R.string.at_time, Format.dayDate(at.toLocalDate()), Format.time(at.toLocalTime()))

/** Either the single bound event, or the calendar / keyword filters. */
@Composable
private fun EventSelection(state: EventAlarmEditUiState, viewModel: EventAlarmEditViewModel, onPickEvent: () -> Unit) {
    val rule = state.alarm
    val single = rule.singleEvent
    if (single != null) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.specific_event), style = MaterialTheme.typography.labelMedium)
                    Text(single.title, fontWeight = FontWeight.SemiBold)
                    Text(dateTime(single.start))
                }
                TextButton(onClick = {
                    viewModel.setSingleEvent(null)
                }) { Text(stringResource(R.string.action_remove)) }
            }
        }
        return
    }
    Text(stringResource(R.string.calendars), style = MaterialTheme.typography.labelLarge)
    val fallback = state.eventCategoryKeys.joinToString {
        state.calendarName(it)
    }.ifBlank { stringResource(R.string.none_lower) }
    Text(
        stringResource(R.string.calendars_hint, fallback),
        style = MaterialTheme.typography.bodySmall,
    )
    state.calendars.forEach { cal ->
        Row(
            Modifier.fillMaxWidth().toggleable(cal.key in rule.sourceKeys, role = Role.Checkbox) {
                viewModel.toggleSource(cal.key)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = cal.key in rule.sourceKeys, onCheckedChange = null, modifier = Modifier.padding(8.dp))
            ColorDot(cal.color)
            Column(Modifier.padding(start = 12.dp)) {
                Text(cal.name)
                Text(cal.account, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    OutlinedTextField(
        value = rule.keywords,
        onValueChange = viewModel::setKeywords,
        label = { Text(stringResource(R.string.keywords_optional)) },
        supportingText = { Text(stringResource(R.string.keywords_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.first_event_only), modifier = Modifier.weight(1f))
        Switch(checked = rule.firstEventOfDayOnly, onCheckedChange = viewModel::setFirstEventOfDayOnly)
    }
    OutlinedButton(onClick = onPickEvent) { Text(stringResource(R.string.pick_specific_event)) }
}

@Composable
private fun UpcomingRings(state: EventAlarmEditUiState) {
    Text(stringResource(R.string.upcoming_rings), style = MaterialTheme.typography.titleMedium)
    if (state.upcoming.isEmpty()) {
        Text(stringResource(R.string.no_matching_events), style = MaterialTheme.typography.bodyMedium)
    }
    state.upcoming.forEach { ring ->
        Row {
            Text(
                stringResource(
                    R.string.fmt_space,
                    Format.dayDate(ring.at.toLocalDate()),
                    Format.time(ring.at.toLocalTime()),
                ),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(150.dp),
            )
            Text(stringResource(R.string.fmt_paren, ring.event.title, Format.time(ring.event.start.toLocalTime())))
        }
    }
}

@Composable
private fun EventPickerDialog(state: EventAlarmEditUiState, onPick: (CalendarEvent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.pick_event_title)) },
        text = {
            if (state.candidates.isEmpty()) {
                Text(stringResource(R.string.no_events_in_calendars))
            } else {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    state.candidates.forEach { event ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(event) }.padding(vertical = 10.dp)) {
                            Text(
                                event.title.ifBlank {
                                    stringResource(R.string.untitled)
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(
                                    R.string.fmt_dot,
                                    dateTime(event.start),
                                    state.calendarName(event.sourceKey),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}
