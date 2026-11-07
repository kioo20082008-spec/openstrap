# OpenStrap

## Who and what

The owner (waleedhamadallah@gmail.com, GitHub `kioo20082008-spec`) uses OpenStrap, an Android replacement for the Zepp app,
with an Amazfit Helio Strap. He writes Gulf Arabic: **reply in Arabic**, short and plain, and spend few tokens (his
standing preference: "do the full task in high performance with less token"). He judges design by looking: he has
rejected generic, "AI-looking", cheap or repetitive designs more than once, so show mockups (HTML + Playwright
screenshot) before building a new look, and keep to the premium direction below.

## Working setup

- Repo `/home/claude/openstrap`, fork `kioo20082008-spec/openstrap`, branch **`design/navy-arabic`** (the only branch
  the phone follows). No Android SDK in the sandbox: nothing compiles or runs locally, so every change is checked by
  reading, and the real check is CI.
- Two workflows on every push to `design/**`: **APK** (`apk.yml`: builds the debug APK with `-PappVersion=3.0.<run>`, and on
  this branch publishes release `v3.0.<run>` with `RELEASE_NOTES.md` as its notes) and **Tests** (`tests.yml`: unit tests for
  `data.*`, `widget.*`, `presentation.*`, `*DayViewModel*`, `ui.dashboard.*`, `*EnergyFlask*` only). The app updates itself
  from those releases (every 3 h, UNMETERED, installs once per tag and only when no workout is recording). The debug
  keystore `app/redesign-debug.keystore` is fixed so updates install in place; it is public in the repo, so moving it to
  GitHub Secrets is an open decision for the owner.
- A new push cancels the in-progress run: wait for CI to finish before pushing again. After a push, poll with
  `gh run list -R kioo20082008-spec/openstrap -w APK|Tests -L 1` and read failures with `gh run view <id> --log-failed`.
  A failing build is usually a resource error (escape `'` as `\'` in values XML) or a test that asserted the old layout.
- Commit messages end with the attribution lines the session's reminder gives. Put scratch worktrees for subagents under the
  session scratchpad (never inside the repo); `.git/info/exclude` already ignores `/check-*/` and `/chk-*/`.
- Big work goes to background agents in a scratch worktree that return a patch file; apply it with `git apply`, commit,
  push, watch CI. They also can't compile, so expect one round of CI fixes.
- Add a short Arabic entry to `RELEASE_NOTES.md` with each batch of user-visible changes (the app shows it on the Updates
  page, parsed as headings, bullets, bold).
