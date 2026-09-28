package com.x13labs.dreampulse.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crashes in a private file on the watch. Nothing is ever sent anywhere:
 * the user can read it in Settings and choose to share a photo of it or pull it over adb.
 *
 * File: filesDir/crash.log, plain text, one block per crash:
 *   === 2026-09-28 04:12:33 · v1.2.0 (17) · SM-L310 · Android 15 · thread main ===
 *   <stack trace>
 */
object CrashLog {
    private const val MAX_BYTES = 32 * 1024
    private const val HEADER = "=== "

    private fun file(context: Context) = File(context.filesDir, "crash.log")

    /** Record uncaught exceptions, then let the default handler crash the app as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun record(context: Context, threadName: String, error: Throwable) {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("v?")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val block = "$HEADER$time · $version · ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · thread $threadName ===\n$trace\n"

        // Throws while the watch is still locked after a restart (no user storage yet)
        val f = file(context)
        val text = (if (f.exists()) f.readText() else "") + block
        f.writeText(if (text.length > MAX_BYTES) text.takeLast(MAX_BYTES) else text)
    }

    fun read(context: Context): String =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun count(context: Context): Int = read(context).lines().count { it.startsWith(HEADER) }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
