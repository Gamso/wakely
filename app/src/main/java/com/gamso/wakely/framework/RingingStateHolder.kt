package com.gamso.wakely.framework

import com.gamso.wakely.domain.model.RingKey
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RingingAlarm(
    val key: RingKey,
    val label: String,
    /** 0 = snooze disabled. */
    val snoozeMinutes: Int,
    /** Start time of the event an event rule rings for. */
    val eventTime: LocalTime? = null,
)

/** State shared between the ringing service and the ringing screen. */
class RingingStateHolder {
    private val _ringing = MutableStateFlow<RingingAlarm?>(null)
    val ringing: StateFlow<RingingAlarm?> = _ringing.asStateFlow()

    private val _unanswered = MutableStateFlow<RingingAlarm?>(null)

    /**
     * The ringing alarm as long as the user has not chosen Stop or Snooze on the ringing screen. The service stops a
     * moment after that choice: in between, the app must not open the ringing screen again.
     */
    val unanswered: StateFlow<RingingAlarm?> = _unanswered.asStateFlow()

    fun set(value: RingingAlarm?) {
        _unanswered.value = value
        _ringing.value = value
    }

    /** Stop or Snooze was chosen on the ringing screen. */
    fun answer() {
        _unanswered.value = null
    }
}
