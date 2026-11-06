# M8: Supplements

**Goal:** optional supplement tracking with optional reminders.
**Users get:** a daily checklist (vitamin D, creatine, iron …), reminders if they want them, and protein powder counted in the diary.
**Depends on:** M1 (notifications).
**Read first:** [data-model.md](data-model.md) (M8).
**New dependencies:** none.

## Tasks

### M8.1 Switching it on (database version 10)

- [ ] Me → **Supplements**, off by default. Tables `supplements` and `supplement_logs`.

### M8.2 Adding a supplement

- [ ] Name, kind, dose (amount and unit), times of day, days of the week (default every day), start and end date, and an optional nutrition per dose (for protein powder or gainers).
- [ ] A **Reminders** switch per supplement, off by default.

### M8.3 Today checklist and history

- [ ] When supplements exist, Today shows the ones due today with **Taken** and **Skip**.
- [ ] History per supplement: taken / skipped per day, and how regularly over 7 and 30 days.

### M8.4 Reminders (only if the user wants them)

- [ ] Use `AlarmManager.setWindow` with about a 10-minute window; supplements don't need exact alarms.
- [ ] The notification has **Taken** and **Skip** buttons that log without opening the app (a `BroadcastReceiver`).
- [ ] Reschedule after a reboot (`RECEIVE_BOOT_COMPLETED`) and after time or time-zone changes.
- [ ] Channel *Supplements*. Ask for the notification permission only when the first reminder is switched on.

**Done when:** a reminder arrives within its window with the app closed, and **Taken** on the notification logs it.

### M8.5 Counting nutrition

- [ ] Taking a supplement that has calories creates a diary entry (`entryType = SUPPLEMENT`) with a nutrient snapshot. Undoing the log removes the entry.

## Out of scope

Tracking how many pills are left, and adding supplements to the shopping list automatically (ideas for later).
