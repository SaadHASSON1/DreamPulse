<p align="center">
  <img src="web/mark.png" width="112" alt="DreamPulse">
</p>

<h1 align="center">DreamPulse</h1>

<p align="center">
  <b>The alarm that starts counting when you fall asleep, not when you go to bed.</b><br>
  A smart sleep alarm for Wear OS · by X13LABS<br>
  <a href="https://saadhasson1.github.io/DreamPulse/"><b>Website</b></a> · <a href="https://saadhasson1.github.io/DreamPulse/privacy-policy.html">Privacy policy</a>
</p>

<p align="center">
  <a href="https://github.com/SaadHASSON1/DreamPulse/actions/workflows/ci.yml"><img src="https://github.com/SaadHASSON1/DreamPulse/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Wear%20OS-3%2B-4285F4?logo=wearos&logoColor=white" alt="Wear OS 3+">
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-Material%203%20for%20Wear-3DDC84" alt="Compose Material 3">
  <img src="https://img.shields.io/badge/languages-EN%20%C2%B7%20AR%20%C2%B7%20TR-534AB7" alt="Languages">
  <img src="https://img.shields.io/badge/network-none-5DCAA5" alt="No network access">
</p>

<p align="center">
  <img src="docs/screenshots/showcase-en.png" width="100%" alt="DreamPulse on a Galaxy Watch7: setup, duration wheels, waiting for sleep, sunrise alarm, history">
</p>

---

## Why

I sleep after Fajr and I never fall asleep the minute I lie down. Some nights it's 10 minutes, some nights 40, and a normal alarm takes all of that out of my sleep.

So I made DreamPulse for my Galaxy Watch. It watches heart rate and movement and only starts the countdown once I'm actually asleep. Ask for 7 hours, get 7 hours.

## Features

| | |
|---|---|
| **Sleep-onset countdown** | The alarm is set from the moment you fall asleep, not from when you press Start. |
| **Backup alarm from the first second** | Scheduled as soon as you start (goal + 1 h), so you wake up even if sleep is never detected or the watch restarts. |
| **Wake-by time** | Optional hard limit, e.g. "never later than 07:00", whatever time you fell asleep. |
| **Smart Wake** | In the last 15 minutes, a real turn-over (two separate movements) rings the alarm early, while you are in light sleep. A single twitch does not. |
| **Sunrise alarm** | The screen warms from night to dawn; hold anywhere or shake your wrist to stop. |
| **Morning summary and history** | Time slept, time it took to fall asleep (not counted against your goal), minutes woken early, battery used. Last 7 nights. |
| **Made for the watch** | Wheel pickers, swipe navigation, a Tile, an Ongoing Activity chip on the watch face, haptics, Material 3 for Wear. |
| **Three languages** | English, Arabic (full right-to-left layout) and Turkish, with an in-app language picker. |

## How sleep is detected

All logic lives in [`SleepDetector`](app/src/main/java/com/x13labs/dreampulse/domain/SleepDetector.kt), a pure Kotlin class covered by unit tests, including tests built from a real recorded night.

1. **Awake baseline:** the first heart-rate reading after you press Start.
2. **Asleep when:**
   - 10 minutes still and the heart rate is at least 15% below the baseline, or
   - 15 minutes still and at least 5% below it, or
   - the watch's own sleep signal (Health Services), confirmed by 5 minutes of stillness, or
   - 25 minutes of complete stillness, when no heart rate is available.
3. **Only while worn:** with the watch off the wrist, detection pauses and restarts when you put it back on.
4. **Onset time:** halfway between your last movement and the first low heart-rate reading. Replaying the first recorded test night, this gives 04:54, the same minute Samsung Health reported.

## Privacy

DreamPulse has **no internet permission**. Heart rate, motion and sleep history never leave the watch. See the [privacy policy](https://saadhasson1.github.io/DreamPulse/privacy-policy.html).

## Build

Requirements: JDK 17+ and the Android SDK (compileSdk 37). The Gradle wrapper is included.

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug         # debug build
./gradlew assembleProfiling     # release configuration, signed with the debug key (for performance testing)
./gradlew bundleRelease         # Play Store bundle (needs your upload key)
```

Install on a watch over Wi-Fi: enable **Developer options → Wireless debugging**, pair with `adb pair <ip:port>`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```
app/src/main/java/com/x13labs/dreampulse/
├── domain/        SleepDetector, SmartWakeGate (pure, unit-tested)
├── service/       SleepMonitorService (sensors, detection), AlarmService (ringing)
├── receiver/      AlarmReceiver, HeartbeatReceiver (keeps the session alive), BootReceiver
├── data/          DataStore preferences, night history, Health Services
├── tile/          Tile shown next to the watch face
└── ui/            Compose screens: setup, tracking, alarm, summary, history, settings
```

## Compatibility

Wear OS 3 and later (Android 11+), tested on a Galaxy Watch7 (Wear OS 6). Needs a heart-rate sensor.

---

<div dir="rtl">

## بالعربية

أنام بعد صلاة الفجر، ولا أغفو بمجرد أن أضع رأسي على الوسادة، والمنبّه العادي يقتطع تلك الدقائق من نومي. لذلك صنعت **DreamPulse** لساعات Wear OS: لا يبدأ العدّ لحظة ضبطه، بل ينتظر **حتى تغفو فعلاً** (من نبض القلب والحركة)، ثم يعدّ المدة التي طلبتها. طلبت سبع ساعات؟ تنام سبع ساعات حقيقية.

- **منبّه احتياطي** منذ اللحظة الأولى، فتستيقظ حتى لو لم يُكتشف النوم أو أُعيد تشغيل الساعة.
- **موعد استيقاظ إلزامي** اختياري، مثل «ليس بعد السابعة».
- **استيقاظ ذكي** في الربع ساعة الأخيرة إن تقلّبت في نوم خفيف.
- **منبّه شروق** يتوقف بضغطة مطوّلة أو بهزّة من يدك.
- **ملخّص الصباح وسجل آخر سبع ليالٍ.**
- **واجهة عربية كاملة** من اليمين إلى اليسار، مع التركية والإنجليزية.
- **بلا إنترنت:** تبقى بياناتك كلها على ساعتك.

<p align="center"><img src="docs/screenshots/showcase-ar.png" width="100%" alt="شاشات DreamPulse بالعربي"></p>

</div>

---

<p align="center">© 2026 Saad HASSON · X13LABS. All rights reserved.</p>
