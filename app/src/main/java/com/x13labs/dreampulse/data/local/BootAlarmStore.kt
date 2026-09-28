package com.x13labs.dreampulse.data.local

import android.content.Context
import android.os.UserManager

/**
 * The scheduled wake time, mirrored into device-protected storage.
 *
 * After a restart, a watch with a screen lock keeps its normal (credential-encrypted) storage
 * locked until the user unlocks it, so DataStore cannot be read and the alarm would not come
 * back until then: an overnight restart would mean no alarm. This small copy can be read
 * right after boot, before unlocking, so the alarm is re-armed at once.
 */
object BootAlarmStore {
    private const val FILE = "boot_alarm"
    private const val KEY_TARGET = "target_wake"
    private const val KEY_RANG_AT = "rang_at"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveTarget(context: Context, target: Long) {
        prefs(context).edit().putLong(KEY_TARGET, target).apply()
    }

    fun target(context: Context): Long = prefs(context).getLong(KEY_TARGET, 0L)

    /** Called when the alarm rings, even before the watch is unlocked. */
    fun markRang(context: Context, now: Long) {
        prefs(context).edit().putLong(KEY_TARGET, 0L).putLong(KEY_RANG_AT, now).apply()
    }

    fun rangAt(context: Context): Long = prefs(context).getLong(KEY_RANG_AT, 0L)

    fun isUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
