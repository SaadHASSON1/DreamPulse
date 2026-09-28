package com.x13labs.dreampulse.data.local

import android.content.Context
import android.os.BatteryManager
import java.io.File

/**
 * Finished nights, one CSV line each, in app-private storage (never leaves the watch).
 *
 * Line format (times are epoch milliseconds, 0 = unknown):
 *   sessionStart,sleepOnset,source,wake,scheduledWake,goalMinutes,batteryStart,batteryEnd
 */
object NightHistory {

    data class Night(
        val sessionStart: Long,
        val sleepOnset: Long,
        val source: String,
        val wake: Long,
        val scheduledWake: Long,
        val goalMinutes: Int,
        val batteryStart: Int,
        val batteryEnd: Int,
    ) {
        val sleptMinutes: Int? get() = if (sleepOnset in (sessionStart until wake)) ((wake - sleepOnset) / 60_000).toInt() else null
        val latencyMinutes: Int? get() = if (sleepOnset > sessionStart) ((sleepOnset - sessionStart) / 60_000).toInt() else null
        val batteryUsed: Int? get() = if (batteryStart > 0 && batteryEnd > 0) batteryStart - batteryEnd else null
    }

    private const val KEEP = 30

    private fun file(context: Context) = File(context.filesDir, "nights.csv")

    @Synchronized
    fun append(context: Context, night: Night) {
        val f = file(context)
        val line = listOf(
            night.sessionStart, night.sleepOnset, night.source.replace(',', ' '), night.wake,
            night.scheduledWake, night.goalMinutes, night.batteryStart, night.batteryEnd,
        ).joinToString(",")
        val lines = (if (f.exists()) f.readLines() else emptyList()) + line
        f.writeText(lines.takeLast(KEEP).joinToString("\n") + "\n")
    }

    /** Newest first. */
    @Synchronized
    fun last(context: Context, count: Int = 7): List<Night> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull(::parse).takeLast(count).reversed()
    }

    private fun parse(line: String): Night? {
        val p = line.split(',')
        if (p.size < 8) return null
        return runCatching {
            Night(p[0].toLong(), p[1].toLong(), p[2], p[3].toLong(), p[4].toLong(), p[5].toInt(), p[6].toInt(), p[7].toInt())
        }.getOrNull()
    }

    fun batteryLevel(context: Context): Int =
        runCatching {
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(0)
}

/**
 * Per-minute signals while waiting for sleep, one CSV file per session, for tuning the
 * detector against real nights. Kept for the last 7 sessions in the app's own external
 * files folder (other apps cannot read it; adb can, even on a release build), and echoed
 * to logcat under the NightLog tag.
 *
 * Columns: time,motionBursts,heartRate,worn,stillMinutes
 *   worn is 1 (on wrist), 0 (off) or empty (unknown); heartRate is empty when not sampled.
 */
object NightLog {
    private const val KEEP_FILES = 7

    private fun dir(context: Context) =
        (context.getExternalFilesDir("nightlogs") ?: File(context.filesDir, "nightlogs")).apply { mkdirs() }

    @Synchronized
    fun append(context: Context, sessionStart: Long, line: String) {
        val f = File(dir(context), "session-$sessionStart.csv")
        if (!f.exists()) {
            f.writeText("time,motionBursts,heartRate,worn,stillMinutes\n")
            dir(context).listFiles()?.sortedBy { it.name }?.dropLast(KEEP_FILES)?.forEach { it.delete() }
        }
        f.appendText(line + "\n")
        android.util.Log.d("NightLog", "$sessionStart,$line")
    }
}
