package com.gamso.wakely.domain.usecase

/**
 * The gradual volume of an alarm: silence at the start, the alarm volume once the ramp is over. The curve is
 * quadratic because the ear hears loudness on a logarithmic scale: a linear rise would seem to start too loud.
 */
object VolumeRamp {

    /** Volume factor (0 to 1) [elapsedMs] after the alarm started, for a ramp of [rampSeconds]. */
    fun level(elapsedMs: Long, rampSeconds: Int): Float {
        // No ramp, or any doubt: full volume. A loud alarm is better than a missed one.
        if (rampSeconds <= 0 || elapsedMs < 0) return 1f
        val progress = elapsedMs.toFloat() / (rampSeconds * MS_PER_SECOND)
        return if (progress >= 1f) 1f else progress * progress
    }

    private const val MS_PER_SECOND = 1000f
}
