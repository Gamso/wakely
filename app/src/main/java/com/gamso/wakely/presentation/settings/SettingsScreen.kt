package com.gamso.wakely.presentation.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.HolidayItem
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.usecase.LeaveItem
import com.gamso.wakely.domain.usecase.LeavePreview
import com.gamso.wakely.presentation.common.BackButton
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.TimePickerDialog
import com.gamso.wakely.presentation.common.dayOffReasonText
import java.util.Locale

private const val CHEVRON_OPEN_DEGREES = 180f
private const val MAX_COUNTED_SHOWN = 8
private const val MAX_IGNORED_SHOWN = 3

private data class CategoryText(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val keywordHint: Int,
)

private val CATEGORY_TEXTS = mapOf(
    CalendarCategory.LEAVE to CategoryText(
        R.string.cat_leave_title,
        R.string.cat_leave_desc,
        R.string.cat_leave_hint,
    ),
    CalendarCategory.EVENTS to CategoryText(
        R.string.cat_events_title,
        R.string.cat_events_desc,
        R.string.cat_events_hint,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var pickSummaryTime by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<CalendarCategory?>(null) }
    var pickCountry by remember { mutableStateOf(false) }
    var pickRegion by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page.title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                SettingsPage.MENU -> menuItems(state, onOpenPage, onOpenAbout, onRequestPermission)

                SettingsPage.NOTIFICATION -> item(key = "notification") {
                    NotificationCard(state.summary, viewModel::setSummaryEnabled) { pickSummaryTime = true }
                }

                SettingsPage.QUICK -> quickItems(state, viewModel)

                SettingsPage.HOLIDAYS -> item(key = "holidays") {
                    HolidaysContent(
                        state = state,
                        onPickCountry = { pickCountry = true },
                        onPickRegion = { pickRegion = true },
                        onToggle = viewModel::setHolidayExcluded,
                    )
                }

                SettingsPage.LEAVE, SettingsPage.EVENTS -> calendarItems(
                    category = if (page == SettingsPage.LEAVE) CalendarCategory.LEAVE else CalendarCategory.EVENTS,
                    state = state,
                    viewModel = viewModel,
                    onAdd = { addTo = it },
                    onRequestPermission = onRequestPermission,
                )
            }
        }
    }

    if (pickSummaryTime) {
        TimePickerDialog(state.summary.time, { pickSummaryTime = false }, viewModel::setSummaryTime)
    }
    if (pickCountry) {
        CountryDialog(
            auto = state.holidayCountryAuto,
            detected = state.holidayDetectedCountry,
            selected = state.holidayCountry,
            countries = state.supportedCountries,
            onAuto = {
                viewModel.setHolidayCountryAuto()
                pickCountry = false
            },
            onPick = {
                viewModel.setHolidayCountry(it)
                pickCountry = false
            },
            onDismiss = { pickCountry = false },
        )
    }
    if (pickRegion) {
        RegionDialog(
            regions = state.holidayRegions,
            selected = state.holidayRegion?.path,
            onPick = {
                viewModel.setHolidayRegion(it)
                pickRegion = false
            },
            onDismiss = { pickRegion = false },
        )
    }
    addTo?.let { category ->
        AddCalendarDialog(
            title = stringResource(R.string.add_to_category, stringResource(CATEGORY_TEXTS.getValue(category).title)),
            calendars = state.available(category),
            onDismiss = { addTo = null },
            onPick = {
                viewModel.addCalendar(it, category)
                addTo = null
            },
        )
    }
}

