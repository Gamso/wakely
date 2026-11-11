package com.gamso.wakely.presentation.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.usecase.AlarmKeywords
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.RingStyleSection
import com.gamso.wakely.presentation.common.SnoozeChips
import com.gamso.wakely.presentation.sound.SoundPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventAlarmEditScreen(viewModel: EventAlarmEditViewModel, state: EventAlarmEditUiState, onClose: () -> Unit) {
    LaunchedEffect(state.done) { if (state.done) onClose() }
    var pickSound by remember { mutableStateOf(false) }
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
            EventSelection(state, viewModel)

            HorizontalDivider()
            UpcomingRings(state)

            HorizontalDivider()
            Text(stringResource(R.string.ringtone), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickSound = true }, modifier = Modifier.fillMaxWidth()) {
                Text((rule.sound ?: viewModel.defaultSound).name)
            }
            Text(stringResource(R.string.snooze_title), style = MaterialTheme.typography.titleMedium)
            SnoozeChips(rule.snoozeMinutes, viewModel::setSnoozeMinutes)

            HorizontalDivider()
            RingStyleSection(rule.volumeRampSeconds, rule.vibrate, viewModel::setVolumeRamp, viewModel::setVibrate)

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

/** The calendars the rule watches and, among their keywords, the ones it is for. */
@Composable
private fun EventSelection(state: EventAlarmEditUiState, viewModel: EventAlarmEditViewModel) {
    val rule = state.alarm
    Text(stringResource(R.string.calendars), style = MaterialTheme.typography.labelLarge)
    // The calendars of the "Events" category are the pool the rule chooses from.
    val pool = state.calendars.filter { it.key in state.eventCategoryKeys }
    if (pool.isEmpty()) {
        Text(stringResource(R.string.calendars_pool_empty), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(
            stringResource(R.string.calendars_hint, pool.joinToString { it.name }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    pool.forEach { cal ->
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
    KeywordDropdown(state, viewModel)
}

/**
 * The keywords of the watched calendars in a drop-down list, each with a check box. None ticked means every event the
 * calendars accept. The list stays open while ticking, so several can be chosen in one go.
 */
@Composable
private fun KeywordDropdown(state: EventAlarmEditUiState, viewModel: EventAlarmEditViewModel) {
    if (state.keywordOptions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val ticked = state.keywordOptions.filter { AlarmKeywords.isSelected(state.alarm.keywords, it) }
    Text(stringResource(R.string.keywords_from_calendars), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.keywords_from_calendars_help), style = MaterialTheme.typography.bodySmall)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                ticked.joinToString(", ").ifEmpty { stringResource(R.string.keywords_all_events) },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, Modifier.rotate(90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.keywordOptions.forEach { word ->
                DropdownMenuItem(
                    text = { Text(word) },
                    leadingIcon = {
                        Checkbox(checked = AlarmKeywords.isSelected(state.alarm.keywords, word), onCheckedChange = null)
                    },
                    onClick = { viewModel.toggleKeyword(word) },
                )
            }
        }
    }
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
