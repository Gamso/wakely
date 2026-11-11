package com.gamso.wakely.presentation.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.usecase.EventPreview
import com.gamso.wakely.domain.usecase.EventPreviewItem
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format

/**
 * The "Calendars" screen: the calendars the user chose to use, once each, grouped by account, with what each is used
 * for. Calendars are not listed automatically: [added] holds the ones just added from the picker, which stay while
 * the screen is open even before they get a use.
 */
internal fun LazyListScope.calendarsItems(
    state: SettingsUiState,
    added: Set<String>,
    onOpenCalendar: (String) -> Unit,
    onLeave: (DeviceCalendar, Boolean) -> Unit,
    onEvents: (DeviceCalendar) -> Unit,
    onRemove: (DeviceCalendar) -> Unit,
    onAdd: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    if (!state.loading && !state.hasPermission) {
        item(key = "permission") {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow_calendar_access)) }
        }
    }
    item(key = "calendars-description") {
        Text(stringResource(R.string.calendars_pick_description), style = MaterialTheme.typography.bodyMedium)
    }
    val chosen = state.calendars.filter { state.isLeave(it) || state.isEvents(it) || it.key in added }
    if (!state.loading && chosen.isEmpty()) {
        item(key = "none-used") {
            Text(
                stringResource(R.string.calendars_none_used),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    chosen.groupBy { it.account }.forEach { (account, calendars) ->
        item(key = "account-$account") {
            Text(
                account,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(calendars, key = { it.key }) { calendar ->
            CalendarCard(calendar, state, { onOpenCalendar(calendar.key) }, onLeave, onEvents) { onRemove(calendar) }
        }
    }
    if (!state.loading && state.hasPermission) {
        item(key = "add-calendar") { OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.add_calendar)) } }
    }
}

@Composable
private fun CalendarCard(
    calendar: DeviceCalendar,
    state: SettingsUiState,
    onOpen: () -> Unit,
    onLeave: (DeviceCalendar, Boolean) -> Unit,
    onEvents: (DeviceCalendar) -> Unit,
    onRemove: () -> Unit,
) {
    val leave = state.isLeave(calendar)
    val events = state.isEvents(calendar)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(calendar.color)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(calendar.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        usesSummary(leave, events),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = leave,
                    onClick = { onLeave(calendar, !leave) },
                    label = { Text(stringResource(R.string.use_leave)) },
                )
                FilterChip(
                    selected = events,
                    onClick = { onEvents(calendar) },
                    label = { Text(stringResource(R.string.use_events)) },
                )
            }
            if (!leave && !events) UnusedWarning(Modifier.padding(top = 8.dp))
        }
    }
}

/** A listed calendar that no alarm uses: the user is told, and can pick a use or remove it. */
@Composable
private fun UnusedWarning(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.calendar_unused_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}

/** "Leave + Events", or "Not used". */
@Composable
internal fun usesSummary(leave: Boolean, events: Boolean): String {
    val leaveText = stringResource(R.string.use_leave)
    val eventsText = stringResource(R.string.use_events)
    return listOfNotNull(leaveText.takeIf { leave }, eventsText.takeIf { events })
        .joinToString(" + ")
        .ifEmpty { stringResource(R.string.use_none) }
}

