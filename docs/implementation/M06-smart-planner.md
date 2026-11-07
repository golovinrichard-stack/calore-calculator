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
- [x] Make sure the profile's food and cooking preferences (diet, allergens, liked and disliked foods, cuisines, equipment, variety, budget, cooking sessions) are easy to fill in. The first time someone taps **Plan my week**, a short checklist shows the ones still missing, all skippable. *As built:* they are all in Me → Profile (M1). The checklist is a card on top of the first proposal (so a new user still gets a plan with no other step): **Fill in my profile** or **Not now**; either way it is not shown again. **Regenerate** reads the profile again.

### M6.2 The planner engine

- [x] `domain/plan/Planner.kt` implementing A15 exactly: hard filters → scores → greedy fill → 200 improvement iterations (seeded) → box sizes → `plan_cooks` → shopping list. *As built:* `Planner.plan` does steps 1–3; `PlanEstimates` gives step 4 (the cooks, with `PlanCooks`) and the inputs for step 5 (needs and the estimated cost; the list itself is made by A16 when the plan is accepted). See the as-built notes in algorithms.md A15.
- [x] All weights and limits in one `PlannerConfig`.
- [x] A `PhaseBonusProvider` interface that returns 0 for now (filled in by M7).
- [x] A result object that explains itself: for each chosen recipe, the main reasons ("you like it", "uses your spinach before it expires", "cheap protein"), so the UI can show them. *As built:* `PlanResult.choices` with up to 3 `PlanReason`s each; `Planner.alternatives` gives the top 5 for **Swap** with their reasons.

**Tests (JVM):** all A15 test cases; a performance test with 300 synthetic recipes, 3 people and 7 days × 4 meals under 500 ms; the same seed gives the same plan; locked slots never change; no plan ever contains a filtered recipe (property test over random inputs).
*As built:* `PlannerTest` (19 tests, including all of these; the 300-recipe plan takes about 90 ms on the build machine) and `PlanEstimatesTest`.

### M6.3 "Plan my week"

- [x] Plan tab → **Plan my week**: choose the week and the people (me plus people I cook for). *As built:* a button on the Week tab; on the proposal, This week / Next week and a chip per person (I am always included).
- [x] The proposal shows:
  - the week grid with recipes;
  - per day: kcal and protein against the targets;
  - the cooking sessions with recipes, amounts and estimated active time;
  - boxes that will be frozen;
  - the estimated shopping cost.
- [x] Actions:
  - **Lock** a meal;
  - **Swap** a meal (the top 5 alternatives with their reasons);
  - **Regenerate** everything not locked (new seed);
  - **Accept**, which saves the plan, `plan_cooks` and the shopping list.

  *As built:* tap a meal to see why it was chosen, swap it (the swapped meal is then locked) or lock it; the lock icon on each meal does the same. Planned cooks are saved with no batch; cooking fills in the batch on the same row.
- [x] Planning a week that already has a plan offers to keep the cooked boxes and replan the rest. *As built:* kept are eaten meals, meals with a box, meals whose pot is already cooked, locked meals, eat out and free, everyone not chosen, and days before today.

**Done when:** a new user who completes setup and taps **Plan my week** gets a full plan and a shopping list without any other step.
*As built:* `StarterPackPlannerTest` (JVM, the real starter pack: all 28 meals of a new user's week are planned; also a vegetarian plus a meat eater) and `PlanMyWeekTest` (instrumented, compiled here: proposal → Accept → slots, planned cooks and a shopping list; replanning keeps locked and eat-out meals).

### M6.4 "Use it up"

- [x] Kitchen → **Use it up**: pantry items that expire within 4 days, each with the recipes that use them, and **Add to plan**. *As built:* a card on the Pantry tab opens it. **Add to plan** asks for a day (today and the next 3) and a meal, and plans the recipe cooked fresh for me and for everyone I cook for who eats that meal and passes the A15 filters. `UseItUpTest`.
- [x] The planner already scores these recipes higher (A15).

### M6.5 When plans change

- [x] "I'm eating out tonight" (or skipping a meal) in a planned week: the app moves the box to another day if its eat-by date allows, else suggests freezing it, or offering it to another person. *As built:* Today → a planned meal's menu → **I'm eating out**. The meal becomes "eat out"; if it had a box, the same choices as **Skip** (M5.11) follow, plus "keep it in the fridge for now". Skipping is M5.11's **Skip**.
- [x] **Replan the rest of the week**: runs the planner for the remaining unlocked, not-yet-cooked slots only. *As built:* the Week tab's button says **Replan the rest of the week** when the current week has a plan; it opens **Plan my week**, which plans from today and keeps what M6.3 keeps.

### M6.6 Check-in → planner

- [x] When a weekly check-in (M1.7) is accepted, the next planned week uses the new targets. For the current week, offer to resize boxes that are not cooked yet. *As built:* the planner always uses the current targets. After **Use it**, the check-in offers to update this week's meals from today that are not eaten, boxed or cooked yet; their sizes follow from the new targets when they are cooked. Instrumented test in `PlanRepositoryTest`.

**Done when:** after accepting a new target, the next "Plan my week" uses it.
*As built:* the planner reads each person's current targets (A8 meal targets) every time it runs.

## Out of scope

Planning for household members on other phones (M11 adds them). Cloud or AI planning (not planned).
