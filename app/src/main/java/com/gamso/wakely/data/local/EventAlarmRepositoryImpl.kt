package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRef
import com.gamso.wakely.domain.repository.EventAlarmRepository
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores "minutes before an event" rules as JSON in SharedPreferences. */
class EventAlarmRepositoryImpl(context: Context) : EventAlarmRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override fun observeEventAlarms(): Flow<List<EventAlarm>> = state.asStateFlow()

    override suspend fun getEventAlarms(): List<EventAlarm> = state.value

    override suspend fun getEventAlarm(id: Long): EventAlarm? = state.value.find { it.id == id }

    override suspend fun save(alarm: EventAlarm): EventAlarm = mutex.withLock {
        val saved = if (alarm.id == 0L) alarm.copy(id = nextId()) else alarm
        update(state.value.filterNot { it.id == saved.id } + saved)
        saved
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        update(state.value.filterNot { it.id == id })
    }

    private suspend fun update(list: List<EventAlarm>) {
        val sorted = list.sortedBy { it.id }
        state.value = sorted
        val json = JSONArray().also { arr -> sorted.forEach { arr.put(it.toJson()) } }
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_RULES, json.toString()).commit() }
    }

    private fun nextId(): Long {
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun load(): List<EventAlarm> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_RULES, "[]"))
        (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toEventAlarm() }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun EventAlarm.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("enabled", enabled)
        .put("minutesBefore", minutesBefore)
        .put("sources", JSONArray(sourceKeys.toList()))
        .put("keywords", keywords)
        .put("firstOfDay", firstEventOfDayOnly)
        .put(
            "single",
            singleEvent?.let {
                JSONObject().put("source", it.sourceKey).put("title", it.title).put("start", it.start.toString())
            },
        )
        .put("sound", sound?.toJson())
        .put("snoozeMinutes", snoozeMinutes)
        .put("volumeRampSeconds", volumeRampSeconds)
        .put("vibrate", vibrate)

    private fun JSONObject.toEventAlarm(): EventAlarm {
        val sources = optJSONArray("sources")
        return EventAlarm(
            id = getLong("id"),
            label = optString("label"),
            enabled = optBoolean("enabled", true),
            minutesBefore = optInt("minutesBefore", EventAlarm.DEFAULT_MINUTES_BEFORE),
            sourceKeys = (0 until (sources?.length() ?: 0)).map { sources!!.getString(it) }.toSet(),
            keywords = optString("keywords"),
            firstEventOfDayOnly = optBoolean("firstOfDay", true),
            singleEvent = optJSONObject("single")?.let {
                EventRef(it.getString("source"), it.getString("title"), LocalDateTime.parse(it.getString("start")))
            },
            sound = optJSONObject("sound")?.toAlarmSound(),
            snoozeMinutes = optInt("snoozeMinutes", Alarm.DEFAULT_SNOOZE_MINUTES),
            volumeRampSeconds = optInt("volumeRampSeconds", 0),
            vibrate = optBoolean("vibrate", true),
        )
    }

    private companion object {
        const val PREFS = "event_alarms"
        const val KEY_RULES = "rules"
        const val KEY_NEXT_ID = "next_id"
    }
}
