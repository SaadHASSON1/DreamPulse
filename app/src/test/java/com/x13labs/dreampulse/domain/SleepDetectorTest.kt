package com.x13labs.dreampulse.domain

import org.junit.Assert.assertEquals
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

    @Test
    fun `implausible heart rates are ignored`() {
        val d = SleepDetector(start)
        d.onHeartRate(min(1), 0f)
        d.onHeartRate(min(1), 250f)
        d.onHeartRate(min(1), 12f)
        assertNull(d.baseline())
    }
}
