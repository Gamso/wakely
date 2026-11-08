package com.gamso.wakely.presentation.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.presentation.common.BackButton
import com.gamso.wakely.presentation.common.ColorDot
import com.gamso.wakely.presentation.common.Format
import com.gamso.wakely.presentation.common.TimePickerDialog

private data class CategoryText(val title: String, val description: String, val keywordHint: String)

private val CATEGORY_TEXTS = mapOf(
    CalendarCategory.HOLIDAYS to CategoryText(
        "Jours fériés",
        "Les événements « journée entière » de ces agendas sont des jours non travaillés.",
        "ex. férié (exclut les simples fêtes)",
    ),
    CalendarCategory.LEAVE to CategoryText(
        "Congés et vacances",
        "Vos jours de congé, vacances, RTT… : jours non travaillés.",
        "ex. congé, vacances, RTT",
    ),
    CalendarCategory.EVENTS to CategoryText(
        "Événements",
        "Agendas utilisés par les réveils « avant un événement ».",
        "ex. réunion, client",
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
                title = { Text("Réglages") },
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
                                Text("Notification de la veille", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Chaque soir, un résumé des réveils du lendemain (commun à tous les réveils).",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = state.summary.enabled, onCheckedChange = viewModel::setSummaryEnabled)
                        }
                        if (state.summary.enabled) {
                            OutlinedButton(onClick = {
                                pickSummaryTime = true
                            }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Heure de la notification : ${Format.time(state.summary.time)}")
                            }
                        }
                    }
                }
            }

            if (!state.loading && !state.hasPermission) {
                item(key = "permission") {
                    Button(onClick = onRequestPermission) { Text("Autoriser l'accès à l'agenda") }
                }
            }

            item(key = "calendars-title") {
                Text("Agendas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Agendas synchronisés sur ce téléphone (Google, Outlook, Samsung…). " +
                        "Un même agenda peut être utilisé dans plusieurs catégories.",
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
                TextButton(onClick = onOpenAbout) { Text("À propos de Wakely") }
            }
        }
    }

    if (pickSummaryTime) {
        TimePickerDialog(state.summary.time, { pickSummaryTime = false }, viewModel::setSummaryTime)
    }
    addTo?.let { category ->
        AddCalendarDialog(
            title = "Ajouter à « ${CATEGORY_TEXTS.getValue(category).title} »",
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.title, style = MaterialTheme.typography.titleMedium)
            Text(text.description, style = MaterialTheme.typography.bodySmall)
            if (assigned.isEmpty()) {
                Text(
                    "Aucun agenda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            assigned.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(item.calendar.color)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.calendar.name)
                        Text(item.calendar.account, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemove(item.calendar) }) { Text("Retirer") }
                }
                OutlinedTextField(
                    value = item.keywords,
                    onValueChange = { onKeywords(item.calendar, it) },
                    label = { Text("Mots-clés (facultatif)") },
                    placeholder = { Text(text.keywordHint) },
                    supportingText = { Text("Séparés par des virgules. Vide = tous les événements.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = onAdd) { Text("+  Ajouter un agenda") }
        }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text(title) },
        text = {
            if (calendars.isEmpty()) {
                Text(
                    "Aucun autre agenda disponible. Ajoutez un compte (Google, Outlook…) dans les " +
                        "paramètres Android, et activez la synchronisation de son agenda.",
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
