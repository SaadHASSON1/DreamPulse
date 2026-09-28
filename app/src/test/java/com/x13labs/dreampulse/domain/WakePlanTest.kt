package com.x13labs.dreampulse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WakePlanTest {

    private val riyadh = TimeZone.getTimeZone("Asia/Riyadh")
    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
    private val london = TimeZone.getTimeZone("Europe/London")

    private fun at(zone: TimeZone, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `deadline later the same night stays on the same day`() {
        val start = at(riyadh, 28, 1, 0)
        assertEquals(at(riyadh, 28, 7, 0), WakePlan.deadlineAfter(start, 7 * 60, riyadh))
    }

    @Test
    fun `deadline before the start moves to the next morning`() {
        val start = at(riyadh, 28, 23, 30)
        assertEquals(at(riyadh, 29, 7, 0), WakePlan.deadlineAfter(start, 7 * 60, riyadh))
    }

    @Test
    fun `after changing time zone the deadline follows the new local time`() {
        // Started at 23:30 in Riyadh, then the watch moved to London (2 hours behind)
        val start = at(riyadh, 28, 23, 30)
        assertEquals(at(london, 29, 7, 0), WakePlan.deadlineAfter(start, 7 * 60, london))
    }

    @Test
    fun `same offset zones give the same instant`() {
        val start = at(riyadh, 28, 23, 30)
        assertEquals(WakePlan.deadlineAfter(start, 420, riyadh), WakePlan.deadlineAfter(start, 420, istanbul))
    }

    @Test
    fun `backup alarm leaves one hour to fall asleep`() {
        val start = 1_000_000L
        val goal = 7 * 3_600_000L
        assertEquals(start + goal + WakePlan.MAX_SLEEP_LATENCY_MS, WakePlan.target(start, null, goal, null))
    }

    @Test
    fun `detected sleep counts from the onset`() {
        val start = 1_000_000L
        val onset = start + 20 * 60_000L
        val goal = 7 * 3_600_000L
        assertEquals(onset + goal, WakePlan.target(start, onset, goal, null))
    }

    @Test
    fun `an earlier deadline wins, a later one does not`() {
        val start = 1_000_000L
        val onset = start + 20 * 60_000L
        val goal = 7 * 3_600_000L
        assertEquals(onset + goal - 1, WakePlan.target(start, onset, goal, onset + goal - 1))
        assertEquals(onset + goal, WakePlan.target(start, onset, goal, onset + goal + 1))
    }

    @Test
    fun `battery below twenty percent is too low unless charging`() {
        assertTrue(BatteryCheck.isTooLow(19, charging = false))
        assertFalse(BatteryCheck.isTooLow(19, charging = true))
        assertFalse(BatteryCheck.isTooLow(20, charging = false))
        // 0 means the level could not be read: do not block the user on a guess
        assertFalse(BatteryCheck.isTooLow(0, charging = false))
    }
}
