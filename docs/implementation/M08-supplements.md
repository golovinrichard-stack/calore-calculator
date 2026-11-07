# M8: Supplements

**Goal:** optional supplement tracking with optional reminders.
**Users get:** a daily checklist (vitamin D, creatine, iron …), reminders if they want them, and protein powder counted in the diary.
**Depends on:** M1 (notifications).
**Read first:** [data-model.md](data-model.md) (M8).
**New dependencies:** none.

## Tasks

### M8.1 Switching it on (database version 10)

- [x] Me → **Supplements**, off by default. Tables `supplements` and `supplement_logs`. *As built:* "off" means nothing shows anywhere until the first supplement is added. Database version 10; private tables (`SyncScope`), deleted for real; backup format 9.

### M8.2 Adding a supplement

- [x] Name, kind, dose (amount and unit), times of day, days of the week (default every day), start and end date, and an optional nutrition per dose (for protein powder or gainers). *As built:* at least one time (08:00 by default); picking a kind fills in its usual unit; nutrition is kcal, protein, carbs and fat; a switch "Taking it now" pauses one without losing its history.
- [x] A **Reminders** switch per supplement, off by default.

### M8.3 Today checklist and history

- [x] When supplements exist, Today shows the ones due today with **Taken** and **Skip**. *As built:* with **Undo** after either.
- [x] History per supplement: taken / skipped per day, and how regularly over 7 and 30 days. *As built:* in the Supplements list: "7 days: 6 of 7 · 30 days: 27 of 30" and the last 14 days as ● ✕ ○.

### M8.4 Reminders (only if the user wants them)

- [x] Use `AlarmManager.setWindow` with about a 10-minute window; supplements don't need exact alarms. *As built:* one alarm per supplement, for its next dose; firing posts the reminder (unless the dose is already logged) and sets the next one.
- [x] The notification has **Taken** and **Skip** buttons that log without opening the app (a `BroadcastReceiver`).
- [x] Reschedule after a reboot (`RECEIVE_BOOT_COMPLETED`) and after time or time-zone changes. *As built:* also after an app update.
- [x] Channel *Supplements*. Ask for the notification permission only when the first reminder is switched on.

**Done when:** a reminder arrives within its window with the app closed, and **Taken** on the notification logs it.
*As built:* needs a phone to check; `SupplementRepositoryTest` (instrumented, compiled here) checks that the alarm is set only when reminders are on, and that Taken logs the dose.

### M8.5 Counting nutrition

- [x] Taking a supplement that has calories creates a diary entry (`entryType = SUPPLEMENT`) with a nutrient snapshot. Undoing the log removes the entry. *As built:* in the meal for the dose's time; **Skip** after **Taken** removes it too. In the diary such an entry can only be removed (it has no grams).

## Out of scope

Tracking how many pills are left, and adding supplements to the shopping list automatically (ideas for later).
