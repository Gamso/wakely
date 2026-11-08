package com.gamso.wakely.presentation.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.repository.SoundPreviewer
import com.gamso.wakely.domain.repository.SoundRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SoundPickerUiState(
    val loading: Boolean = true,
    val sounds: List<AlarmSound> = emptyList(),
    val selectedUri: String? = null,
    val playingUri: String? = null,
    val addFailed: Boolean = false,
) {
    val selected: AlarmSound? get() = sounds.find { it.uri == selectedUri }
}

class SoundPickerViewModel(
    initialUri: String?,
    private val soundRepository: SoundRepository,
    private val previewer: SoundPreviewer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SoundPickerUiState(selectedUri = initialUri ?: soundRepository.defaultSound().uri),
    )
    val uiState: StateFlow<SoundPickerUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    private fun reload() = viewModelScope.launch {
        val sounds = soundRepository.getSounds()
        _uiState.update { state ->
            // A previously chosen sound that no longer exists falls back to the default.
            val selected = state.selectedUri?.takeIf { uri -> sounds.any { it.uri == uri } }
                ?: soundRepository.defaultSound().uri
            state.copy(loading = false, sounds = sounds, selectedUri = selected)
        }
    }

    /** Selects a sound and previews it; tapping the playing sound again stops it. */
    fun onSoundClick(sound: AlarmSound) {
        val playing = _uiState.value.playingUri == sound.uri
        if (playing) {
            stopPreview()
        } else {
            previewer.play(sound.uri)
            _uiState.update { it.copy(playingUri = sound.uri) }
        }
        _uiState.update { it.copy(selectedUri = sound.uri) }
    }

    fun addCustomSound(uri: String) = viewModelScope.launch {
        val sound = soundRepository.addCustomSound(uri)
        if (sound == null) {
            _uiState.update { it.copy(addFailed = true) }
        } else {
            _uiState.update { it.copy(selectedUri = sound.uri, addFailed = false) }
            reload()
        }
    }

    fun removeCustomSound(sound: AlarmSound) = viewModelScope.launch {
        if (_uiState.value.playingUri == sound.uri) stopPreview()
        soundRepository.removeCustomSound(sound.uri)
        reload()
    }

    fun stopPreview() {
        previewer.stop()
        _uiState.update { it.copy(playingUri = null) }
    }

    override fun onCleared() {
        previewer.stop()
    }
}
