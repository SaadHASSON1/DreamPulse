package com.x13labs.dreampulse.domain

/**
 * Decides when the user fell asleep, from three kinds of evidence:
 *  - movement bursts (from the accelerometer)
 *  - heart-rate samples (sampled in bursts by the service's duty cycle)
 *  - whether the watch is on the wrist
 *
 * Pure logic with an injected clock (every call takes `now`), so it can be unit-tested.
 *
 * Rules, in order:
 *  1. Stillness of [HR_STILLNESS_MS] AND the recent heart rate at least [HR_DROP] below the
 *     start-of-session baseline -> asleep.
 *  2. Stillness of [LONG_STILLNESS_MS] alone -> asleep (for when heart rate is unavailable).
 * Stillness only counts while the watch is worn. The reported onset is the START of the
 * stillness, not the moment the rule fired, so the countdown is not 15-25 minutes late.
 * The system's own "asleep" signal is handled by the caller and bypasses these rules.
 */
class SleepDetector(private val sessionStart: Long) {

    data class Decision(val onset: Long, val source: String)

    private val hrSamples = ArrayList<Pair<Long, Float>>()
    private var stillSince: Long = sessionStart
    private var worn: Boolean? = null

    @Synchronized
    fun onMotion(now: Long) {
        stillSince = now
    }

    @Synchronized
    fun onWorn(now: Long, isWorn: Boolean) {
        if (isWorn && worn != true) stillSince = now   // stillness restarts when put back on
        worn = isWorn
    }

    @Synchronized
    fun onHeartRate(now: Long, bpm: Float) {
        if (bpm < 30f || bpm > 220f) return
        hrSamples.add(now to bpm)
        // Keep the baseline window plus the last hour; drop the middle to bound memory
        val cutoff = now - 60 * 60 * 1000L
        hrSamples.removeAll { (t, _) -> t < cutoff && t > sessionStart + BASELINE_WINDOW_MS }
    }

    /** Median heart rate of the first minutes of the session, or null if too few samples. */
    @Synchronized
    fun baseline(): Float? {
        val early = hrSamples.filter { (t, _) -> t <= sessionStart + BASELINE_WINDOW_MS }.map { it.second }
        val source = if (early.size >= MIN_SAMPLES) early else hrSamples.take(MIN_SAMPLES).map { it.second }
        return if (source.size >= MIN_SAMPLES) median(source) else null
    }

    /** Median heart rate during the current stillness, or null if too few samples. */
    @Synchronized
    fun recentDuringStillness(now: Long): Float? {
        val from = maxOf(stillSince, now - RECENT_WINDOW_MS)
        val recent = hrSamples.filter { (t, _) -> t >= from }.map { it.second }
        return if (recent.size >= MIN_SAMPLES) median(recent) else null
    }

    /** Returns a decision once the user is judged asleep, otherwise null. */
    @Synchronized
    fun evaluate(now: Long): Decision? {
        if (worn == false) {
            stillSince = now          // detection paused while off the wrist
            return null
        }
        val still = now - stillSince
        if (still >= HR_STILLNESS_MS) {
            val base = baseline()
            val recent = recentDuringStillness(now)
            if (base != null && recent != null && recent <= base * (1f - HR_DROP)) {
                return Decision(stillSince, "stillness+heart-rate")
            }
        }
        if (still >= LONG_STILLNESS_MS) return Decision(stillSince, "long stillness")
        return null
    }

    @Synchronized
    fun stillForMs(now: Long): Long = if (worn == false) 0 else now - stillSince

    private fun median(values: List<Float>): Float {
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
    }

    companion object {
        const val HR_STILLNESS_MS = 15 * 60 * 1000L
        const val LONG_STILLNESS_MS = 25 * 60 * 1000L
        const val HR_DROP = 0.05f
        const val BASELINE_WINDOW_MS = 10 * 60 * 1000L
        const val RECENT_WINDOW_MS = 10 * 60 * 1000L
        const val MIN_SAMPLES = 3
    }
}
