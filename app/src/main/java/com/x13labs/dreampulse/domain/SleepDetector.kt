package com.x13labs.dreampulse.domain

/**
 * Decides when the user fell asleep, from three kinds of evidence:
 *  - movement bursts (from the accelerometer)
 *  - heart-rate samples (sampled in bursts by the service's duty cycle)
 *  - whether the watch is on the wrist
 *
 * Pure logic with an injected clock (every call takes `now`), so it can be unit-tested.
 *
 * The awake baseline is the FIRST heart-rate burst of the session: the user has just pressed
 * Start, so they are awake. (An average over the first minutes was wrong for people who fall
 * asleep within them: on 2026-09-27 it gave 49 bpm instead of 74 and hid a 32% drop.)
 *
 * Rules:
 *  1. Stillness of [BIG_DROP_STILLNESS_MS] with heart rate at least [BIG_DROP] below the baseline.
 *  2. Stillness of [HR_STILLNESS_MS] with heart rate at least [HR_DROP] below the baseline.
 *  3. Stillness of [LONG_STILLNESS_MS] alone (for when heart rate is unavailable).
 * Stillness only counts while the watch is worn. The reported onset is always the START of the
 * stillness, not the moment a rule fires; the same applies to the system's own "asleep"
 * signal via [onsetForSystemSignal].
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
        // Keep the first burst (baseline) plus the last hour, to bound memory
        val firstBurstEnd = (hrSamples.firstOrNull()?.first ?: now) + FIRST_BURST_MS
        val cutoff = now - 60 * 60 * 1000L
        hrSamples.removeAll { (t, _) -> t < cutoff && t > firstBurstEnd }
    }

    /** Median of the first heart-rate burst of the session, or null if it had too few samples. */
    @Synchronized
    fun baseline(): Float? {
        val first = hrSamples.firstOrNull() ?: return null
        val burst = hrSamples.filter { (t, _) -> t <= first.first + FIRST_BURST_MS }.map { it.second }
        return if (burst.size >= MIN_SAMPLES) median(burst) else null
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
        val base = baseline()
        val recent = if (still >= BIG_DROP_STILLNESS_MS) recentDuringStillness(now) else null
        if (base != null && recent != null) {
            if (still >= BIG_DROP_STILLNESS_MS && recent <= base * (1f - BIG_DROP)) {
                return Decision(stillSince, "stillness+big heart-rate drop")
            }
            if (still >= HR_STILLNESS_MS && recent <= base * (1f - HR_DROP)) {
                return Decision(stillSince, "stillness+heart-rate")
            }
        }
        if (still >= LONG_STILLNESS_MS) return Decision(stillSince, "long stillness")
        return null
    }

    /**
     * The system reports sleep late (it needs its own evidence first). If the user has been
     * still for a while, they fell asleep at the start of that stillness, not now.
     */
    @Synchronized
    fun onsetForSystemSignal(now: Long): Long =
        if (worn != false && now - stillSince >= SYSTEM_BACKDATE_MIN_STILL_MS) stillSince else now

    @Synchronized
    fun stillForMs(now: Long): Long = if (worn == false) 0 else now - stillSince

    private fun median(values: List<Float>): Float {
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
    }

    companion object {
        const val BIG_DROP_STILLNESS_MS = 10 * 60 * 1000L
        const val BIG_DROP = 0.15f
        const val HR_STILLNESS_MS = 15 * 60 * 1000L
        const val HR_DROP = 0.05f
        const val LONG_STILLNESS_MS = 25 * 60 * 1000L
        const val FIRST_BURST_MS = 3 * 60 * 1000L
        const val RECENT_WINDOW_MS = 10 * 60 * 1000L
        const val SYSTEM_BACKDATE_MIN_STILL_MS = 5 * 60 * 1000L
        const val MIN_SAMPLES = 3
    }
}

/**
 * Smart wake should react to light-sleep movement (turning over), not to a single twitch:
 * it fires once there are at least [EPISODES] separate movement episodes (at least
 * [EPISODE_GAP_MS] apart) within [WINDOW_MS].
 */
class SmartWakeGate {
    private val episodes = ArrayDeque<Long>()

    /** Record a movement burst; returns true when the alarm should fire. */
    @Synchronized
    fun onMotion(now: Long): Boolean {
        if (episodes.isEmpty() || now - episodes.last() >= EPISODE_GAP_MS) episodes.addLast(now)
        while (episodes.isNotEmpty() && now - episodes.first() > WINDOW_MS) episodes.removeFirst()
        return episodes.size >= EPISODES
    }

    @Synchronized
    fun reset() = episodes.clear()

    companion object {
        const val EPISODES = 2
        const val EPISODE_GAP_MS = 30_000L
        const val WINDOW_MS = 3 * 60 * 1000L
    }
}
