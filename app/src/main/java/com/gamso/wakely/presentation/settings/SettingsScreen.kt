package com.gamso.wakely.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.presentation.common.BackButton
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.TimePickerDialog

private const val CHEVRON_OPEN_DEGREES = 180f

private data class CategoryText(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val keywordHint: Int,
)

private val CATEGORY_TEXTS = mapOf(
    CalendarCategory.HOLIDAYS to CategoryText(
        R.string.cat_holidays_title,
        R.string.cat_holidays_desc,
        R.string.cat_holidays_hint,
    ),
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

            if (!state.loading && !state.hasPermission) {
                item(key = "permission") {
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow_calendar_access)) }
                }
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

@Composable
private fun CategoryCard(
    category: CalendarCategory,
    assigned: List<AssignedCalendar>,
    onAdd: () -> Unit,
    onRemove: (DeviceCalendar) -> Unit,
    onKeywords: (DeviceCalendar, String) -> Unit,
) {
    val text = CATEGORY_TEXTS.getValue(category)
    // Collapsed by default; kept across screen rotation.
    var expanded by rememberSaveable(category.name) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CategoryHeader(
                title = stringResource(text.title),
                count = assigned.size,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }
}

/** Tappable header of a category: title, number of calendars and a chevron. */
@Composable
private fun CategoryHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) CHEVRON_OPEN_DEGREES else 0f, label = "chevron")
    val actionLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (count == 0) {
                    stringResource(R.string.calendars_none)
                } else {
                    pluralStringResource(R.plurals.calendars_count, count, count)
                },
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
