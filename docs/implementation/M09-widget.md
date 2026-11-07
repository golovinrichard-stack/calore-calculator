# M9: Home-screen widget

**Goal:** see today's progress and the next box without opening the app.
**Users get:** a widget with calories and protein for today, the next planned box with an **Ate it** button, and how many boxes must be eaten today.
**Depends on:** M5 (plan slots).
**New dependencies:** Jetpack Glance (`androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`).

## Tasks

### M9.1 The "Today" widget

- [x] **Small (2 × 2):** a ring for calories eaten against the target, and the next planned meal ("Lunch: C2"). *As built:* Jetpack Glance 1.2.0; the ring is a small drawn picture (Glance can't draw arcs). The next meal is the first planned meal not eaten yet whose time is now or later.
- [x] **Wide (4 × 2):** also protein, the next box with an **Ate it** button (the same action as M4.7), and "2 boxes to eat today". *As built:* "boxes to eat today" counts my fridge boxes whose eat-by date is today or earlier.
- [x] With hide-numbers mode on: no numbers or rings, only the next meal and the boxes.
- [x] Tapping the widget opens Today.

### M9.2 Keeping it up to date

- [x] Update after diary, box and plan changes (repositories call the widget's `updateAll`), at midnight, and at most every 30 minutes otherwise. *As built:* instead of each repository calling it, Room's invalidation tracker tells the app when the diary, box, plan or member tables change; one second later every widget updates (only if a widget exists). An inexact alarm just after midnight, and `updatePeriodMillis` = 30 minutes.

**Done when:** logging food in the app updates the widget within a few seconds, and **Ate it** on the widget logs the box.
*As built:* needs a phone with the widget on the home screen to check. `TodayWidgetModelTest` (JVM) checks what the widget shows.

## Out of scope

Other widgets (shopping list, cycle) – ideas for later.