/** The short menu: a few grouped rows, each with an icon, a title and a summary of its current state. */
private fun LazyListScope.menuItems(
    state: SettingsUiState,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenAbout: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    if (!state.loading && !state.hasPermission) {
        item(key = "permission") {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow_calendar_access)) }
        }
    }
    item(key = "menu-features") {
        MenuGroup {
            MenuRow(R.drawable.ic_notifications, R.string.summary_title, notificationSummary(state)) {
                onOpenPage(SettingsPage.NOTIFICATION)
            }
            MenuRow(R.drawable.ic_skip_next, R.string.quick_buttons_title, quickSummary(state)) {
                onOpenPage(SettingsPage.QUICK)
            }
        }
    }
    item(key = "menu-days-off") {
        MenuGroup {
            MenuRow(R.drawable.ic_flag, R.string.holidays_title, holidaysSummary(state)) {
                onOpenPage(SettingsPage.HOLIDAYS)
            }
            MenuRow(R.drawable.ic_sun, R.string.cat_leave_title, leaveSummary(state)) {
                onOpenPage(SettingsPage.LEAVE)
            }
        }
    }
    item(key = "menu-events") {
        MenuGroup {
            MenuRow(R.drawable.ic_event, R.string.cat_events_title, calendarsSummary(state, CalendarCategory.EVENTS)) {
                onOpenPage(SettingsPage.EVENTS)
            }
        }
    }
    item(key = "menu-about") {
        MenuGroup { MenuRow(R.drawable.ic_info, R.string.about_link, null, onOpenAbout) }
    }
}

@Composable
private fun notificationSummary(state: SettingsUiState): String = if (state.summary.enabled) {
    stringResource(R.string.menu_notification_on, Format.time(state.summary.time))
} else {
    stringResource(R.string.menu_notification_off)
}

@Composable
private fun quickSummary(state: SettingsUiState): String {
    val on = quickButtonsOn(state.skipButtons, state.pauseButton)
    if (on.isEmpty()) return stringResource(R.string.menu_none)
    val skip = stringResource(R.string.quick_skip)
    val pause = stringResource(R.string.quick_pause)
    return on.joinToString(", ") { if (it == QuickButton.SKIP) skip else pause }
}

@Composable
private fun holidaysSummary(state: SettingsUiState): String =
    listOfNotNull(state.holidayCountry?.let(::countryName), state.holidayRegion?.name).joinToString(" · ")
        .ifEmpty { stringResource(R.string.holidays_country_none) }

@Composable
private fun calendarsSummary(state: SettingsUiState, category: CalendarCategory): String {
    val count = state.byCategory[category].orEmpty().size
    return if (count == 0) {
        stringResource(R.string.calendars_none)
    } else {
        pluralStringResource(R.plurals.calendars_count, count, count)
    }
}

/** The leave summary prefers the number of days detected, once it is known. */
@Composable
private fun leaveSummary(state: SettingsUiState): String {
    val preview = state.leavePreview
    return if (state.byCategory[CalendarCategory.LEAVE].orEmpty().isNotEmpty() && preview != null) {
        pluralStringResource(R.plurals.leave_preview_days, preview.days, preview.days)
    } else {
        calendarsSummary(state, CalendarCategory.LEAVE)
    }
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(content = content) }
}

/** A row of the menu: icon, title, an optional one-line summary, and a chevron. */
@Composable
private fun MenuRow(@DrawableRes icon: Int, @StringRes title: Int, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
}

@Composable
private fun NotificationCard(summary: SummarySettings, onEnabled: (Boolean) -> Unit, onPickTime: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.summary_description), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = summary.enabled, onCheckedChange = onEnabled)
            }
            if (summary.enabled) {
                OutlinedButton(onClick = onPickTime, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.summary_time, Format.time(summary.time)))
                }
            }
        }
    }
}

private fun LazyListScope.quickItems(state: SettingsUiState, viewModel: SettingsViewModel) {
    item(key = "skip-buttons") {
        SwitchCard(
            stringResource(R.string.skip_buttons_title),
            stringResource(R.string.skip_buttons_description),
            state.skipButtons,
            viewModel::setSkipButtons,
        )
    }
    item(key = "pause-button") {
        SwitchCard(
            stringResource(R.string.pause_button_title),
            stringResource(R.string.pause_button_description),
            state.pauseButton,
            viewModel::setPauseButton,
        )
    }
}

