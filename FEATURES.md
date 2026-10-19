# Tea Timer: feature list

The features wanted for the app, with notes on how each could be built.
Items marked **(open)** still need a decision.

## Core

- **Sequences:** each tea and intensity has a fixed list of steep times
  (between 0:15 and 7:00), entered or imported once.
- **One tap per infusion:** a tap starts the current infusion. When it ends, the
  next one is lined up, ready for the next tap.
- **Reliable alarm:** rings or vibrates with the screen off, using an exact
  alarm and a notification.
- **Runs on GrapheneOS** without Google services.

## 1. Widget can fill the whole home screen

The widget can be resized from a small strip up to the full home screen grid.

- Android lets a widget be resized up to whatever grid the launcher has, so no
  fixed size is needed.
- The layout adapts to the size (Glance's "responsive" sizes):
  - **Small:** the tea name, the infusion number (e.g. "3 / 11") and the
    next time or countdown.
  - **Large:** the same, plus the whole sequence with the current step
    highlighted, and big buttons that are easy to hit with wet hands.

## 2. Organized by tea type → tea → intensity

- Three levels: **type** (Oolong, Pu-erh, Green, …) → **tea** (Da Hong Pao, …) →
  **intensity** (light / medium / strong, …), and each intensity has its own
  sequence.
- In the app, each tea type gets its own tab. A tab lists that type's teas, and
  each tea shows its intensities as buttons.
- The data model in `model/BrewingSequence.kt` already stores tea and
  intensity. It needs a field for the tea type.

## 3. Importing new tea lists

Goal: you ask Claude for a new tea, and it ends up in the app with little effort.

**Proposed: the tea lists live in their own GitHub repo**, separate from the app's
code. Teas can then change without touching the app, and the app repo can stay
private.

- Each tea is a small JSON file, with an index file listing all of them.
- The app gets an **"Update teas"** button that downloads the index and any new or
  changed teas from GitHub. The tea repo has to be public for this; the tea
  lists aren't private, so that's fine.
- The repo address is a setting in the app, so it can be changed later.
- **(open)** Name of the tea repo (e.g. `TeaTimer-Teas`). It gets created once the
  JSON format is settled.
- Adding a new tea would work like this:
  1. You ask Claude for a tea.
  2. Claude writes the JSON file and commits it.
  3. You push.
  4. You tap "Update teas" in the app.
- The app only needs internet access for this one button. GrapheneOS lets you
  switch the app's network permission off whenever it isn't needed.

**Also: import a file directly** (open or share a `.json` file into the app),
for when you don't want to go through GitHub.

**(open)** Can teas also be edited inside the app? Or does editing always go
through the JSON files?

## 4. Using the widget without opening the app

This is possible: a widget can show scrollable lists and buttons, and react to
taps without opening the app.

**Widget states:**

| State | Shows | Tap |
|---|---|---|
| **Choose** (starting state) | Browse lists: type → tea → intensity, with a back button | Choosing an intensity moves to **Ready** |
| **Ready** | Tea, infusion number, next time, **Start** | Start → **Running** |
| **Running** | Live countdown | Tap → **Paused**; double tap → restart this infusion (**Ready**) |
| **Paused** | Time left | Tap → **Running** again |
| **Infusion done** | Alarm rings; widget shows the next time | Moves on to **Ready** for the next infusion by itself |
| **Last infusion done** | Alarm rings | Back to **Choose** by itself |

- **Reset** button, visible in every state except Choose: jumps straight back to
  **Choose**.
- If browsing in the widget turns out too clumsy, the fallback is a **Start**
  button in the Choose state that opens the app to pick the sequence. After
  that the widget works exactly as above.

**Decided (2026-10-19)**
- Tap while running: pause / resume. Double tap: restart the current infusion.
- The alarm rings for a set time, adjustable in the app: 3, 5, 10 or 15 seconds.
- The alarm follows the ringer: sound + vibration, vibration only on vibrate,
  nothing on silent.

**(open)**
- Repeat or skip an infusion (e.g. "+1 infusion", "skip this one")?
- Choice of alarm sound (the prototype uses the phone's default alarm sound).

## Suggested build order

Steps marked ✓ are in the first prototype (0.1.0), not yet tested on the phone.

1. ✓ Install the build tools and build the app. Still to do: get it onto the phone.
2. Data: tea type → tea → intensity → times. ✓ Format and a sample list are
   bundled in the app; the real list still needs importing.
3. ✓ Timer with alarm and notification (Core).
4. ✓ Widget states and Reset (feature 4), with the "open the app to choose"
   fallback.
5. ✓ Tabs in the app (feature 2).
6. Responsive widget sizes up to full screen (feature 1).
7. Browsing inside the widget (feature 4, full version).
8. GitHub update and file import (feature 3).
