package com.gamso.wakely.presentation.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.dayOffReasonText
import java.time.LocalDateTime

private const val MAX_SKIPPED_SHOWN = 3

/** System warnings computed by the activity (Android permissions). */
data class SystemWarnings(val notificationsDenied: Boolean = false, val fullScreenDenied: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    warnings: SystemWarnings,
    onAdd: () -> Unit,
    onAddEventAlarm: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditEventAlarm: (Long) -> Unit,
    onToggle: (AlarmItemUi, Boolean) -> Unit,
    onToggleEventAlarm: (EventAlarmItemUi, Boolean) -> Unit,
    onSkip: (RingKey) -> Unit,
    onUndoSkip: (RingKey) -> Unit,
    onOpenCalendars: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarms_title)) },
                actions = {
                    IconButton(onClick = onOpenCalendars) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            var menu by remember { mutableStateOf(false) }
            Box {
                ExtendedFloatingActionButton(onClick = { menu = true }) { Text(stringResource(R.string.new_button)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_alarm)) }, onClick = {
                        menu = false
                        onAdd()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_event_alarm)) }, onClick = {
                        menu = false
                        onAddEventAlarm()
                    })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loading) {
                if (!state.hasCalendarPermission) {
                    item {
                        Banner(
                            stringResource(R.string.banner_calendar_permission),
                            stringResource(R.string.action_allow),
                            onRequestCalendarPermission,
                        )
                    }
                } else if (!state.hasCalendarSelected) {
                    item {
                        Banner(
                            stringResource(R.string.banner_choose_calendars),
                            stringResource(R.string.action_choose),
                            onOpenCalendars,
                        )
                    }
                }
                if (!state.canScheduleExactAlarms) {
                    item {
                        Banner(
                            stringResource(R.string.banner_exact_alarms),
                            stringResource(R.string.action_enable),
                            onOpenExactAlarmSettings,
                        )
                    }
                }
                if (warnings.notificationsDenied) {
                    item {
                        Banner(
                            stringResource(R.string.banner_notifications),
                            stringResource(R.string.action_allow),
                            onRequestNotifications,
                        )
                    }
                }
                if (warnings.fullScreenDenied) {
                    item {
                        Banner(
                            stringResource(R.string.banner_full_screen),
                            stringResource(R.string.action_allow),
                            onOpenFullScreenSettings,
                        )
                    }
                }
                if (state.items.isEmpty() && state.eventItems.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.empty_alarms),
                            modifier = Modifier.padding(top = 32.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(state.items, key = { "alarm-${it.alarm.id}" }) { item ->
                val key = RingKey(RingKind.ALARM, item.alarm.id)
                AlarmCard(
                    item,
                    showSkip = state.showSkipButtons,
                    onClick = { onEdit(item.alarm.id) },
                    onToggle = { onToggle(item, it) },
                    onSkip = { onSkip(key) },
                    onUndoSkip = { onUndoSkip(key) },
                )
            }
            if (state.eventItems.isNotEmpty()) {
                item(key = "events-header") {
                    Text(
                        stringResource(R.string.events_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(state.eventItems, key = { "event-${it.rule.id}" }) { item ->
                val key = RingKey(RingKind.EVENT, item.rule.id)
                EventAlarmCard(
                    item,
                    showSkip = state.showSkipButtons,
                    onClick = { onEditEventAlarm(item.rule.id) },
                    onToggle = { onToggleEventAlarm(item, it) },
                    onSkip = { onSkip(key) },
                    onUndoSkip = { onUndoSkip(key) },
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(
    item: AlarmItemUi,
    showSkip: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onUndoSkip: () -> Unit,
) {
    val alarm = item.alarm
    val dim = if (alarm.enabled) 1f else 0.5f
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Format.time(alarm.time),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    )
                    val title = listOf(alarm.label, Format.daysSummary(alarm.days))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Text(
                Format.dayOffSummary(alarm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val next = item.next
            if (alarm.enabled && next != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    Format.nextRing(next),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                next.dayOffReason?.let {
                    Text(
                        stringResource(R.string.adapted_time, dayOffReasonText(it)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (next.skipped.isNotEmpty() && alarm.dayOffMode == DayOffMode.SKIP) {
                    val shown = next.skipped.take(MAX_SKIPPED_SHOWN).map {
                        stringResource(R.string.fmt_paren, Format.dayDate(it.date), dayOffReasonText(it.reason))
                    }.joinToString(", ")
                    val more = if (next.skipped.size > MAX_SKIPPED_SHOWN) {
                        " +${next.skipped.size - MAX_SKIPPED_SHOWN}"
                    } else {
                        ""
                    }
                    Text(
                        stringResource(R.string.skipped_days, shown + more),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (alarm.enabled) {
                Text(stringResource(R.string.no_next_ring), style = MaterialTheme.typography.bodySmall)
            }
            if (item.canSkip) SkipRow(next?.at.takeIf { showSkip }, item.skippedAt, onSkip, onUndoSkip)
        }
    }
}

@Composable
private fun EventAlarmCard(
    item: EventAlarmItemUi,
    showSkip: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onUndoSkip: () -> Unit,
) {
    val rule = item.rule
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.minutes_before, Format.duration(rule.minutesBefore)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (rule.enabled) 1f else 0.5f),
                    )
                    val single = rule.singleEvent
                    val what = when {
                        single != null -> stringResource(
                            R.string.event_what_single,
                            single.title,
                            Format.dayDate(single.start.toLocalDate()),
                        )

                        rule.firstEventOfDayOnly -> stringResource(R.string.event_what_first)

                        else -> stringResource(R.string.event_what_each)
                    }
                    Text(listOf(rule.label, what).filter { it.isNotBlank() }.joinToString(" · "))
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            val filters = buildList {
                add(item.calendarNames.joinToString().ifBlank { stringResource(R.string.no_calendar) })
                if (rule.keywords.isNotBlank()) add(stringResource(R.string.keywords_summary, rule.keywords))
            }.joinToString(" · ")
            Text(
                filters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val next = item.next
            if (rule.enabled && next != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.fmt_next_ring_event,
                        Format.nextRing(next.at),
                        next.event.title,
                        Format.time(next.event.start.toLocalTime()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (rule.enabled) {
                Text(stringResource(R.string.no_upcoming_event), style = MaterialTheme.typography.bodySmall)
            }
            if (item.canSkip) SkipRow(next?.at.takeIf { showSkip }, item.skippedAt, onSkip, onUndoSkip)
        }
    }
}

/**
 * "Skip tomorrow" as a button with an icon, or, once skipped, a coloured strip that says so with a way back.
 * The alarm itself is not changed.
 */
@Composable
private fun SkipRow(nextAt: LocalDateTime?, skippedAt: LocalDateTime?, onSkip: () -> Unit, onUndo: () -> Unit) {
    when {
        skippedAt != null -> Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = null, Modifier.size(18.dp))
                Text(
                    stringResource(R.string.skip_tag, Format.dayWord(skippedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = onUndo) { Text(stringResource(R.string.skip_undo)) }
            }
        }

        nextAt != null -> AssistChip(
            onClick = onSkip,
            label = { Text(stringResource(R.string.skip_next, Format.dayWord(nextAt))) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = null, Modifier.size(18.dp))
            },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
