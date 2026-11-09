package com.gamso.wakely.data.local

import android.content.Context
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.repository.SkippedOccurrences
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps the skipped occurrences as "KIND:id|time" strings in SharedPreferences. */
class SkippedOccurrencesPrefs(context: Context) : SkippedOccurrences {

    private val prefs = context.getSharedPreferences("skipped_occurrences", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override fun observe(): Flow<Set<SkippedOccurrence>> = state.asStateFlow()

    override suspend fun get(): Set<SkippedOccurrence> = state.value

    override suspend fun add(occurrence: SkippedOccurrence) = update { it + occurrence }

    override suspend fun remove(occurrence: SkippedOccurrence) = update { it - occurrence }

    override suspend fun clear(key: RingKey) = update { all -> all.filterNot { it.key == key }.toSet() }

    override suspend fun prune(limit: LocalDateTime) = update { all -> all.filter { !it.at.isBefore(limit) }.toSet() }

    private suspend fun update(block: (Set<SkippedOccurrence>) -> Set<SkippedOccurrence>) {
        mutex.withLock {
            val updated = block(state.value)
            if (updated == state.value) return
            state.value = updated
            withContext(Dispatchers.IO) {
                prefs.edit().putStringSet(KEY, updated.map { "${it.key.kind.name}:${it.key.id}|${it.at}" }.toSet())
                    .commit()
            }
        }
    }

    private fun load(): Set<SkippedOccurrence> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull {
        runCatching {
            val (key, at) = it.split('|')
            val (kind, id) = key.split(':')
            SkippedOccurrence(RingKey(RingKind.valueOf(kind), id.toLong()), LocalDateTime.parse(at))
        }.getOrNull()
    }.toSet()

    private companion object {
        const val KEY = "occurrences"
    }
}
