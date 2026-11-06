# M9: Home-screen widget

**Goal:** see today's progress and the next box without opening the app.
**Users get:** a widget with calories and protein for today, the next planned box with an **Ate it** button, and how many boxes must be eaten today.
**Depends on:** M5 (plan slots).
**New dependencies:** Jetpack Glance (`androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`).

## Tasks

### M9.1 The "Today" widget

- [ ] **Small (2 × 2):** a ring for calories eaten against the target, and the next planned meal ("Lunch: C2").
- [ ] **Wide (4 × 2):** also protein, the next box with an **Ate it** button (the same action as M4.7), and "2 boxes to eat today".
- [ ] With hide-numbers mode on: no numbers or rings, only the next meal and the boxes.
- [ ] Tapping the widget opens Today.

### M9.2 Keeping it up to date

- [ ] Update after diary, box and plan changes (repositories call the widget's `updateAll`), at midnight, and at most every 30 minutes otherwise.

**Done when:** logging food in the app updates the widget within a few seconds, and **Ate it** on the widget logs the box.

## Out of scope

Other widgets (shopping list, cycle) – ideas for later.
