package com.gamso.wakely.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.usecase.DeleteAlarmUseCase
import com.gamso.wakely.domain.usecase.SaveAlarmUseCase
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmEditUiState(
    val alarm: Alarm = Alarm(),
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    /** Becomes true when the screen must close (saved or deleted). */
    val done: Boolean = false,
)

class AlarmEditViewModel(
    private val alarmId: Long?,
    private val alarmRepository: AlarmRepository,
    private val saveAlarm: SaveAlarmUseCase,
    private val deleteAlarm: DeleteAlarmUseCase,
    /** Sound used when the alarm has none selected. */
    val defaultSound: AlarmSound,
    /** Whether the phone has a step counter (the option is offered only then). */
    val stepSensorAvailable: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmEditUiState(loaded = alarmId == null))
    val uiState: StateFlow<AlarmEditUiState> = _uiState.asStateFlow()

    init {
        if (alarmId != null) {
            viewModelScope.launch {
                val alarm = alarmRepository.getAlarm(alarmId)
                _uiState.value = if (alarm != null) {
                    AlarmEditUiState(alarm = alarm, isNew = false, loaded = true)
                } else {
                    AlarmEditUiState(loaded = true)
                }
            }
        }
    }

    private fun edit(block: (Alarm) -> Alarm) = _uiState.update { it.copy(alarm = block(it.alarm)) }

    fun setTime(time: LocalTime) = edit { it.copy(time = time) }
    fun setLabel(label: String) = edit { it.copy(label = label) }
    fun setDays(days: Set<DayOfWeek>) = edit { it.copy(days = days) }
    fun toggleDay(day: DayOfWeek) = edit { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
    fun setDayOffMode(mode: DayOffMode) = edit {
        // With "don't ring" there is no ring on a day off, so "only on days off" would never apply.
        val skip = if (mode == DayOffMode.SKIP && it.skipIfAwake == SkipIfAwake.DAYS_OFF_ONLY) {
            SkipIfAwake.ALWAYS
        } else {
            it.skipIfAwake
        }
        it.copy(dayOffMode = mode, skipIfAwake = skip)
    }
    fun setDayOffTime(time: LocalTime) = edit { it.copy(dayOffTime = time) }
    fun setSound(sound: AlarmSound) = edit { it.copy(sound = sound) }
    fun setSnoozeMinutes(minutes: Int) = edit { it.copy(snoozeMinutes = minutes) }
    fun setSkipIfAwake(skip: SkipIfAwake) = edit { it.copy(skipIfAwake = skip) }

    /** Turns the option on: an alarm that also rings on days off is only skipped on those days at first. */
    fun enableSkipIfAwake() = edit {
        it.copy(skipIfAwake = if (it.dayOffMode == DayOffMode.SKIP) SkipIfAwake.ALWAYS else SkipIfAwake.DAYS_OFF_ONLY)
    }

    fun save() = viewModelScope.launch {
        saveAlarm(_uiState.value.alarm.copy(enabled = true))
        _uiState.update { it.copy(done = true) }
    }

    fun delete() = viewModelScope.launch {
        if (alarmId != null) deleteAlarm(alarmId)
        _uiState.update { it.copy(done = true) }
    }
}
