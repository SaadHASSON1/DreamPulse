package com.x13labs.dreampulse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SleepDetectorTest {

    private val start = 1_000_000_000L
    private fun min(m: Int) = start + m * 60_000L

    /** Heart-rate bursts like the service's duty cycle: a few samples every 5 minutes. */
    private fun SleepDetector.hrBursts(fromMin: Int, toMin: Int, bpm: Float) {
        var m = fromMin
        while (m <= toMin) {
            repeat(5) { i -> onHeartRate(min(m) + i * 10_000L, bpm) }
            m += 5
        }
    }

    @Test
    fun `movement keeps resetting stillness so nothing is detected`() {
        val d = SleepDetector(start)
        d.onWorn(start, true)
        for (m in 0..60 step 5) {
            d.onMotion(min(m))
            assertNull(d.evaluate(min(m) + 1))
        }
    }

    @Test
    fun `fifteen still minutes with a heart-rate drop is sleep, dated to the start of stillness`() {
        val d = SleepDetector(start)
        d.onWorn(start, true)
        d.hrBursts(0, 9, 70f)          // baseline 70
        d.onMotion(min(10))            // last movement: 10 min in
        d.hrBursts(15, 25, 64f)        // about 9% lower while still
        assertNull(d.evaluate(min(24)))
        val decision = d.evaluate(min(25))
        assertNotNull(decision)
        assertEquals(min(10), decision!!.onset)
        assertEquals("stillness+heart-rate", decision.source)
    }

    @Test
    fun `stillness without a heart-rate drop waits for the long rule`() {
        val d = SleepDetector(start)
        d.onWorn(start, true)
        d.hrBursts(0, 40, 70f)         // no drop at all (e.g. lying awake)
        d.onMotion(min(5))
        assertNull(d.evaluate(min(25)))    // 20 min still, no drop: not yet
        val decision = d.evaluate(min(30)) // 25 min still: long-stillness rule
        assertNotNull(decision)
        assertEquals(min(5), decision!!.onset)
        assertEquals("long stillness", decision.source)
    }

    @Test
    fun `no heart rate at all still ends with the long rule`() {
        val d = SleepDetector(start)
        d.onWorn(start, true)
        assertNull(d.evaluate(min(20)))
        assertNotNull(d.evaluate(min(25)))
    }

    @Test
    fun `off the wrist never counts and putting it back on restarts the clock`() {
        val d = SleepDetector(start)
        d.onWorn(start, false)
        assertNull(d.evaluate(min(40)))    // 40 min on a table: nothing
        d.onWorn(min(40), true)
        assertNull(d.evaluate(min(60)))    // only 20 min worn and still
        val decision = d.evaluate(min(65))
        assertNotNull(decision)
        assertEquals(min(40), decision!!.onset)
    }

    /**
     * Real night, 2026-09-27 (Galaxy Watch8): Start at 04:50:29 with 74 bpm, last movement
     * 04:51:30, then 48-51 bpm while still. Samsung Health: asleep at 04:54. The old detector
     * averaged the first 10 minutes into its "awake" baseline (49 bpm) and dated sleep 05:08.
     */
    @Test
    fun `real night - fast sleeper is dated to the start of stillness`() {
        val t0 = 1_790_473_829_997L                     // 04:50:29
        fun at(m: Int, s: Int = 0) = t0 + m * 60_000L + s * 1000L
        val d = SleepDetector(t0)
        d.onWorn(t0, true)
        repeat(8) { i -> d.onHeartRate(at(1, i * 5), 74f) }        // first burst, awake
        d.onMotion(at(1, 1))                                         // 04:51:30 last movement
        listOf(6 to 48f, 7 to 49f, 11 to 49f, 12 to 50f, 16 to 50f, 17 to 51f).forEach { (m, bpm) ->
            repeat(5) { i -> d.onHeartRate(at(m, i * 10), bpm) }
        }
        assertNull(d.evaluate(at(10)))                                // 9 min still: not yet
        val decision = d.evaluate(at(11, 5))                          // 10 min still, 33% drop
        assertNotNull(decision)
        assertEquals(at(1, 1), decision!!.onset)                     // 04:51:30, not 05:08
        assertEquals("stillness+big heart-rate drop", decision.source)
    }

    @Test
    fun `late system signal is dated to the start of stillness`() {
        val d = SleepDetector(start)
        d.onWorn(start, true)
        d.onMotion(min(2))
        assertEquals(min(2), d.onsetForSystemSignal(min(18)))        // still 16 min
        d.onMotion(min(20))
        assertEquals(min(22), d.onsetForSystemSignal(min(22)))       // only 2 min still: now
    }

    @Test
    fun `smart wake ignores a single twitch but fires on two separate movements`() {
        val g = SmartWakeGate()
        assertFalse(g.onMotion(min(0)))
        assertFalse(g.onMotion(min(0) + 5_000))      // same twitch, 5 s later
        assertFalse(g.onMotion(min(0) + 20_000))
        assertTrue(g.onMotion(min(1)))                // a second movement a minute later
    }

    @Test
    fun `smart wake forgets movements older than the window`() {
        val g = SmartWakeGate()
        assertFalse(g.onMotion(min(0)))
        assertFalse(g.onMotion(min(5)))               // 5 min later: the first one expired
    }

    @Test
    fun `implausible heart rates are ignored`() {
        val d = SleepDetector(start)
        d.onHeartRate(min(1), 0f)
        d.onHeartRate(min(1), 250f)
        d.onHeartRate(min(1), 12f)
        assertNull(d.baseline())
    }
}
