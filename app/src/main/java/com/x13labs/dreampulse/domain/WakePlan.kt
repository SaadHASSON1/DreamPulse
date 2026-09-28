package com.x13labs.dreampulse.domain

import java.util.Calendar
import java.util.TimeZone

/**
 * When the alarm should ring. Pure, so it can be unit-tested with any clock and time zone.
 */
object WakePlan {

    /** Longest we expect falling asleep to take: the backup alarm leaves this much extra. */
    const val MAX_SLEEP_LATENCY_MS = 60 * 60 * 1000L

    /**
     * Next occurrence of [deadlineMinutes] (minutes after local midnight) after [sessionStart].
     * Anchored on the session start (not "now") so a confirmation after midnight still
     * resolves to the same morning instead of jumping a day ahead. Resolved in [zone], so after
     * flying to another time zone "07:00" means 07:00 where the user is now.
     */
    fun deadlineAfter(sessionStart: Long, deadlineMinutes: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = sessionStart
            set(Calendar.HOUR_OF_DAY, deadlineMinutes / 60)
            set(Calendar.MINUTE, deadlineMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= sessionStart) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * The wake time for a session: [sleepOnset] + goal once sleep is detected, otherwise the
     * backup (session start + goal + [MAX_SLEEP_LATENCY_MS]). A deadline, if set, wins when earlier.
     */
    fun target(sessionStart: Long, sleepOnset: Long?, goalMs: Long, deadline: Long?): Long {
        val base = if (sleepOnset != null) sleepOnset + goalMs else sessionStart + goalMs + MAX_SLEEP_LATENCY_MS
        return if (deadline != null && deadline < base) deadline else base
    }
}

/** Starting a night on a low battery risks the watch dying before the alarm rings. */
object BatteryCheck {
    /** Measured on a Galaxy Watch7: about 17% per night, so keep a safety margin. */
    const val LOW_PERCENT = 20

    fun isTooLow(level: Int, charging: Boolean): Boolean = !charging && level in 1 until LOW_PERCENT
}
