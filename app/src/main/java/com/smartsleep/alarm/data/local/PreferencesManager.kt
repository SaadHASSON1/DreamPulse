package com.smartsleep.alarm.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val SLEEP_DURATION_KEY = intPreferencesKey("sleep_duration_hours")
    private val SLEEP_START_TIME_KEY = longPreferencesKey("sleep_start_time")
    private val USER_NAME_KEY = androidx.datastore.preferences.core.stringPreferencesKey("user_name")
    private val IS_TRACKING_ACTIVE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("is_tracking_active")
    private val LAST_SLEEP_SUMMARY_KEY = androidx.datastore.preferences.core.stringPreferencesKey("last_sleep_summary")
    private val IS_SLEEP_CONFIRMED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("is_sleep_confirmed")
    private val SERVICE_START_TIME_KEY = longPreferencesKey("service_start_time")
    private val TARGET_WAKE_TIME_KEY = longPreferencesKey("target_wake_time")

    val sleepDuration: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SLEEP_DURATION_KEY] ?: 480 // 8 hours in minutes
    }

    val sleepStartTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SLEEP_START_TIME_KEY] ?: 0L
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY] ?: ""
    }

    val isTrackingActive: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_TRACKING_ACTIVE_KEY] ?: false
    }

    val lastSleepSummary: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SLEEP_SUMMARY_KEY] ?: "No data"
    }

    val isSleepConfirmed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SLEEP_CONFIRMED_KEY] ?: false
    }

    val serviceStartTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SERVICE_START_TIME_KEY] ?: 0L
    }

    val targetWakeTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[TARGET_WAKE_TIME_KEY] ?: 0L
    }

    suspend fun saveSleepDuration(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[SLEEP_DURATION_KEY] = minutes
        }
    }

    suspend fun saveSleepStartTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[SLEEP_START_TIME_KEY] = timestamp
        }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun setTrackingActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_TRACKING_ACTIVE_KEY] = active
        }
    }

    suspend fun saveLastSleepSummary(summary: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SLEEP_SUMMARY_KEY] = summary
        }
    }

    suspend fun saveSleepSummary(summary: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SLEEP_SUMMARY_KEY] = summary
        }
    }

    suspend fun saveSleepConfirmed(confirmed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SLEEP_CONFIRMED_KEY] = confirmed
        }
    }

    suspend fun saveServiceStartTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_START_TIME_KEY] = timestamp
        }
    }

    suspend fun saveTargetWakeTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_WAKE_TIME_KEY] = timestamp
        }
    }

    // Hard Deadline ("Must wake by" feature)
    private val HARD_DEADLINE_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hard_deadline_enabled")
    private val HARD_DEADLINE_MINUTES_KEY = intPreferencesKey("hard_deadline_minutes")

    val hardDeadlineEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HARD_DEADLINE_ENABLED_KEY] ?: false
    }

    val hardDeadlineMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[HARD_DEADLINE_MINUTES_KEY] ?: 420 // Default: 07:00 AM
    }

    suspend fun saveHardDeadlineEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HARD_DEADLINE_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHardDeadlineMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[HARD_DEADLINE_MINUTES_KEY] = minutes
        }
    }
}
