# Antigravity Prompt — DreamPulse Reliability Overhaul

> Paste **everything from "## ROLE" down to the end** into Google Antigravity as a single instruction.
> The codebase is already open in the IDE — Antigravity has full file access.

---

## ROLE

You are a senior Android / Wear OS engineer. The user (Saad) has built a Wear OS sleep alarm called **DreamPulse** in `com.smartsleep.alarm`. The app's job: when the user falls asleep, start a countdown of N minutes (set by the user), and ring an alarm exactly N minutes after **physiological sleep onset** — not bedtime. Two failure modes are happening in production on Samsung Galaxy Watch:

1. **Sleep is never detected** → countdown never starts → no alarm.
2. **Sleep is detected but the alarm never fires** → the user oversleeps.

Your job is to fix BOTH failure modes with maximum reliability on Samsung One UI Watch and stock Wear OS, **without rooting, without hidden APIs, without anything that would violate Play Store policy**. Use only documented Android / Wear OS / Health Services APIs and the standard set of "aggressive-battery-management bypass" techniques that every serious alarm and health app uses.

## CRITICAL ROOT-CAUSE FINDINGS (FIX THESE FIRST)

I have already audited the code. These are the confirmed bugs — fix them exactly as described.

### Bug 1 — `startShadowExercise()` is the #1 reason sleep is never detected

In `HealthServicesManager.kt`, `startHeartRateMeasurement()` calls `startShadowExercise()` which starts `ExerciseType.WALKING`. While an exercise session is active, **`UserActivityState` will never become `USER_ACTIVITY_ASLEEP`** because the system classifies the user as exercising. This single bug breaks the entire passive sleep detection pipeline.

**Fix:** Delete `startShadowExercise()` and `stopShadowExercise()` entirely. Remove every call site. To keep the heart-rate sensor warm during tracking we will use **`PassiveMonitoringClient` with `HEART_RATE_BPM`** plus an opportunistic short `MeasureClient` window only during the heuristic check — never an exercise session.

### Bug 2 — `SleepPassiveListenerService` is incomplete

It only overrides `onUserActivityInfoReceived`. It must also override:
- `onNewDataPointsReceived(dataPoints: DataPointContainer)` to forward HEART_RATE_BPM to `HealthServicesManager.updateHeartRate(...)`.
- `onPermissionLost()` → log + post a notification asking the user to re-grant `BODY_SENSORS_BACKGROUND`.
- `onRegistrationFailed(throwable)` → log and re-register with exponential backoff.

Also: `PassiveListenerConfig` must request both `setShouldUserActivityInfoBeRequested(true)` AND `setDataTypes(setOf(DataType.HEART_RATE_BPM))`. It currently does both but the service ignores the data points.

### Bug 3 — `startHeartRateDutyCycle()` has overlapping delays

The method has `delay(60000)` followed by `delay(30000)` outside the `if/else`, producing undefined cadence. Replace with a clean state machine: while sleep is unconfirmed, run `MeasureClient` continuously for the first 90 seconds of every 5-minute window (so the passive HR keeps a recent baseline), and rely on `PassiveMonitoringClient` for the rest. After sleep is confirmed, stop `MeasureClient` entirely and rely only on the AlarmManager schedule.

### Bug 4 — Alarm fails because of system-level restrictions, not the alarm code itself

`AlarmManager.setAlarmClock()` is the correct call. The reason it fails is the OS killing the app or deferring the alarm. Fix the surrounding battery / permissions / Samsung issues (see "RELIABILITY HARDENING" below).

### Bug 5 — `AlarmReceiver.startActivity()` from background is illegal on Android 10+

Move activity launch entirely into the **full-screen-intent notification** path. The receiver must:
1. Acquire a partial wake lock with timeout.
2. Start `AlarmService` as a foreground service.
3. NOT call `startActivity()` directly. The full-screen-intent on the foreground notification is what brings up `AlarmActivity` over the lockscreen.

