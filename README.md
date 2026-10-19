# Tea Timer

An Android app for gongfu brewing: enter each tea's sequence of steep times once,
then step through the infusions from a home screen widget.

Built for GrapheneOS. It needs no Google services.

## How it should work

- Each **sequence** is a tea at an intensity, with its list of steep times
  (for example `0:15, 0:20, 0:25 … 7:00`).
- The **widget** shows the selected sequence, the infusion number and the next time.
  - **Tap:** start the next infusion's timer. When it finishes, the phone rings or
    vibrates and the widget moves on to the next time.
  - **Long-press / settings:** choose which sequence to brew, or reset to infusion 1.
- The timer must fire reliably with the screen off (exact alarm + notification).
- The existing list of brewing parameters is imported once instead of typed in.

The full feature list, open questions and build order are in [FEATURES.md](FEATURES.md).

## Status

Project skeleton only:

- An app screen that lists a sample sequence (`model/BrewingSequence.kt`).
- A placeholder widget that opens the app (`widget/TeaTimerWidget.kt`).
- No timer, storage or import yet.

The project has **not been built yet**: this PC doesn't have the build tools installed.

## Next steps

1. Install the JDK (21) and the Android SDK (command-line tools or Android Studio).
2. Generate the Gradle wrapper scripts (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
   `gradle/wrapper/gradle-wrapper.properties` already pins Gradle 9.8.1.
3. Build a debug APK and install it on the phone.
4. Store sequences on the phone and import the real brewing list.
5. Timer: exact alarm, notification, sound/vibration.
6. Widget: tap to start the next infusion, live countdown, sequence picker.

## Project layout

```
app/src/main/java/app/teatimer/
  MainActivity.kt          app screen (Jetpack Compose)
  model/BrewingSequence.kt sequence data model
  ui/theme/Theme.kt        colours (follows the phone's Material You colours)
  widget/TeaTimerWidget.kt home screen widget (Jetpack Glance)
```

Stack: Kotlin 2.4, Android Gradle Plugin 9.4, Jetpack Compose, Glance.
Minimum Android 12 (API 31).