/** A calendar category on its own screen: its calendars, their keywords and, for leave, the detected days. */
private fun LazyListScope.calendarItems(
    category: CalendarCategory,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onAdd: (CalendarCategory) -> Unit,
    onRequestPermission: () -> Unit,
) {
    if (!state.loading && !state.hasPermission) {
        item(key = "permission") {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow_calendar_access)) }
        }
    }
    item(key = "calendars-description") {
        Text(stringResource(R.string.calendars_description), style = MaterialTheme.typography.bodySmall)
    }
    item(key = "cat-$category") {
        CategoryContent(
            category = category,
            assigned = state.byCategory[category].orEmpty(),
            onAdd = { onAdd(category) },
            onRemove = { viewModel.removeCalendar(it, category) },
            onKeywords = { cal, kw -> viewModel.setKeywords(cal, category, kw) },
            extra = if (category == CalendarCategory.LEAVE) {
                { LeavePreviewSection(state.leavePreview, state.byCategory[category].orEmpty()) }
            } else {
                null
            },
        )
    }
}

/** What the leave calendars give as days off, with their keywords, so a mixed calendar can be tuned by eye. */
@Composable
private fun LeavePreviewSection(preview: LeavePreview?, assigned: List<AssignedCalendar>) {
    if (assigned.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(stringResource(R.string.leave_preview_title), style = MaterialTheme.typography.titleSmall)
    when {
        preview == null -> Text(stringResource(R.string.loading))

        preview.counted.isEmpty() && preview.ignored.isEmpty() -> Text(
            stringResource(R.string.leave_preview_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            Text(
                pluralStringResource(R.plurals.leave_preview_days, preview.days, preview.days),
                style = MaterialTheme.typography.bodySmall,
            )
            preview.counted.take(MAX_COUNTED_SHOWN).forEach { LeaveRow(it, counted = true) }
            preview.ignored.take(MAX_IGNORED_SHOWN).forEach { LeaveRow(it, counted = false) }
            val hidden = preview.counted.size - MAX_COUNTED_SHOWN.coerceAtMost(preview.counted.size) +
                preview.ignored.size - MAX_IGNORED_SHOWN.coerceAtMost(preview.ignored.size)
            if (hidden > 0) {
                Text(
                    stringResource(R.string.leave_preview_more, hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.counted.isNotEmpty() && assigned.any { it.keywords.isBlank() }) {
                Text(
                    stringResource(R.string.leave_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun LeaveRow(item: LeaveItem, counted: Boolean) {
    val dates = if (item.last == item.first) {
        Format.dayDate(item.first)
    } else {
        stringResource(R.string.leave_range, Format.dayDate(item.first), Format.dayDate(item.last))
    }
    val color = if (counted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                dayOffReasonText(item.title),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textDecoration = if (counted) null else TextDecoration.LineThrough,
            )
            Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(if (counted) R.string.leave_counted else R.string.leave_ignored),
            style = MaterialTheme.typography.labelSmall,
            color = if (counted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A card with a title, a description and one switch. */
@Composable
private fun SwitchCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CategoryContent(
    category: CalendarCategory,
    assigned: List<AssignedCalendar>,
    onAdd: () -> Unit,
    onRemove: (DeviceCalendar) -> Unit,
    onKeywords: (DeviceCalendar, String) -> Unit,
    /** Extra content shown after the "add a calendar" button. */
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val text = CATEGORY_TEXTS.getValue(category)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(text.description), style = MaterialTheme.typography.bodyMedium)
        assigned.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(item.calendar.color)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.calendar.name)
                    Text(item.calendar.account, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onRemove(item.calendar) }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
            OutlinedTextField(
                value = item.keywords,
                onValueChange = { onKeywords(item.calendar, it) },
                label = { Text(stringResource(R.string.keywords_optional)) },
                placeholder = { Text(stringResource(text.keywordHint)) },
                supportingText = { Text(stringResource(R.string.keywords_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.add_calendar)) }
        extra?.invoke(this)
    }
}

@Composable
private fun AddCalendarDialog(
    title: String,
    calendars: List<DeviceCalendar>,
    onDismiss: () -> Unit,
    onPick: (DeviceCalendar) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(title) },
        text = {
            if (calendars.isEmpty()) {
                Text(
                    stringResource(R.string.no_other_calendar),
                )
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
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
                }
            }
        },
    )
}

private fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())

/** Public holidays: country, region, and the upcoming holidays one can switch off. */
@Composable
private fun HolidaysContent(
    state: SettingsUiState,
    onPickCountry: () -> Unit,
    onPickRegion: () -> Unit,
    onToggle: (HolidayItem, Boolean) -> Unit,
) {
    val country = state.holidayCountry?.let(::countryName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.holidays_desc), style = MaterialTheme.typography.bodyMedium)
        val name = country ?: stringResource(R.string.holidays_country_none)
        val label = if (state.holidayCountryAuto) {
            R.string.holidays_country_label_auto
        } else {
            R.string.holidays_country_label
        }
        OutlinedButton(onClick = onPickCountry) { Text(stringResource(label, name)) }
        if (state.holidayRegions.isNotEmpty()) {
            Text(stringResource(R.string.holidays_region_desc), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onPickRegion) {
                Text(
                    stringResource(
                        R.string.holidays_region_label,
                        state.holidayRegion?.name ?: stringResource(R.string.holidays_region_whole),
                    ),
                )
            }
        }
        UpcomingHolidaysSection(state.upcomingHolidays, onToggle)
    }
}

@Composable
private fun RegionDialog(
    regions: List<HolidayRegion>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.holidays_region_dialog_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                CountryRow(stringResource(R.string.holidays_region_whole), selected == null) { onPick(null) }
                regions.forEach { region -> CountryRow(region.name, region.path == selected) { onPick(region.path) } }
            }
        },
    )
}

/** The next public holidays, each with a switch: turn it off if you work that day. */
@Composable
private fun UpcomingHolidaysSection(items: List<HolidayItem>?, onToggle: (HolidayItem, Boolean) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(stringResource(R.string.upcoming_holidays_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.upcoming_holidays_desc), style = MaterialTheme.typography.bodySmall)
    when {
        items == null -> Text(stringResource(R.string.loading))

        items.isEmpty() -> Text(
            stringResource(R.string.upcoming_holidays_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> items.forEach { HolidayRow(it, onToggle) }
    }
}

@Composable
private fun HolidayRow(item: HolidayItem, onToggle: (HolidayItem, Boolean) -> Unit) {
    val dayOff = !item.excluded
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = dayOff, role = Role.Switch, onValueChange = { onToggle(item, !it) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dayOffReasonText(item.name),
                style = MaterialTheme.typography.bodyMedium,
                color = if (dayOff) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val date = Format.fullDate(item.date)
            Text(
                if (dayOff) date else stringResource(R.string.fmt_dot, date, stringResource(R.string.holiday_working)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = dayOff, onCheckedChange = null)
    }
}

@Composable
private fun CountryDialog(
    auto: Boolean,
    detected: String?,
    selected: String?,
    countries: List<String>,
    onAuto: () -> Unit,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(countries) { countries.sortedBy { countryName(it) } }
    val autoLabel = detected?.let { stringResource(R.string.holidays_country_auto, countryName(it)) }
        ?: stringResource(R.string.holidays_country_auto_none)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.holidays_country_dialog_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                CountryRow(autoLabel, auto, onAuto)
                CountryRow(stringResource(R.string.holidays_country_none), !auto && selected == null) { onPick(null) }
                sorted.forEach { code -> CountryRow(countryName(code), !auto && code == selected) { onPick(code) } }
            }
        },
    )
}

@Composable
private fun CountryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(
            selected,
            onClick = onClick,
            role = Role.RadioButton,
        ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
