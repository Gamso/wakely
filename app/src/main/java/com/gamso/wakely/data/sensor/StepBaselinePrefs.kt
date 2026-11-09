package com.gamso.wakely.data.sensor

import android.content.Context
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.StepBaseline
import com.gamso.wakely.domain.repository.StepBaselineStore
import java.time.LocalDateTime

/** Keeps the step count taken before an alarm until it rings. */
class StepBaselinePrefs(context: Context) : StepBaselineStore {

    private val prefs = context.getSharedPreferences("step_baselines", Context.MODE_PRIVATE)

    override fun save(key: RingKey, baseline: StepBaseline) {
        prefs.edit().putString(name(key), "${baseline.steps};${baseline.takenAt}").commit()
    }

    override fun get(key: RingKey): StepBaseline? = runCatching {
        val (steps, takenAt) = prefs.getString(name(key), null)!!.split(';')
        StepBaseline(steps.toLong(), LocalDateTime.parse(takenAt))
    }.getOrNull()

    override fun clear(key: RingKey) {
        prefs.edit().remove(name(key)).commit()
    }

    private fun name(key: RingKey) = "${key.kind.name}:${key.id}"
}
