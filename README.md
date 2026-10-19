# Tea Timer

An Android app for gongfu brewing: enter each tea's sequence of steep times once,
then step through the infusions from a home screen widget.

Built for GrapheneOS. It needs no Google services.

## How it works

- Each **sequence** is a tea, brewed one way (e.g. *Gongfu 90 ml*) at one strength
  (*Light / Medium / Strong*), with its steep times and temperatures
  (for example `0:15, 0:20, 0:25 … 7:00`).
- **Choose a tea** in the app (one tab per tea type), or tap the widget to open the app
  and choose; there, **Select** closes the app again and leaves the tea ready on the widget.
- The tea list comes from the [TeaTimerTimes](https://github.com/GoodGuyTuring/TeaTimerTimes)
  repo; **Update teas** in the app downloads the latest version.
- The **widget** then shows the tea, the infusion number and the next time:
  - **Tap:** start the infusion. Tap again to pause or resume.
  - **Double tap:** restart the current infusion.
  - **✕:** stop and go back to choosing a tea.
  - **Bottom row:** ‹ Prev / Next › jump to the previous or next infusion; the 🔔 chip
    switches the ring duration (3 → 5 → 10 → 15 s), same setting as in the app.
  - The widget's **background colour** is chosen in the app (Wallpaper or a fixed colour).
- When an infusion is done, the phone rings for 3, 5, 10 or 15 seconds (set in the app),
  following the ringer mode, and the widget lines up the next time. After the last
  infusion it goes back to choosing.
- A notification shows the countdown, e.g. on the lock screen.

The full feature list, decisions and build order are in [FEATURES.md](FEATURES.md).

## Status

Prototype (0.2.0), installed on the phone from 0.1.1 on. The bundled tea list
(`app/src/main/assets/teas.json`) is a copy of the TeaTimerTimes list; it includes a
"Test" tea with 5-second steps for checking the alarm quickly.

## Installing on the phone (Obtainium)

Releases are published on GitHub with the APK attached. [Obtainium](https://github.com/ImranR98/Obtainium)
installs them and checks for updates:

1. Install Obtainium (from F-Droid, or the APK from its GitHub releases page).
2. In Obtainium, tap **Add app** and enter `https://github.com/GoodGuyTuring/TeaTimer`.
3. Tap **Install**, and allow Obtainium to install apps when GrapheneOS asks.

When a new release is out, Obtainium shows an update; one tap installs it.

## Building

Needs JDK 21 and the Android SDK. `local.properties` (not in Git) points to the SDK.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Releasing

Release builds are signed with `release.jks`; its password is in `keystore.properties`.
Both are on the development PC only (never in Git). **Keep a backup of both files**:
Android only installs updates signed with the same key, so losing it means uninstalling
the app (and its settings) before the next version can be installed.

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
2. Build: `./gradlew testDebugUnitTest assembleRelease`
3. Publish with the APK attached (Obtainium picks up the newest release):

```bash
gh release create v0.1.0 TeaTimer-0.1.0.apk --title "Tea Timer 0.1.0" --notes "…"
```

where `TeaTimer-0.1.0.apk` is a renamed copy of `app/build/outputs/apk/release/app-release.apk`.

## Project layout

```
app/src/main/
  assets/teas.json             the tea list (format: model/Tea.kt)
  java/app/teatimer/
    MainActivity.kt            app screen: current brew, tea tabs, ring duration
    model/Tea.kt               tea file format, sequences, time parsing
    model/BrewState.kt         brew state machine (ready / running / paused)
    brew/BrewController.kt     owns the state; keeps alarm, notification and widget in step
    brew/AlarmScheduler.kt     exact alarm for the end of an infusion
    brew/AlarmReceiver.kt      alarm fired → next infusion + alert
    brew/AlertService.kt       rings/vibrates for the set duration
    brew/Notifications.kt      countdown and "infusion done" notifications
    widget/TeaTimerWidget.kt   home screen widget (Jetpack Glance)
    ui/theme/Theme.kt          colours (follows the phone's Material You colours)
```

Stack: Kotlin 2.4, Android Gradle Plugin 9.4, Jetpack Compose, Glance,
kotlinx.serialization. Minimum Android 12 (API 31).
