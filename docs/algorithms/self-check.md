# Self-check: the app reading its own readings

OpenStrap's scores are models, and a model can be wrong for one person on one day in a way that no reader would believe: Energy stuck at 0 from 16:30 to 21:47 on 975 steps after a ten hour night, or a Recovery that jumps 40 points while HRV, resting heart rate and sleep did not move. The self-check looks for those readings, learns a **small, bounded** correction where the model has a knob that fits, and says so. It never touches stored data and never changes a number that was measured: only the Energy model's own constants move, and only within the bounds below.

Code: `data/selfcheck/` (pure, unit tested) with `SelfCheckRunner` and `SelfCheckKeeper` as the only parts that read the database. Words: `presentation/SelfCheckCopy.kt`, `res/values*/strings_selfcheck.xml`. Page: You > App > Self-learning.

## How a pass runs

1. **When.** `SelfCheckKeeper.tick()` runs from the 15 minute `CoachWorker`, two minutes after new data lands, and 90 seconds after the app starts. The heavy pass is gated by `SelfLearning.due`: never twice within 30 minutes, the first of each day, and then every 3 hours. It waits while a workout is recording or the strap's firmware is installing, and does nothing at all when the switch is off.
2. **Facts.** The last 4 days of Energy are replayed minute by minute under the **published model** (the check-in calibration's tuning, without what was learned), and the nights, Readiness, calories and strap state of the last 10 days are read from the cached Recovery. Detection runs on the published model on purpose: a correction that works keeps being seen as needed for as long as the model alone would be wrong, so it is held at full strength instead of fading and coming back.
3. **Checks.** Every `Check` in `SelfChecks.all` is asked; each returns `Finding`s with a severity, its numbers and a plan: inform only, a direct set, or a search.
4. **Plan.** For one day, all the findings with a search plan are solved together by `SelfPlanner`: each knob they name is tried at its default, halfway and at the end the direction points to, and the combination with the smallest total change (as a share of each knob's range, then the fewest knobs) that makes every finding of the day go quiet, without a new one appearing, wins. At most 160 replays; if nothing works the finding is only recorded. This is the least-change idea of the calibration's ridge penalty (`EnergyCalibration`) without a fit: the published model is the prior and moves only as far as the day demands.
5. **Settle.** `SelfLearning.settle` joins a finding to the incident of the same check within 3 days, or starts one. Only Medium and High findings with a change are applied; Low ones and findings with nothing to change are recorded. An incident the user undid is not applied again in the same episode.
6. **Tell.** `SelfLearning.nextNotice`: one notification per incident, at most 2 a day, not in the user's quiet hours, Do Not Disturb or before 08:00 and from 22:00, only while the evidence is no older than a day, and only for an applied Medium or High incident or a High one nothing could change. Low importance, with an Undo button when something changed.

The result is stored as one text value (`self_check_state`, version line, run line, told days, one line per incident, 40 kept). The learned tuning is **derived** from it, never stored: undoing an incident, resetting, or the days passing is enough to give the published model back.

## What may change (the knobs)

| Knob | What it is | Published | Bounds | Search goes towards |
|---|---|---|---|---|
| `alert` | share of the body-signal Alert's effect on Energy kept (recharge x0.75, drain x1.25 at 1) | 1 | 0 to 1 | 0.5, 0 |
| `active` | scale on the drain of Activity and Exercise minutes (on top of the check-in `exercise`) | 1 | 0.6 to 1.4 | 0.8, 0.6 (or 1.2, 1.4) |
| `rest` | scale on the drain of a still, awake Rest minute | 1 | 0.5 to 1.5 | 0.75, 0.5 (or 1.25, 1.5) |
| `cap` | the longest run of raised heart rate without steps counted as exercise, minutes | 90 | 30 to 90 | 60, 30 |
| `rhr` | bpm added to the daytime resting heart rate Energy measures reserve against | 0 | 0 to 8 | 4, 8 |

All five are in `ChargeTuning` beside the check-in multipliers (`sleep`, `exercise`, `carry`, which keep their own bounds 0.7 to 1.3 and their own source). `ChargeTuning.withLearned` and `learnedPart` keep the two sets apart: the calibration never sees a learned value as its own, and a learned value never overwrites a check-in one. With every knob at its default the model gives results identical to the published one (checked against the code before this change, and `SelfTuningModelTest` pins the knobs' effects).

Every value is snapped to its grid (0.05, 5 minutes, half a beat) and clamped both when it is chosen and when it is read back from storage.

### Decay

A correction is at full strength on the last day an incident showed the problem (`last`), and fades **linearly to the published value over 14 days** after it (`SelfLearning.factor`). A finding on a later day moves `last` forward, so a problem that keeps showing keeps its correction. When two applied incidents set the same knob, the one with the later `last` wins.

### Undo and reset

Undo takes one incident's change back and keeps it listed as Undone. Reset all takes every change back at once. Remove deletes a recorded or undone incident from the list (an incident still at work is undone instead). A switch off gives the published model at once and keeps the list.

## The checks

Each check is one small class, with its thresholds as named constants, tested on an implausible fixture and on a healthy one. Severity: **Low** is listed and nothing more; **Medium** may adjust and is told when it did; **High** is told even when nothing could be changed.

### Energy (may adjust)

| Id | Sees | Severity | May change |
|---|---|---|---|
| `energy.stuck_empty` | Energy at 0 (1 or under) for 180 minutes or more while awake, and no 90 minute workout or 8 000 steps in the 3 hours up to it. 300 minutes or more is High | Medium, High | `alert`, `active`, `rest`, `cap`, `rhr` toward gentler |
| `energy.stuck_full` | Energy at 99 or more for 360 minutes or more awake with 4 000 steps or more in them | Medium | `active`, `rest` toward stronger |
| `energy.low_wake` | Energy at wake under 55 after 8 hours or more asleep, strap not off overnight. Under 40 is High | Medium, High | as `stuck_empty` |
| `energy.drain_fast` | in some 2 hour window, the points lost in Activity and Exercise minutes exceed 25 + 4 per 1 000 steps + 0.6 per workout minute, and are at least 28 (45 is High) | Medium, High | `active`, `cap`, `rhr`, `alert` toward gentler |
| `energy.alert_streak` | the body-signal Alert in force for 4 nights or more in a row (7 or more is High) | Medium, High | `alert` set to 0.5 (4 nights) or 0.25 (7 or more), directly |

### Heart rate, steps, calories, strap (record and tell, mostly)

| Id | Sees | Severity | May change |
|---|---|---|---|
| `heart.sitting_as_effort` | still awake minutes (no step, no workout) counted as Activity or Exercise: 90 minutes or more and 12 % of the still minutes; 150 or more is Medium | Low, Medium | `rhr`, `cap`, `active` toward gentler |
| `heart.peak_over_max` | 3 minutes or more at 10 bpm over the maximum held; 10 or more is Medium | Low, Medium | nothing (the maximum is learned from real peaks and follows them) |
| `steps.no_response` | 1 500 steps or more, and 35 % of the day's, in minutes with 30 steps or more and no heart response (reserve 4 % or less), on a finished day | Low | nothing |
| `steps.mostly_false` | 3 000 false steps or more, and 60 % of the day's, on a finished day | Low | nothing |
| `calories.pal` | a finished day whose activity level (total over resting) is outside 1.2 to 2.5 | Medium | nothing |
| `strap.gaps` | 2 hours or more a day without heart rate that nothing explains (strap off the wrist, off overnight), on 2 of the last 3 days | Medium | nothing |
| `strap.battery` | 5 % or less and not charging | Low | nothing |

### Sleep and Readiness (record and tell)

| Id | Sees | Severity |
|---|---|---|
| `sleep.implausible` | a night under 2 hours or over 14, or deep plus REM more than the sleep (15 minutes slack), in the last 3 nights | Medium |
| `sleep.score_gap` | OpenStrap's sleep score 30 points or more from the strap's, the same way, on 3 of the last 5 nights | Low |
| `sleep.midpoint_jump` | the latest night's midpoint 4 hours or more from the circular mean of the 14 before (7 or more needed) | Low |
| `readiness.jump` | 36 points or more from one day to the next while HRV, resting heart rate and sleep each moved under 15 (two of them present) | Medium |
| `readiness.stuck` | the same score (a point either way) on all of the last 7 days, 6 or more scored | Medium |

Nothing about sleep or Readiness is learned: stages are displayed, never scored, and the Readiness weights belong to the morning check-ins (`readiness-calibration.md`).

## What it does not do

* It never writes to the database, never edits stored readings, never writes a Room migration.
* It does not tune anything it cannot replay: a finding with no knob is recorded and, when High, told.
* It does not run during a workout, and a failed pass counts as a run, so it is not retried every quarter hour.
* It is not a medical check. Everything it says is about whether the app's own estimates are plausible.

## Adding a check

Add a class implementing `Check` (or `DayCheck` when it reads one `DayTrace`, so the planner can ask it again of a replayed day), list it in `SelfChecks.all` with a unique id of the form `domain.what`, add its words to `SelfCheckCopy.seen` and `strings_selfcheck.xml` (English and Arabic, isolates around the placeholders), and give it an implausible and a healthy fixture in the tests. A check that throws is skipped, so one bad check never stops the others.
