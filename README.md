# Tea Timer

An Android app for gongfu brewing: enter each tea's sequence of steep times once,
then step through the infusions from a home screen widget.

Built for GrapheneOS. It needs no Google services.

## How it works

- Each **sequence** is a tea at an intensity, with its list of steep times
  (for example `0:15, 0:20, 0:25 … 7:00`).
- **Choose a tea** in the app (one tab per tea type), or tap the widget to open the app
  and choose. Once you've chosen, the app closes again.
- The **widget** then shows the tea, the infusion number and the next time:
  - **Tap:** start the infusion. Tap again to pause or resume.
  - **Double tap:** restart the current infusion.
  - **✕:** stop and go back to choosing a tea.
- When an infusion is done, the phone rings for 3, 5, 10 or 15 seconds (set in the app),
  following the ringer mode, and the widget lines up the next time. After the last
  infusion it goes back to choosing.
- A notification shows the countdown, e.g. on the lock screen.

The full feature list, decisions and build order are in [FEATURES.md](FEATURES.md).

## Status

First prototype (0.1.0): builds, and the unit tests pass, but it **hasn't been
tried on a phone yet**. The tea list is a bundled sample
(`app/src/main/assets/teas.json`), including a "Test" tea with 5-second steps for
checking the alarm quickly.

## Building

Needs JDK 21 and the Android SDK. `local.properties` (not in Git) points to the SDK.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it on the phone by
copying it over and opening it (allow installing from that app when GrapheneOS asks),
or with `adb install` over USB.

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