### Bug 6 — Backup alarm uses `setExactAndAllowWhileIdle`

This is deferrable on Samsung. Replace with a second `setAlarmClock` (different request code) at `targetWakeTime + 30 min`. `setAlarmClock` has the highest priority and shows up in the system's alarm icon — Samsung respects it.

### Bug 7 — `stopForeground(true)` is deprecated and `triggerAlarmNow()` has a race

Replace with `stopForeground(Service.STOP_FOREGROUND_REMOVE)`. In `triggerAlarmNow()`, do **not** call `stopSelf()` immediately after `sendBroadcast()`. Instead post the stop with a 2-second `Handler` delay to let the broadcast be received.

### Bug 8 — Missing runtime permission requests

In `MainActivity`, on first launch (and every cold start while not granted) request, in this order:
1. `BODY_SENSORS` (foreground)
2. `BODY_SENSORS_BACKGROUND` (only AFTER #1 is granted; uses a separate request)
3. `ACTIVITY_RECOGNITION`
4. `POST_NOTIFICATIONS`
5. **`SCHEDULE_EXACT_ALARM`** — check `AlarmManager.canScheduleExactAlarms()`; if false, send the user to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
6. **`USE_FULL_SCREEN_INTENT`** — on Android 14+ check `NotificationManager.canUseFullScreenIntent()`; if false, send the user to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.

If any of these are missing, show a blocking dialog explaining why before letting the user start tracking.

### Bug 9 — No state persistence for `isSleepConfirmed`

If Android kills the foreground service and `START_STICKY` restarts it, `isSleepConfirmed`, `serviceStartTime`, and the alarm target are all lost. Persist them to `PreferencesManager` immediately when set, and rehydrate on `onStartCommand`. The alarm itself survives via `AlarmManager`, but the service must reload its state to avoid double-scheduling or losing the smart-window logic.

## RELIABILITY HARDENING — THE "BYPASS-AGGRESSIVE-BATTERY-MANAGEMENT" CHECKLIST

Implement all of these. They are the legitimate techniques used by Sleep as Android, Pillow, Sleep Cycle, and Samsung Health.

### A. Battery optimization exemption
- Check `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
- If false, on first run launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `Uri.parse("package:$packageName")`.
- Do NOT use `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` because Play Store rejects it for non-alarm apps; we are an alarm app, so the targeted request is allowed and proper.

### B. Samsung "Sleeping apps" exception (the silent killer)
There is no API to programmatically add the app to Samsung's "Never sleeping apps" list. So:
- Detect Samsung devices via `Build.MANUFACTURER.equals("samsung", ignoreCase = true)`.
- On first run on Samsung, show a one-time onboarding screen with step-by-step instructions and an `Intent` button that opens **Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps** (`Intent("android.settings.APPLICATION_DETAILS_SETTINGS")` is the closest reliable deep-link).
- Persist a flag `samsung_battery_briefing_shown` so it's not shown again.

### C. Foreground service typing
The manifest already has `foregroundServiceType="health"` for `SleepMonitorService` and `specialUse` (subtype `alarm`) for `AlarmService`. Verify both `startForeground(...)` calls pass the matching `FOREGROUND_SERVICE_TYPE_*` constant on Android 10+, otherwise Android 14 throws `ForegroundServiceTypeException`. `AlarmService` currently calls `startForeground(1, notification)` without the type → fix it to pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.

### D. Wake-lock discipline
- The 9-hour `PARTIAL_WAKE_LOCK` in `SleepMonitorService.onCreate()` is acceptable but excessive. Replace with `WakefulBroadcastReceiver`-style handoffs: short wake locks (max 15 min) during heuristic windows, released and re-acquired on each duty cycle.
- The `SCREEN_BRIGHT_WAKE_LOCK` in `AlarmReceiver` (15s) is fine but never explicitly released. Add `wakeLock.setReferenceCounted(false)` and release in a `Handler.postDelayed { if (wakeLock.isHeld) wakeLock.release() }` 14 seconds later.

### E. AlarmManager belt-and-suspenders
For every confirmed sleep session, schedule **three** alarms with three different request codes:
1. Primary: `setAlarmClock` at `targetWakeTime`. (Already exists.)
2. Backup-1: `setAlarmClock` at `targetWakeTime + 5 min` — survives if primary is somehow swallowed.
3. Backup-2: `setAlarmClock` at `targetWakeTime + 30 min` — survives even worst-case OEM doze.

When the user dismisses the alarm in `AlarmActivity`, cancel all three.

### F. WorkManager heartbeat
Add a `PeriodicWorkRequest` with `Constraints.NONE` and 15-minute interval that, while a sleep session is active, simply checks `serviceStartTime` and `isSleepConfirmed` — and if the foreground service has died, restart it. This is your insurance against Samsung's app freeze.

### G. Boot recovery
`BootReceiver` already restarts the service on boot. Two fixes:
- After `startForegroundService()`, the service has 5s to call `startForeground()` or it crashes. Audit `SleepMonitorService.onStartCommand()` — `startForeground(...)` happens after `setupSensors()` etc.; move it to the very first lines of `onStartCommand`, before any other work, exactly like `AlarmService` does.
- Also handle `Intent.ACTION_LOCKED_BOOT_COMPLETED` and `Intent.ACTION_MY_PACKAGE_REPLACED` to recover after app updates.

### H. Improved sleep heuristic
Replace the fixed `<75 BPM` threshold with a **personal baseline**:
- During the first 5 minutes after `serviceStartTime`, collect HR samples and compute `baseline = average`.
- "Resting HR" condition is now: `currentHR < baseline - 8 BPM` AND `motionSilence > 5 min`.
- Lower the accelerometer motion-event threshold from 0.15 to 0.08 m/s², but require **3 distinct events within 4 seconds** to count as real motion. This kills false-positives from sensor noise without missing true wake events.
- Keep the absolute 10-min motion timeout as the safety catch-all, and the 45-min "fallback" — but raise the fallback's `timeSinceLastMotion` requirement to 5 min (from 3) to avoid false confirms when the user is just lying still watching their phone.

### I. Smart wake window correctness
`isSmartWindowActive` flips to true 15 min before `targetWakeTime`. The current code triggers the alarm on **any motion** in that window, which means a single roll-over wakes the user 15 min early. Require the same "3 motion events in 4 seconds" rule used by the heuristic so that only real waking motion triggers it.

## SPECIFIC FILE-BY-FILE TODO LIST

Work through these in order. Do **not** invent new architecture — keep the existing Hilt-based MVVM. Just fix what's there.

### `app/src/main/AndroidManifest.xml`
- No new permissions needed. Verify `tools:node="merge"` not needed.
- Add `<receiver>` for `Intent.ACTION_LOCKED_BOOT_COMPLETED` and `Intent.ACTION_MY_PACKAGE_REPLACED` on `BootReceiver`.

### `data/sensors/HealthServicesManager.kt`
- Delete `exerciseClient`, `startShadowExercise()`, `stopShadowExercise()`, all references.
- Rewrite `startHeartRateDutyCycle()` cadence (see Bug 3).
- Add `unregisterAll()` cleanup that is idempotent.

### `data/sensors/SleepPassiveListenerService.kt`
- Add `onNewDataPointsReceived(...)` → forward HR.
- Add `onPermissionLost()` and `onRegistrationFailed()`.

### `service/SleepMonitorService.kt`
- Move `startForeground(...)` to the top of `onStartCommand`.
- Replace `stopForeground(true)` with `stopForeground(STOP_FOREGROUND_REMOVE)`.
- In `triggerAlarmNow()`, post `stopSelf()` with 2s delay.
- Persist `isSleepConfirmed`, `serviceStartTime`, `targetWakeTime` to `PreferencesManager`.
- Rehydrate state at the top of `onStartCommand` if `serviceStartTime > 0` and `isSleepConfirmed == true`.
- Implement personal-baseline heuristic (item H).
- Schedule the three-alarm belt-and-suspenders (item E).
- Update `isSmartWindowActive` motion logic (item I).
- Cap wake-lock duration to 15 min, refresh on each duty cycle.

### `service/AlarmService.kt`
- Pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to `startForeground(...)`.
- Make sure the notification's full-screen intent is what brings up `AlarmActivity`, not a direct `startActivity` call.
- Add a 10-minute auto-stop if the user never dismisses (so the watch doesn't vibrate forever).

### `receiver/AlarmReceiver.kt`
- Remove the direct `context.startActivity(activityIntent)` call. Rely on the full-screen intent from `AlarmService`.
- Properly release the SCREEN_BRIGHT wake lock after a Handler delay.

### `receiver/BootReceiver.kt`
- Handle `LOCKED_BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` actions.

### `ui/MainActivity.kt`
- Implement the full permissions onboarding flow (item, see Bug 8).
- Add the battery optimization request.
- Add the Samsung-specific one-time briefing screen (item B).

### `ui/viewmodel/MainViewModel.kt`
- Expose `permissionsReady: StateFlow<Boolean>` based on the runtime checks above.
- Block `startTracking()` when `permissionsReady.value == false` and emit the right `_permissionRequestEvent`.

### `worker/SleepHeartbeatWorker.kt` (NEW)
- Create this file. `PeriodicWorkRequest` every 15 min, enqueued from `MainViewModel.startTracking()` with `KEEP` policy, cancelled on stop.

## NON-GOALS / DO NOT DO

- Do NOT request root or `su`.
- Do NOT use reflection to call hidden Android APIs (`@hide`, `@SystemApi`).
- Do NOT modify SELinux, framework files, or system properties.
- Do NOT install as a system app or push to `/system`.
- Do NOT use `KILL_BACKGROUND_PROCESSES` or `WRITE_SECURE_SETTINGS`.
- Do NOT bypass `SCHEDULE_EXACT_ALARM` or `USE_FULL_SCREEN_INTENT` — request them properly.
- Do NOT silence the 30-second foreground-service-start timeout by tricks; just call `startForeground()` first thing as the fix demands.

These are not just policy concerns — they will cause the app to crash, get rejected from Play Store, or be force-uninstalled by Samsung Knox / Google Play Protect on real user devices.

## DELIVERABLES

1. All the file-by-file changes above, applied as concrete code edits.
2. Build and resolve every compilation error before stopping.
3. Run `./gradlew assembleDebug` and ensure it succeeds.
4. Print a summary at the end listing: every file changed, every new file created, every TODO that's still open, and a brief test plan for the user to run on their physical Galaxy Watch.

## TEST PLAN THE USER SHOULD RUN AFTER YOUR CHANGES

1. **Permissions flow**: Fresh install → all 6 permissions requested → battery optimization exempt → Samsung briefing shown once.
2. **Live simulation**: Tap "Simulate Sleep" → confirms instantly → alarm fires at the exact target time.
3. **Real sleep, short test**: Set 6-minute goal → put on watch → sit still 5 min → sleep state confirms → alarm fires within 6 min of confirmation.
4. **Real overnight**: 8-hour goal → check that the alarm fires.
5. **Force-stop test**: Start tracking → confirm sleep → force-stop the app → wait → WorkManager heartbeat must restart it AND the alarm must still fire (because it lives in `AlarmManager`).
6. **Reboot test**: Start tracking → confirm sleep → reboot the watch → service restarts → alarm still fires.

When all 6 tests pass on a real Samsung Galaxy Watch, the job is done.
