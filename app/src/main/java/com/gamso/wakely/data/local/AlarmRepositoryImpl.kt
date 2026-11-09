package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.repository.AlarmRepository
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores alarms as JSON in SharedPreferences. */
class AlarmRepositoryImpl(context: Context) : AlarmRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override fun observeAlarms(): Flow<List<Alarm>> = state.asStateFlow()

    override suspend fun getAlarms(): List<Alarm> = state.value

    override suspend fun getAlarm(id: Long): Alarm? = state.value.find { it.id == id }

    override suspend fun save(alarm: Alarm): Alarm = mutex.withLock {
        val saved = if (alarm.id == 0L) alarm.copy(id = nextId()) else alarm
        update(state.value.filterNot { it.id == saved.id } + saved)
        saved
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        update(state.value.filterNot { it.id == id })
    }

    private suspend fun update(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.time }, { it.id }))
        state.value = sorted
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_ALARMS, sorted.toJson().toString()).commit()
        }
    }

    private fun nextId(): Long {
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun load(): List<Alarm> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_ALARMS, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).toAlarm() }
    }.getOrDefault(emptyList())

    private fun List<Alarm>.toJson() = JSONArray().also { arr ->
        forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("time", a.time.toString())
                    .put("days", JSONArray(a.days.map { it.value }))
                    .put("label", a.label)
                    .put("enabled", a.enabled)
                    .put("dayOffMode", a.dayOffMode.name)
                    .put("dayOffTime", a.dayOffTime.toString())
                    .put("sound", a.sound?.toJson())
                    .put("snoozeMinutes", a.snoozeMinutes)
                    .put("skipIfAwake", a.skipIfAwake.name)
                    .put("volumeRampSeconds", a.volumeRampSeconds)
                    .put("vibrate", a.vibrate),
            )
        }
    }

    private fun JSONObject.toAlarm(): Alarm {
        val days = getJSONArray("days")
        return Alarm(
            id = getLong("id"),
            time = LocalTime.parse(getString("time")),
            days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
            label = optString("label"),
            enabled = optBoolean("enabled", true),
            dayOffMode = runCatching { DayOffMode.valueOf(getString("dayOffMode")) }
                .getOrDefault(DayOffMode.SKIP),
            dayOffTime = LocalTime.parse(optString("dayOffTime", "09:00")),
            sound = optJSONObject("sound")?.toAlarmSound(),
            snoozeMinutes = optInt("snoozeMinutes", Alarm.DEFAULT_SNOOZE_MINUTES),
            skipIfAwake = skipIfAwake(),
            volumeRampSeconds = optInt("volumeRampSeconds", 0),
            vibrate = optBoolean("vibrate", true),
        )
    }

    /** Absent = off; a former boolean `true` meant every ring. */
    private fun JSONObject.skipIfAwake(): SkipIfAwake {
        val value = opt("skipIfAwake")
        return when {
            value is Boolean -> if (value) SkipIfAwake.ALWAYS else SkipIfAwake.OFF
            value is String -> runCatching { SkipIfAwake.valueOf(value) }.getOrDefault(SkipIfAwake.OFF)
            else -> SkipIfAwake.OFF
        }
    }

    private companion object {
        const val PREFS = "alarms"
        const val KEY_ALARMS = "alarms"
        const val KEY_NEXT_ID = "next_id"
    }
}
