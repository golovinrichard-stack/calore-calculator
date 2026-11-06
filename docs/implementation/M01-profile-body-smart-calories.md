# M1: Profile, body and smart calorie tracking

**Goal:** a real profile, body fat with a tutorial, targets by goal speed, smart maintenance with a weekly check-in, hide-numbers mode, and the **Me** tab.
**Users get:** better targets that learn from their own data. All of today's tracking stays exactly as it is.
**Depends on:** M0.
**Read first:** [data-model.md](data-model.md) (M1), [algorithms.md](algorithms.md) A1–A8.
**New dependencies:** WorkManager (`androidx.work:work-runtime-ktx`).

## Tasks

### M1.1 `members` table and moving the profile (database version 3)

- [x] Create `members`, `body_measurements` and `checkins`, and add `diary_entries.memberId` (data-model M1). Also add `members.weightKg REAL NULL`, used only for `MANAGED` members (the phone's user keeps using `weight_entries`).
- [x] On the first start after the update, copy `SettingsStore` into the `ME` row (`MemberRepository.ensureMe`; macro mode becomes `SPLIT` for updated users so the targets stay exactly the same):
  - sex, height, activity, macro split and goals as they are;
  - age → `birthDate` = 1 July of (this year − age), and remember that the user should confirm it (M1.2);
  - `WeightGoal` → `goalType` and `goalRatePct` (A3: `LOSE` → `CUT` 0.5 %, `GAIN` → `BUILD` 0.25 %);
  - the calculator's weight → a weight entry dated today, only if `weight_entries` is empty.
- [x] Remove the old DataStore keys only after the copy has succeeded.
- [x] `MemberRepository` with `me(): Flow<Member>`, `managed()`, `save()`. The backup goes to version 2 and includes the three new tables; a version-1 backup leaves them as they are.

**Done when:** the migration test passes; an upgrade test from v0.1 with saved calculator inputs shows the same targets afterwards.

### M1.2 Setup and profile screens

- [x] **First run** (new install), 4 short steps that can be skipped:
  1. sex, birth date, height, weight;
  2. activity;
  3. goal and speed, showing "−0.4 kg per week ≈ −440 kcal per day";
  4. the result: the targets, and "You can change this any time in Me".
  Skipping keeps today's default goals.
- [x] **Existing users** see a one-time "What's new" card and are asked to confirm the birth date.
- [x] **Profile screen** (Me → Profile), in groups. Fields that are only needed later show "Not set" until then:
  - *Body:* sex, birth date, height, activity.
  - *Goal:* goal type, speed, protein per kg, macro mode (`AUTO` / `SPLIT`), meal split.
  - *Food:* diet, allergens, liked and disliked foods (search picker), cuisines. *Until M2 adds basic foods with keys, liked and disliked foods are picked as kinds of food (shop sections such as meat, fish, legumes), which `likedFoods` already allows; the search picker for single foods comes with M2.*
  - *Cooking:* cooking sessions per week (1–7), cooking days (optional), minutes per session, which meals come from prep boxes, equipment, variety, weekly budget.

**Done when:** a UI test completes setup and sees the new targets on Today; skipping keeps the defaults.

### M1.3 Goals by speed and macros

- [x] Extend `CalorieCalculator`: `GoalType`, speed, Katch-McArdle (A2), targets (A3), `AUTO` macros (A4) and the warnings.
- [x] Calculator screen: goal type, speed chips with kg per week and kcal per day, which formula is used, macro mode.
- [x] Keep `SPLIT` mode working as today.

**Done when:** the A2–A4 test cases pass, and the existing `CalorieCalculatorTest` is updated and passes.

### M1.4 Body fat and the tutorial

- [x] Domain: US Navy formula with validation (A5).
- [x] Tutorial, 5 short screens with simple vector drawings (neck, waist for men, waist for women, hips, tips), in English and Czech:
  - measure in the morning before eating, relaxed, don't pull in the belly;
  - neck just below the Adam's apple; waist at the belly button (men) or the narrowest point (women); hips at the widest point (women);
  - tape flat and level, snug, not pressing; measure 3 times and use the average;
  - "This is an estimate. It can be off by a few percent, but it is good for following the trend."
- [x] Enter measurements → body fat; or "I know my number" (manual entry). Saved in `body_measurements`.
- [ ] After the first measurement, offer a reminder every 4 weeks (the user can choose 2–8 weeks or turn it off).

**Done when:** the A5 test cases pass; after entering body fat, the calculator uses Katch-McArdle.

### M1.5 Body screen

- [ ] Replaces the Weight tab, inside Me: weight log as today, plus the trend line (A6), body-fat history, and the latest measurements.

**Done when:** the chart shows weigh-ins as dots and the trend as a line; the A6 test cases pass.

### M1.6 Notifications

- [ ] Channels, each created when its feature is first switched on: *Progress* (check-ins, measurement reminders), *Freshness* (M4), *Cooking timers* (M4), *Supplements* (M8), *Household* (M11).
- [ ] A helper that asks for `POST_NOTIFICATIONS` (Android 13+) with a short explanation, only when the user turns on something that needs it.

### M1.7 Smart maintenance and the weekly check-in

- [ ] Domain: A7.
- [ ] WorkManager: a weekly job on Monday morning (flexible window) creates the check-in and notifies "Your weekly check-in is ready" (if reminders are on).
- [ ] Check-in screen: trend change, average intake, counted days, estimated maintenance, the proposed target, and the buttons **Use it** / **Keep my target**. Before there is enough data, it says how many more days are needed.
- [ ] History of past check-ins (Me → Body).

**Done when:** the A7 test cases pass; tests with a fake clock check the scheduling; accepting a check-in updates the targets and macros.

### M1.8 Hide-numbers mode

- [ ] Me → Settings: "Hide calories and macros".
- [ ] When on: Today shows foods and meals without numbers or progress bars. Goals and Body show numbers only after tapping "Show numbers". Every later screen follows the same rule (definition of done).

**Done when:** a UI test switches it on and no calorie numbers are visible on Today.

### M1.9 The Me tab

- [x] Bottom bar: **Today · Me**. Today is the current diary screen.
- [x] Me: Profile, Goals and calculator, Body, Settings (hide numbers, backup, language), About.

**Done when:** every existing feature can still be reached; navigation tests pass.

## Out of scope

Training-day targets, other body measurements (chest, arms …), progress photos, a picture guide for body fat. These are ideas for later.
