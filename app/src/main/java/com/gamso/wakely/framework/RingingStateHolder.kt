package com.gamso.wakely.framework

import com.gamso.wakely.domain.model.RingKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RingingAlarm(
    val key: RingKey,
    val label: String,
    /** 0 = snooze disabled. */
    val snoozeMinutes: Int,
)

/** State shared between the ringing service and the ringing screen. */
class RingingStateHolder {
    private val _ringing = MutableStateFlow<RingingAlarm?>(null)
    val ringing: StateFlow<RingingAlarm?> = _ringing.asStateFlow()

    fun set(value: RingingAlarm?) {
        _ringing.value = value
    }
}
