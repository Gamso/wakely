package com.gamso.wakely.presentation.sound

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.SoundSource
import com.gamso.wakely.presentation.common.appViewModelFactory

/** Full-screen ringtone picker: bundled, device and user sounds, with preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerDialog(initialUri: String?, onDismiss: () -> Unit, onConfirm: (AlarmSound) -> Unit) {
    // A fresh ViewModel each time the dialog opens.
    val key = remember { "sound-picker-${System.nanoTime()}" }
    val vm: SoundPickerViewModel = viewModel(
        key = key,
        factory = appViewModelFactory { SoundPickerViewModel(initialUri, it.soundRepository, it.soundPreviewer) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addCustomSound(uri.toString())
    }
    val close = {
        vm.stopPreview()
        onDismiss()
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sonnerie") },
                    navigationIcon = { TextButton(onClick = close) { Text("Annuler") } },
                    actions = {
                        TextButton(
                            enabled = state.selected != null,
                            onClick = {
                                vm.stopPreview()
                                state.selected?.let(onConfirm)
                                onDismiss()
                            },
                        ) { Text("OK") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.loading) item { Text("Chargement…", Modifier.padding(16.dp)) }
                val groups = state.sounds.groupBy { it.source }
                listOf(
                    SoundSource.BUNDLED to "Sonneries Android (AOSP)",
                    SoundSource.CUSTOM to "Mes sons",
                    SoundSource.SYSTEM to "Sons du téléphone",
                ).forEach { (source, title) ->
                    val sounds = groups[source].orEmpty()
                    if (sounds.isEmpty() && source != SoundSource.CUSTOM) return@forEach
                    item(key = "header-$source") { SectionHeader(title) }
                    items(sounds, key = { it.uri }) { sound ->
                        SoundRow(
                            sound = sound,
                            selected = sound.uri == state.selectedUri,
                            playing = sound.uri == state.playingUri,
                            onClick = { vm.onSoundClick(sound) },
                            onRemove = if (source == SoundSource.CUSTOM) {
                                { vm.removeCustomSound(sound) }
                            } else {
                                null
                            },
                        )
                    }
                    if (source == SoundSource.CUSTOM) {
                        item(key = "add") {
                            OutlinedButton(
                                onClick = { pickFile.launch(arrayOf("audio/*")) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) { Text("+  Ajouter un son") }
                            if (state.addFailed) {
                                Text(
                                    "Impossible de lire ce fichier.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
                item(key = "credits") {
                    Text(
                        "Sonneries Android : © The Android Open Source Project, licence Apache 2.0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SoundRow(
    sound: AlarmSound,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(
            sound.name,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (playing) {
            Text("♪ lecture", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text("Retirer") }
    }
}
