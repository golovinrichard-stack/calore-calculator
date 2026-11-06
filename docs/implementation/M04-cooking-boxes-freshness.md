# M4: Cooking, prep boxes and freshness

**Goal:** the heart of the plan. Cook, weigh the pot, fill the boxes with the right grams, track freshness, and log a box with one tap.
**Users get:** "Fill 6 boxes with 500 g each", an eat-by date and colour on every box, reminders before food spoils, and automatic logging when a box is eaten.
**Depends on:** M1 (targets, notifications, people), M3 (recipes).
**Read first:** [data-model.md](data-model.md) (M4), [algorithms.md](algorithms.md) A8–A13.
**New dependencies:** none (WorkManager comes from M1).

## Tasks

### M4.1 Cooking data (database version 6)

- [x] Tables `pots`, `batches`, `batch_ingredients`, `boxes` and `box_parts`; `diary_entries.boxId` (data-model M4; `recipeId` already came with version 5 in M3).
- [x] Domain: batch nutrition and learned yield (A9), box splitting (A10), the box solver (A11), eat-by dates and colours (A12), freeze and thaw planning (A13).

**Done when:** the A9–A13 test cases pass.

### M4.2 Pots

- [x] Me → Settings → **My pots**: name and empty weight ("Put the empty pot on the scale and type the weight").

### M4.3 The cook flow

1. [x] Start from a recipe (**Cook**), or later from a planned cook (M5).
2. [x] Choose one or more recipes and how much: servings, or a target cooked weight.
3. [x] Check the ingredients: the scaled amounts are shown and can be changed to what was really used. They are saved as `batch_ingredients`.
4. [ ] **Cook mode** (M4.4).
5. [x] **Weigh:** choose a saved pot and type the full weight (the app subtracts the empty pot), or type the net weight. A9 checks the value.
6. [x] **Fill boxes** (M4.5).

A single food cooked on its own (for example plain rice) is a batch without a recipe: pick the food, its raw grams and the cooked weight. *(Built: "Add a single food" in the first step.)*

### M4.4 Cook mode

- [ ] One timeline for all chosen recipes. Steps keep their order within a recipe. Whenever the cook is free, start the next active step of the recipe with the most time left; passive steps (oven, simmering) run in parallel. Show **Now** and **Next** cards and the estimated total time.
- [ ] A timer on every step that has minutes. The screen stays on while cook mode is open.
- [ ] Timers when the app is in the background: an exact alarm if the user allowed exact alarms (`canScheduleExactAlarms()`), otherwise an inexact one, with a one-time hint explaining how to allow exact alarms. Channel *Cooking timers*.
- [ ] When cook mode ends, set the cooling reminder: 2 hours later, "Is the food in the fridge?"

**Done when:** for two recipes with an oven step, the timeline runs the other recipe's active steps during the oven time; timers fire with the app closed.

### M4.5 Filling boxes

- [x] Modes (A10): **equal**, **by calorie target** and **by person** (choose people and meals; their meal targets give the grams).
- [x] **Boxes from several batches** (for example chicken + rice + broccoli): choose the batches, set fixed parts if wanted, set the target per box (default: the person's meal target), and the solver (A11) gives the grams of each part for each box. Show a warning when the target can't be reached.
- [x] Show for each box: code (`C1` …), grams, kcal and protein (respect hide-numbers mode), the owner's name and colour, fridge or freezer.
- [x] Planned day and meal per box are optional here; M5 fills them from the plan. Boxes whose day is after the fridge eat-by date go to the freezer automatically (A13), and the screen says so.
- [x] Leftover handling as in A10.

**Done when:** the chili example gives 6 × 500 g; the "by person" example gives 4 × 500 g + 2 × 375 g and a 250 g leftover box; the chicken-rice example gives 130 g + 280 g.

### M4.6 The Kitchen tab

- [ ] Bottom bar: **Today · Plan · Kitchen · Me**.
- [x] Boxes in two sections, **Fridge** and **Freezer**, sorted by eat-by date (eat first at the top), each with its green, yellow or red colour, code, contents and owner.
- [x] Filters: mine, everyone's.
- [x] Box detail: contents and grams per part, nutrients, cooked date, eat-by date, (cost in M5).
- [x] Actions: **Ate it**, **Ate part**, **Freeze**, **Thawed**, **Throw away** (with a reason), **Move** (day and meal).

### M4.7 "Ate it": automatic accounting

When someone eats a box and taps **Ate it**, the app does everything at once:

- [x] creates a diary entry (`entryType = BOX`, `boxId`) with the nutrients of the eaten part (snapshot), in the meal of the planned slot (or the meal matching the time of day), for the box's owner (`memberId`);
- [x] marks the box `EATEN`, or `PART_EATEN` with the new `remainingFraction` for **Ate part** (grams or %);
- [ ] marks the plan slot done (once M5 exists);
- [x] updates Today's totals, and what's left for the day;
- [x] offers **Undo**, which restores the diary entry, the box and the slot together.

Entry points: Kitchen, the box detail, the Today card (M4.9) and later the widget (M9).

**Done when:** a test eats a full box and a partly eaten box, and the diary totals, the box state and the undo are all correct.

### M4.8 People I cook for

- [ ] Me → **People I cook for**: add `MANAGED` members (name, colour, and targets either from the calculator, using sex, birth date, height, `weightKg`, activity and goal, or typed in), their meal split and their prepped meals.
- [ ] They appear in "by person" box filling (M4.5) and later in the plan (M5) and the planner (M6). This is how "cook once for the family" works on one phone. In M11, household members with their own phones join in.

### M4.9 Freshness reminders and Today

- [ ] A daily WorkManager job in the morning (flexible window): "2 boxes should be eaten today" (yellow boxes due today, plus red ones). Channel *Freshness*.
- [ ] An evening job at about 19:00: thaw reminders for frozen boxes planned for tomorrow.
- [ ] Settings switches for each reminder type.
- [ ] Today shows an **Eat first** card with up to 3 boxes (by eat-by date) and an **Ate it** button.

### M4.10 Waste log

- [x] **Throw away** sets `WASTED` with a reason (`EXPIRED`, `SPOILED`, `DIDNT_LIKE`, `OTHER`).
- [x] Kitchen → **Thrown away**: a history with this month's count and calories. Money comes in M5.

## Out of scope

The week plan (M5), costs (M5), sharing boxes with other phones (M11).
