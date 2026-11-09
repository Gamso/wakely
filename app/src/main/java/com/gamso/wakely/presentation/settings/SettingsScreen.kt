package com.gamso.wakely.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.HolidayItem
import com.gamso.wakely.presentation.common.BackButton
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.TimePickerDialog
import com.gamso.wakely.presentation.common.dayOffReasonText
import java.util.Locale

private const val CHEVRON_OPEN_DEGREES = 180f

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
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.summary_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.summary_description),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = state.summary.enabled, onCheckedChange = viewModel::setSummaryEnabled)
                        }
                        if (state.summary.enabled) {
                            OutlinedButton(onClick = {
                                pickSummaryTime = true
                            }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.summary_time, Format.time(state.summary.time)))
                            }
                        }
                    }
                }
            }

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

            if (!state.loading && !state.hasPermission) {
                item(key = "permission") {
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow_calendar_access)) }
                }
            }

            item(key = "holidays") {
                HolidaysCard(
                    state = state,
                    onPickCountry = { pickCountry = true },
                    onPickRegion = { pickRegion = true },
                    onToggle = viewModel::setHolidayExcluded,
                )
            }

            item(key = "calendars-title") {
                Text(
                    stringResource(R.string.calendars_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.calendars_description),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            CalendarCategory.entries.forEach { category ->
                item(key = "cat-$category") {
                    CategoryCard(
                        category = category,
                        assigned = state.byCategory[category].orEmpty(),
                        onAdd = { addTo = category },
                        onRemove = { viewModel.removeCalendar(it, category) },
                        onKeywords = { cal, kw -> viewModel.setKeywords(cal, category, kw) },
                    )
                }
            }

            item(key = "about") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.about_link)) }
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
private fun CategoryCard(
    category: CalendarCategory,
    assigned: List<AssignedCalendar>,
    onAdd: () -> Unit,
    onRemove: (DeviceCalendar) -> Unit,
    onKeywords: (DeviceCalendar, String) -> Unit,
) {
    val text = CATEGORY_TEXTS.getValue(category)
    val calendarsText = if (assigned.isEmpty()) {
        stringResource(R.string.calendars_none)
    } else {
        pluralStringResource(R.plurals.calendars_count, assigned.size, assigned.size)
    }
    SectionCard(key = category.name, title = stringResource(text.title), subtitle = calendarsText) {
        Text(stringResource(text.description), style = MaterialTheme.typography.bodySmall)
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
    }
}

/** A card of Settings that opens and closes: collapsed by default, and kept across screen rotation. */
@Composable
private fun SectionCard(key: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CategoryHeader(title, subtitle, expanded) { expanded = !expanded }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/** Tappable header of a category: title, a short summary and a chevron. */
@Composable
private fun CategoryHeader(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) CHEVRON_OPEN_DEGREES else 0f, label = "chevron")
    val actionLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
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
private fun HolidaysCard(
    state: SettingsUiState,
    onPickCountry: () -> Unit,
    onPickRegion: () -> Unit,
    onToggle: (HolidayItem, Boolean) -> Unit,
) {
    val country = state.holidayCountry?.let(::countryName)
    val subtitle = listOfNotNull(country, state.holidayRegion?.name).joinToString(" · ")
        .ifEmpty { stringResource(R.string.holidays_country_none) }
    SectionCard(key = "holidays", title = stringResource(R.string.holidays_title), subtitle = subtitle) {
        Text(stringResource(R.string.holidays_desc), style = MaterialTheme.typography.bodySmall)
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
