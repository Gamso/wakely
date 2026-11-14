package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.PendingSnoozes
import java.time.LocalDateTime

/** Keeps the time of each pending snooze, under "KIND:id". */
class PendingSnoozesPrefs(context: Context) : PendingSnoozes {

    private val prefs = context.getSharedPreferences("pending_snoozes", Context.MODE_PRIVATE)

    override fun save(key: RingKey, at: LocalDateTime) {
        prefs.edit().putString(name(key), at.toString()).apply()
    }

    override fun getAll(): Map<RingKey, LocalDateTime> = prefs.all.mapNotNull { (name, value) ->
        runCatching {
            val (kind, id) = name.split(':')
            RingKey(RingKind.valueOf(kind), id.toLong()) to LocalDateTime.parse(value as String)
        }.getOrNull()
    }.toMap()

    override fun clear(key: RingKey) {
        prefs.edit().remove(name(key)).apply()
    }

    private fun name(key: RingKey) = "${key.kind.name}:${key.id}"
}
