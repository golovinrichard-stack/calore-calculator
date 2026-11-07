# M6: Smart planner ("Plan my week")

**Goal:** one button plans the whole week: what to cook on each cooking day, how much, which box for which meal, and the shopping list. The owner said this is the most important feature after the basics.
**Users get:** a complete week plan in seconds that fits their targets, taste, budget, cooking days and what is already at home. They can lock, swap and regenerate meals.
**Depends on:** M5.
**Read first:** [algorithms.md](algorithms.md) A15 (the whole section), A10, A11, A13, A14, A16; [data-model.md](data-model.md) (M6).
**New dependencies:** none. No AI or cloud service; everything runs on the phone.

## Tasks

### M6.1 Ratings and preferences (database version 8)

- [x] Table `recipe_ratings`. *As built:* also `createdAt` and `deletedAt` (data-model conventions); taking a rating back soft-deletes it.
- [x] After **Ate it** on a box or a dish, an optional 👍 / 👎 (one tap, easy to ignore). Also on the recipe detail. *As built:* after **Ate it** on a box (Today and Kitchen) a small "… did you like it? 👍 👎 ✕" card asks about the box's recipes not rated yet; a dish is rated on its recipe page, where the thumbs always are.
- [ ] Make sure the profile's food and cooking preferences (diet, allergens, liked and disliked foods, cuisines, equipment, variety, budget, cooking sessions) are easy to fill in. The first time someone taps **Plan my week**, a short checklist shows the ones still missing, all skippable.

### M6.2 The planner engine

- [ ] `domain/plan/Planner.kt` implementing A15 exactly: hard filters → scores → greedy fill → 200 improvement iterations (seeded) → box sizes → `plan_cooks` → shopping list.
- [ ] All weights and limits in one `PlannerConfig`.
- [ ] A `PhaseBonusProvider` interface that returns 0 for now (filled in by M7).
- [ ] A result object that explains itself: for each chosen recipe, the main reasons ("you like it", "uses your spinach before it expires", "cheap protein"), so the UI can show them.

**Tests (JVM):** all A15 test cases; a performance test with 300 synthetic recipes, 3 people and 7 days × 4 meals under 500 ms; the same seed gives the same plan; locked slots never change; no plan ever contains a filtered recipe (property test over random inputs).

### M6.3 "Plan my week"

- [ ] Plan tab → **Plan my week**: choose the week and the people (me plus people I cook for).
- [ ] The proposal shows:
  - the week grid with recipes;
  - per day: kcal and protein against the targets;
  - the cooking sessions with recipes, amounts and estimated active time;
  - boxes that will be frozen;
  - the estimated shopping cost.
- [ ] Actions:
  - **Lock** a meal;
  - **Swap** a meal (the top 5 alternatives with their reasons);
  - **Regenerate** everything not locked (new seed);
  - **Accept**, which saves the plan, `plan_cooks` and the shopping list.
- [ ] Planning a week that already has a plan offers to keep the cooked boxes and replan the rest.

**Done when:** a new user who completes setup and taps **Plan my week** gets a full plan and a shopping list without any other step.

### M6.4 "Use it up"

- [ ] Kitchen → **Use it up**: pantry items that expire within 4 days, each with the recipes that use them, and **Add to plan**.
- [ ] The planner already scores these recipes higher (A15).

### M6.5 When plans change

- [ ] "I'm eating out tonight" (or skipping a meal) in a planned week: the app moves the box to another day if its eat-by date allows, else suggests freezing it, or offering it to another person.
- [ ] **Replan the rest of the week**: runs the planner for the remaining unlocked, not-yet-cooked slots only.

### M6.6 Check-in → planner

- [ ] When a weekly check-in (M1.7) is accepted, the next planned week uses the new targets. For the current week, offer to resize boxes that are not cooked yet.

**Done when:** after accepting a new target, the next "Plan my week" uses it.

## Out of scope

Planning for household members on other phones (M11 adds them). Cloud or AI planning (not planned).
