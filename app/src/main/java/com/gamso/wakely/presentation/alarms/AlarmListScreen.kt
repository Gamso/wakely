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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.presentation.common.Format

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
    onOpenCalendars: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réveils") },
                actions = {
                    IconButton(onClick = onOpenCalendars) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Réglages")
                    }
                },
            )
        },
        floatingActionButton = {
            var menu by remember { mutableStateOf(false) }
            Box {
                ExtendedFloatingActionButton(onClick = { menu = true }) { Text("+  Nouveau") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Réveil") }, onClick = {
                        menu = false
                        onAdd()
                    })
                    DropdownMenuItem(text = { Text("Réveil avant un événement") }, onClick = {
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
                            "Autorisez l'accès à l'agenda pour adapter vos réveils aux jours fériés et congés.",
                            "Autoriser",
                            onRequestCalendarPermission,
                        )
                    }
                } else if (!state.hasCalendarSelected) {
                    item {
                        Banner(
                            "Choisissez le ou les agendas contenant vos jours non travaillés.",
                            "Choisir",
                            onOpenCalendars,
                        )
                    }
                }
                if (!state.canScheduleExactAlarms) {
                    item {
                        Banner(
                            "Les alarmes exactes sont désactivées : les réveils pourraient être en retard.",
                            "Activer",
                            onOpenExactAlarmSettings,
                        )
                    }
                }
                if (warnings.notificationsDenied) {
                    item {
                        Banner(
                            "Les notifications sont nécessaires pour afficher le réveil qui sonne.",
                            "Autoriser",
                            onRequestNotifications,
                        )
                    }
                }
                if (warnings.fullScreenDenied) {
                    item {
                        Banner(
                            "Autorisez l'affichage plein écran pour voir le réveil sur l'écran verrouillé.",
                            "Autoriser",
                            onOpenFullScreenSettings,
                        )
                    }
                }
                if (state.items.isEmpty() && state.eventItems.isEmpty()) {
                    item {
                        Text(
                            "Aucun réveil. Appuyez sur « Nouveau » pour commencer.",
                            modifier = Modifier.padding(top = 32.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(state.items, key = { "alarm-${it.alarm.id}" }) { item ->
                AlarmCard(item, onClick = { onEdit(item.alarm.id) }, onToggle = { onToggle(item, it) })
            }
            if (state.eventItems.isNotEmpty()) {
                item(key = "events-header") {
                    Text(
                        "Avant un événement",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(state.eventItems, key = { "event-${it.rule.id}" }) { item ->
                EventAlarmCard(item, onClick = {
                    onEditEventAlarm(item.rule.id)
                }, onToggle = { onToggleEventAlarm(item, it) })
            }
        }
    }
}

@Composable
private fun AlarmCard(item: AlarmItemUi, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
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
                    Text("Heure adaptée : $it", style = MaterialTheme.typography.bodySmall)
                }
                if (next.skipped.isNotEmpty() && alarm.dayOffMode == DayOffMode.SKIP) {
                    val shown = next.skipped.take(3).joinToString(", ") {
                        "${Format.dayDate(it.date)} (${it.reason})"
                    }
                    val more = if (next.skipped.size > 3) " +${next.skipped.size - 3}" else ""
                    Text("Ignoré : $shown$more", style = MaterialTheme.typography.bodySmall)
                }
            } else if (alarm.enabled) {
                Text("Aucune sonnerie prévue", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EventAlarmCard(item: EventAlarmItemUi, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val rule = item.rule
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Format.duration(rule.minutesBefore)} avant",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (rule.enabled) 1f else 0.5f),
                    )
                    val single = rule.singleEvent
                    val what = when {
                        single != null -> "« ${single.title} » du ${Format.dayDate(single.start.toLocalDate())}"
                        rule.firstEventOfDayOnly -> "le premier événement de la journée"
                        else -> "chaque événement"
                    }
                    Text(listOf(rule.label, what).filter { it.isNotBlank() }.joinToString(" · "))
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            val filters = buildList {
                add(item.calendarNames.joinToString().ifBlank { "aucun agenda" })
                if (rule.keywords.isNotBlank()) add("mots-clés : ${rule.keywords}")
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
                    Format.nextRing(
                        next.at,
                    ) + " — ${next.event.title} (${Format.time(next.event.start.toLocalTime())})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (rule.enabled) {
                Text("Aucun événement correspondant à venir", style = MaterialTheme.typography.bodySmall)
            }
        }
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
