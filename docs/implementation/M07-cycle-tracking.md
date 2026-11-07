# M7: Cycle tracking

**Goal:** optional cycle tracking that predicts phases, shows honest food tips, nudges the planner, and keeps cycle data private.
**Users get:** period and symptom logging, predictions with a range, phase-based food suggestions, and a weight chart that knows about water weight.
**Depends on:** M1. The planner bonus needs M6.
**Read first:** [algorithms.md](algorithms.md) A19, A20, A7 (step 4); [data-model.md](data-model.md) (M7).
**New dependencies:** `androidx.biometric:biometric` (app lock).

## Tasks

### M7.1 Switching it on

- [x] Me → **Cycle**. Off by default.
- [x] During setup (M1.2), people who chose "female" see one optional card: "Track your cycle? You'll get food tips for each phase." Anyone can switch it on or off in Me. *As built:* the card is on the last setup step, with a switch (off by default).
- [x] Intro screen: what it does; that the data stays on the phone; that it is not for birth control or getting pregnant.
- [x] Settings: contraception, pregnant, breastfeeding, perimenopause or menopause, usual cycle and period length. *As built:* on the Cycle screen, below the calendar.

### M7.2 Data and predictions (database version 9)

- [x] Tables `cycle_periods` and `cycle_days`, and the `cycle_prefs` DataStore (data-model M7). *As built:* cycle rows are deleted for real (no `deletedAt`): they are private health data and never sync. A second "period started" within 3 days of an open period's start corrects it. Backup format 8 includes both tables. `SyncScope` lists every table as private or shared; a test checks that cycle tables are private.
- [x] Domain: A19 (average, range, irregular cycles, phases, water-retention days, pausing). *As built:* `domain/cycle/Cycles`; A20 in `CycleTips` and `CyclePhaseBonus`. Before 2 complete cycles the range is ± 3 days (not ± 1), so a new user doesn't get a falsely exact date. `CyclesTest` (15 tests).

**Done when:** all A19 test cases pass.
*As built:* they do, in `CyclesTest`.

### M7.3 Calendar and logging

- [x] Month calendar: logged period days, the predicted next period as a range (lighter), and phases as soft colours with a legend. *As built:* a small dot marks days with symptoms or a note; tap a day to log it.
- [x] A Today card: "Day 18 · luteal phase · next period in 9–11 days". *As built:* hidden while the cycle section is locked; tapping it opens Cycle.
- [x] Log: period started / ended, flow, symptoms, a note (the note never leaves the phone).

### M7.4 Tips and the planner

- [x] Phase tips (A20) on the Today card, in neutral, friendly wording, English and Czech.
- [x] Implement `PhaseBonusProvider` (M6.2) with A20, so iron-rich recipes rise in period weeks and magnesium- and fibre-rich ones in the luteal phase. *As built:* `CyclePhaseBonus`, used by **Plan my week** for the phone's user only.
- [x] Recipe badges: "iron-rich", "magnesium-rich". *As built:* on the recipe page, while cycle tracking is on (A20 thresholds per serving: iron ≥ 3 mg, magnesium ≥ 80 mg).
- [x] **No calorie target changes** (see A20).

### M7.5 Water weight

- [x] The weight chart shades likely water-retention days.
- [x] Smart maintenance skips weigh-ins on those days when picking the trend start and end (A7 step 4). *As built:* `CheckinService` passes the water days to A7 (which already supported them since M1.7).

### M7.6 Privacy

- [x] App lock with `BiometricPrompt` (with the device PIN as a fallback): "Lock the cycle section" or "Lock the whole app". *As built:* `androidx.biometric` 1.1.0 (the latest stable); fingerprint, face, or the phone's PIN, pattern or password. It locks again whenever the app leaves the screen. A lock can only be chosen when the phone has a screen lock.
- [x] Cycle tables are never part of household sync (M11), checked by a test. *As built:* `SyncScope` and `SyncScopeTest`; M11 must sync only `SyncScope.SHARED`.
- [x] **Delete all cycle data** with a confirmation. *As built:* deletes every period, day and note and the health settings (contraception, pregnancy, breastfeeding, menopause, usual lengths); whether tracking is on and the lock stay.
- [x] The backup file includes cycle data (it is the user's own), with the health-data warning. *As built:* backup format 8; the export already says "This file contains your health data".
- [x] Research reports include cycle data only with its own separate consent (M10). *As built:* nothing is reported before M10; M10.3 has the separate Cycle switch.

### M7.7 Wording

- [x] "Not for birth control or for getting pregnant. Not medical advice." on the intro and in About.
- [x] Neutral wording throughout; no "you should eat less/more because of your cycle".

## Out of scope

Fertility predictions, a calorie bonus for the luteal phase, and sharing cycle data with the household.