/** One calendar on its own screen: a block for each use, with its keywords, the detected days and the alarms. */
internal fun LazyListScope.calendarDetailItems(
    calendar: DeviceCalendar?,
    state: SettingsUiState,
    onLeave: (DeviceCalendar, Boolean) -> Unit,
    onEvents: (DeviceCalendar) -> Unit,
    onKeywords: (DeviceCalendar, CalendarCategory, String) -> Unit,
    onRemove: (DeviceCalendar) -> Unit,
) {
    if (calendar == null) {
        item(key = "not-found") { Text(stringResource(R.string.calendar_not_found)) }
        return
    }
    item(key = "calendar-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(calendar.color)
            Text(calendar.account, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!state.isLeave(calendar) && !state.isEvents(calendar)) {
        item(key = "unused-warning") { UnusedWarning() }
    }
    item(key = "use-leave") {
        UseBlock(
            title = stringResource(R.string.cat_leave_title),
            description = stringResource(R.string.cat_leave_desc),
            on = state.isLeave(calendar),
            onChange = { onLeave(calendar, it) },
        ) {
            val assigned = state.assigned(calendar, CalendarCategory.LEAVE) ?: return@UseBlock
            KeywordsField(assigned.keywords, R.string.cat_leave_hint) {
                onKeywords(calendar, CalendarCategory.LEAVE, it)
            }
            LeavePreviewSection(state.calendarPreview, listOf(assigned))
        }
    }
    item(key = "use-events") {
        UseBlock(
            title = stringResource(R.string.cat_events_title),
            description = stringResource(R.string.cat_events_desc),
            on = state.isEvents(calendar),
            onChange = { onEvents(calendar) },
        ) {
            val assigned = state.assigned(calendar, CalendarCategory.EVENTS) ?: return@UseBlock
            KeywordsField(assigned.keywords, R.string.cat_events_hint) {
                onKeywords(calendar, CalendarCategory.EVENTS, it)
            }
            Text(
                stringResource(R.string.calendar_events_keywords_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val used = state.usedBy(calendar)
            Text(
                if (used == 0) {
                    stringResource(R.string.calendar_unused_by)
                } else {
                    pluralStringResource(R.plurals.calendar_used_by, used, used)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EventPreviewSection(state.eventPreview, assigned.keywords.isBlank())
        }
    }
    item(key = "remove-calendar") {
        TextButton(onClick = { onRemove(calendar) }) {
            Text(stringResource(R.string.calendar_remove), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A use of the calendar (leave, events): a switch, and its settings while it is on. */
@Composable
private fun UseBlock(
    title: String,
    description: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = on, onCheckedChange = onChange)
            }
            if (on) content()
        }
    }
}

@Composable
private fun KeywordsField(value: String, hint: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.keywords_optional)) },
        placeholder = { Text(stringResource(hint)) },
        supportingText = { Text(stringResource(R.string.keywords_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Asks before a calendar that event alarms use leaves the pool: they will stop watching it. */
@Composable
internal fun StopEventsDialog(title: Int, used: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(pluralStringResource(R.plurals.calendar_stop_events_body, used, used)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The calendars of the phone that are not used yet, to pick from. Public holiday calendars are left out: the holidays
 * are built in. Below, how to get a calendar that is not listed: it has to be synced by an Android account.
 */
@Composable
internal fun AddCalendarDialog(
    calendars: List<DeviceCalendar>,
    onPick: (DeviceCalendar) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.add_calendar_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (calendars.isEmpty()) {
                    Text(stringResource(R.string.no_other_calendar))
                }
                calendars.groupBy { it.account }.forEach { (account, list) ->
                    Text(
                        account,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    list.forEach { cal ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(cal) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(cal.color)
                            Text(cal.name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.calendar_missing_help), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { openAddAccount(context) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.add_account))
                }
            }
        },
    )
}

/** Opens the Android screen to add an account (Outlook, Exchange, Google...). Nothing is lost if it is missing. */
private fun openAddAccount(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** What the Calendars screens do when a calendar is ticked, unticked, added or removed, and the dialogs they need. */
internal class CalendarActions(
    /** Calendars kept in the list while the screen is open, even with no use. */
    val added: Set<String>,
    val onLeave: (DeviceCalendar, Boolean) -> Unit,
    val onEvents: (DeviceCalendar) -> Unit,
    val onRemove: (DeviceCalendar) -> Unit,
    val onAdd: () -> Unit,
    private val dialogs: @Composable () -> Unit,
) {
    @Composable
    fun Dialogs() = dialogs()
}

@Composable
internal fun rememberCalendarActions(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    page: SettingsPage,
    onBack: () -> Unit,
): CalendarActions {
    var picking by remember { mutableStateOf(false) }
    var stopEvents by remember { mutableStateOf<DeviceCalendar?>(null) }
    var removing by remember { mutableStateOf<DeviceCalendar?>(null) }
    // Joined into one string to survive rotation.
    var addedKeys by rememberSaveable { mutableStateOf("") }
    val added = addedKeys.split('|').filter { it.isNotEmpty() }.toSet()

    // A calendar that loses its last use stays listed with a warning while the screen is open: only "Remove" drops it.
    val keep: (DeviceCalendar) -> Unit = { addedKeys = (added + it.key).joinToString("|") }
    val doRemove: (DeviceCalendar) -> Unit = { cal ->
        viewModel.removeFromApp(cal)
        addedKeys = (added - cal.key).joinToString("|")
        if (page == SettingsPage.CALENDAR) onBack()
    }
    return CalendarActions(
        added = added,
        onLeave = { cal, on ->
            viewModel.setLeave(cal, on)
            keep(cal)
        },
        // Turning events off for a calendar that alarms use asks first.
        onEvents = { cal ->
            when {
                !state.isEvents(cal) -> viewModel.setEvents(cal, true)
                state.usedBy(cal) > 0 -> stopEvents = cal
                else -> viewModel.setEvents(cal, false)
            }
            keep(cal)
        },
        onRemove = { cal -> if (state.usedBy(cal) > 0) removing = cal else doRemove(cal) },
        onAdd = { picking = true },
        dialogs = {
            if (picking) {
                AddCalendarDialog(
                    calendars = state.calendars.filter {
                        !it.isHolidays && !state.isLeave(it) && !state.isEvents(it) && it.key !in added
                    },
                    onPick = {
                        addedKeys = (added + it.key).joinToString("|")
                        picking = false
                    },
                    onDismiss = { picking = false },
                )
            }
            stopEvents?.let { cal ->
                StopEventsDialog(
                    title = R.string.calendar_stop_events_title,
                    used = state.usedBy(cal),
                    onConfirm = {
                        viewModel.setEvents(cal, false)
                        stopEvents = null
                    },
                    onDismiss = { stopEvents = null },
                )
            }
            removing?.let { cal ->
                StopEventsDialog(
                    title = R.string.calendar_remove_title,
                    used = state.usedBy(cal),
                    onConfirm = {
                        doRemove(cal)
                        removing = null
                    },
                    onDismiss = { removing = null },
                )
            }
        },
    )
}

private const val MAX_EVENTS_SHOWN = 8
private const val MAX_EVENTS_IGNORED = 3

/** The upcoming events with a time that the event alarms see in this calendar, to check its keywords by eye. */
@Composable
private fun EventPreviewSection(preview: EventPreview?, noKeywords: Boolean) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(stringResource(R.string.events_preview_title), style = MaterialTheme.typography.titleSmall)
    when {
        preview == null -> Text(stringResource(R.string.loading))

        preview.counted.isEmpty() && preview.ignored.isEmpty() -> {
            Text(
                stringResource(R.string.events_preview_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.events_preview_tasks), style = MaterialTheme.typography.bodySmall)
        }

        else -> {
            preview.counted.take(MAX_EVENTS_SHOWN).forEach { EventRow(it, counted = true) }
            preview.ignored.take(MAX_EVENTS_IGNORED).forEach { EventRow(it, counted = false) }
            val hidden = (preview.counted.size - MAX_EVENTS_SHOWN).coerceAtLeast(0) +
                (preview.ignored.size - MAX_EVENTS_IGNORED).coerceAtLeast(0)
            if (hidden > 0) {
                Text(
                    stringResource(R.string.leave_preview_more, hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (noKeywords && preview.counted.isNotEmpty()) {
                Text(
                    stringResource(R.string.events_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun EventRow(item: EventPreviewItem, counted: Boolean) {
    val color = if (counted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { stringResource(R.string.ring_default_label) },
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textDecoration = if (counted) null else TextDecoration.LineThrough,
            )
            Text(
                stringResource(
                    R.string.leave_day_hours,
                    Format.dayDate(item.start.toLocalDate()),
                    Format.time(item.start.toLocalTime()),
                    Format.time(item.end.toLocalTime()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (counted) R.string.leave_counted else R.string.leave_ignored),
            style = MaterialTheme.typography.labelSmall,
            color = if (counted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