- Not in CI and known to fail already at HEAD: `DesignTokenTest` (DialsHero's Material typography, font sizes, 28dp corner),
  `DetailRulesTest` (`ControlSurface` is `0xFF1C2C50`, the test says `0xFF1F1F22`).

## Design rules

- Palette: Navy `#14213D`, Cream `#F4EDE0`, Coral `#FF6B5B`. Fully **Arabic RTL**, font **Almarai**, **Western digits**
  (`LatinDigits`, locale `nu-latn`; the Application context is deliberately not wrapped, only `MainActivity.attachBaseContext`).
- The look: sky gradient + grain (`ui/glow/Sky.kt`), flat glass cards (`GlowCard.kt`: white 5.5% fill, 10% hairline), thin
  numerals (Inter ExtraLight, in `Ltr { }`), hairline gauges, a glowing point. Today's hero is the **Energy flask**
  (`ui/glow/EnergyFlask.kt`, with the Recovery gauge gone), then a glass trio (at waking, Recovery, Sleep), the plan card and
  the coach card. Recovery tab = "نبض" (heart + ripples + ECG), Sleep = "سماء", Activity = "مسار". Cards are small
  (`UnitHeight` 136dp, `CardInset` 16dp). Energy has no card on Today any more.
- Colours go through `AppColors` (`ui/theme/AppColors.kt`, snapshot-state tokens for four themes: Navy, OLED black, Cream
  light, Warm dusk; setting in You > App > Appearance, stored by `SettingsStore.appTheme`). Don't hard-code `Color.White`/cream/
  navy for text, surfaces or hairlines; use `AppColors.readable(c)` for metric-coloured text so it reads on Cream light.
  Maps, share cards and the Breathe veil stay dark on purpose. The theme work was never looked at on a device.
- RTL traps: wrap anything that must read left to right (numbers, charts, time axes) in `Ltr { }` (`GlowCharts.kt`); use
  `placeRelative`; wrap placeholders in Unicode isolates (U+2068/U+2069) in Arabic strings; Arabic plurals need
  zero/one/two/few/many/other; coach text is Modern Standard Arabic.
- Widgets: `WidgetRtl` mirrors them when the app is Arabic and the launcher is LTR (launcher direction comes from
  `LocaleManager` via `WidgetHost.init`); Arabic text is drawn with `FontText`/`renderNumber`. Six kinds: Recovery, Steps,
  Sleep, Start, Moment, Energy.

## Measurements (all documented in `docs/algorithms/`, `recovery.md` first)

- Max HR Tanaka (learned from real peaks); Banister TRIMP load, 42-day fitness vs 7-day fatigue; Mifflin-St Jeor;
  calories from heart-rate reserve; Tudor-Locke cadence; Jurca VO2max; nightly HRV = ln RMSSD with a 7-night mean against a
  60-night baseline, readings in awake stages and per-night outliers dropped; resting HR = lowest 30-minute mean.
- **Sleep score is OpenStrap's own** (`SleepScore.kt`): duration 45% (NSF 7-9 h band around the sleep need), continuity 30%
  (WASO and awakenings over 5 min), regularity 25% (midpoint against the 14-night circular mean). The strap's own score is only
  shown as a note. Stages are displayed, never scored.
- Sleep balance (debt) is scored on the average nightly shortfall (debt over the decay weights), not the summed debt. Naps
  count toward the next night.
- **False steps** (arm movement at a desk) are removed by `Recovery.falseStepMinutes` from steps, calories and Energy;
  the SQL daily/hourly history, `UsualSteps`, Habit Age and Rewards still use the raw numbers.
- **Energy** (called Charge in code, "الطاقة" in the UI, a body-battery 0-100): sleep charges, awake time drains by load, stress
  and heart rate; only sleep recharges meaningfully.
- Coach (`presentation/Coach.kt`) reads live readings; `device/CoachWatch.kt` (WorkManager, every 15 min) sends stress,
  charge and bedtime notifications. The "looks like a workout" prompt: "Not now" now keeps quiet 45 min, doubling each time.

## Open items

- Owner's choice pending: which features to build next (breathing with a strap buzz; an emulated smart alarm, since the strap
  reports `smartSupported=false`; prayer-time buzz, Umm al-Qura; post-workout HR recovery, the `recovery_hr` table is unused;
  move reminder; morning readiness buzz; Ramadan mode) and the keystore decision above.
- Shadow-mode idea not done: deep-sleep-only HRV window; storing the false-step correction per day so history agrees.
- Compare readings against Zepp / Google Health for a week and report differences (Google Health was the reference for sleep
  timing earlier).

## Widget gallery

A debug-only screen that hosts each home screen widget at its three sizes, with forced moods, moments and strap
states. Its code stays in `app/src/debug/kotlin/org/openstrap/app/widget/debug/WidgetGalleryActivity.kt` (it still
compiles), but its activity is commented out in `app/src/debug/AndroidManifest.xml`, so it isn't installed: while
open, its widgets count as placed and refresh along with the real ones.

To test widgets again:

1. Uncomment the `WidgetGalleryActivity` `<activity>` in `app/src/debug/AndroidManifest.xml`.
2. Build and install the debug app (check no workout is recording first: a `workout` notification channel in
   `adb shell dumpsys notification --noredact | grep "pkg=org.openstrap.app"`).
3. `adb shell appwidget grantbind --package org.openstrap.app --user 0`
4. `adb shell am start -f 0x00020000 -n org.openstrap.app/.widget.debug.WidgetGalleryActivity --es widget moment`
   Extras: `widget` recovery|steps|sleep|start|moment|energy, `mood` day|evening|night|auto, `cells` oneui|min,
   `moment` a moment name or auto, `placed` kinds or none|auto, `strap` notpaired|low|stale|yesterday|week|syncing|none|auto,
   `pin` a kind. A render takes 15 to 20 s.
5. When done, close it with Back (so it unbinds its widgets), then comment the activity out again.
