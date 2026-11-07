# Algorithms

Every calculation the new features need, with test cases. Put each one in `domain/` as pure Kotlin and give it a unit test with at least the cases listed here. Constants marked *tunable* go in one place per area (for example `PrepRules`) so they are easy to adjust.

**Shared constants**

| Constant | Value | Notes |
|---|---|---|
| kcal per kg of body weight change | 7,700 | Standard approximation |
| kcal per gram | protein 4, carbs 4, fat 9 | As in `CalorieCalculator` today |
| Rounding | kcal targets to 10 (as today); grams in boxes to 5 g; displayed macros to 1 g | |

---

## A1. Age from birth date

`age = years between birthDate and today`, minus 1 if this year's birthday hasn't come yet. Must stay within `CalorieCalculator.AGE_RANGE` (15–100).

| Birth date | Today | Age |
|---|---|---|
| 2000-07-15 | 2026-11-06 | 26 |
| 2000-12-01 | 2026-11-06 | 25 |

## A2. BMR and maintenance (TDEE)

- **Mifflin-St Jeor:** unchanged, the existing `CalorieCalculator.bmr`.
- **Katch-McArdle:** `BMR = 370 + 21.6 × leanMassKg`, where `leanMassKg = weightKg × (1 − bodyFatPct / 100)`.
- **Which one:** Katch-McArdle when there is a body-fat value measured in the last 90 days (*tunable*), else Mifflin-St Jeor. The UI says which formula was used.
- `TDEE = BMR × activity multiplier` (existing `ActivityLevel`).

| Case | Expected |
|---|---|
| 80 kg, 15 % body fat | lean mass 68 kg, BMR 1,839 kcal |
| Body fat measured 120 days ago | Mifflin-St Jeor is used |

## A3. Goal speed and calorie target

- `weeklyChangeKg = weightKg × goalRatePct / 100`
- `dailyDelta = weeklyChangeKg × 7,700 / 7`, negative for `CUT`, positive for `BUILD`, zero for `MAINTAIN` and `RECOMP`.
- `target = roundToTen(TDEE + dailyDelta)`, then apply the existing safety floors (1,200 kcal women, 1,500 kcal men; never above maintenance for a cut), exactly as `CalorieCalculator.targetKcal` does today.
- Offered speeds: `CUT` 0.5 / 0.75 / 1.0 %; `BUILD` 0.25 / 0.5 %.
- Warnings (not blocks): a cut faster than 1 % per week; a cut when the last body-fat value is below 10 % (men) or 18 % (women) (*tunable*).
- **Old settings:** `LOSE` becomes `CUT` 0.5 %, `GAIN` becomes `BUILD` 0.25 %, `MAINTAIN` stays.

| Case | Expected |
|---|---|
| 80 kg, CUT 0.5 % | −440 kcal per day (−0.4 kg per week) |
| 70 kg, BUILD 0.25 % | +192.5 kcal per day; target rounded to 10 |

## A4. Macros

**`AUTO` mode** (the default from M1):

1. Protein base weight = body weight. If BMI ≥ 30, use the weight at BMI 25 instead (`heightM² × 25`) (*tunable*).
2. `proteinG = proteinPerKg × baseWeight`. Defaults: CUT 2.0, MAINTAIN 1.6, BUILD 1.8, RECOMP 2.0 g/kg; the user can choose 1.2–2.4.
3. `fatG = target × fatPct / 100 / 9`, with `fatPct` default 25, minimum 20.
4. `carbsG = (target − proteinG × 4 − fatG × 9) / 4`.
5. If carbs come out negative: lower `fatPct` to 20 and recalculate. If they are still negative: carbs = 0, fat = whatever calories are left, and show the warning "Protein is too high for this calorie target".

**`SPLIT` mode:** the existing `MacroSplit` percentages, unchanged.

| Case | Expected |
|---|---|
| 2,200 kcal, 80 kg, 180 cm, 2.0 g/kg, fat 25 % | protein 160 g, fat 61 g, carbs 253 g |
| 120 kg, 180 cm, CUT 2.0 g/kg | base weight 81 kg, protein 162 g |
| 1,200 kcal, protein set to 250 g, fat 25 % | fat lowered to 20 %, still negative → carbs 0, fat 22 g, warning shown |

## A5. Body fat, US Navy method

Measurements in cm, `log10`:

- Men: `BF% = 495 / (1.0324 − 0.19077 × log10(waist − neck) + 0.15456 × log10(height)) − 450`
- Women: `BF% = 495 / (1.29579 − 0.35004 × log10(waist + hip − neck) + 0.22100 × log10(height)) − 450`

Validation: `waist > neck` (men) or `waist + hip > neck` (women), every measurement 20–200 cm, height inside `HEIGHT_RANGE_CM`. A result outside 3–60 % is rejected with "Check your measurements".

| Case | Expected |
|---|---|
| Man, 180 cm, neck 38, waist 85 | 16.1 % |
| Woman, 165 cm, neck 32, waist 70, hip 95 | 24.9 % |
| Man, neck 40, waist 38 | rejected |

## A6. Trend weight

An exponential moving average that handles gaps between weigh-ins:

- The first weigh-in sets `trend = weight`.
- After that, `alpha = 1 − 0.9^gapDays`, `trend = trend + alpha × (weight − trend)`, where `gapDays` is the number of days since the previous weigh-in (at least 1).
- The chart shows the weigh-ins as dots and the trend as a line.

| Case | Expected |
|---|---|
| Trend 80.0, weigh-in 79.0 the next day | 79.90 |
| Trend 80.0, weigh-in 79.0 three days later | 79.73 |

## A7. Smart maintenance and the weekly check-in

Runs once a week (Monday morning, or when the user opens the check-in).

1. **Window:** the last 28 days.
2. **Counted days:** days whose logged total is at least 50 % of that day's target (*tunable*). Days below that are treated as incompletely logged and skipped.
3. **Enough data:** at least 14 counted days, and at least 4 weigh-ins spanning at least 10 days. Otherwise: "Keep logging; your first check-in comes when there is enough data."
4. **Cycle:** if cycle tracking is on, leave out weigh-ins on likely water-retention days (A19) when picking the trend start and end.
5. `dataMaintenance = avgIntake(counted days) − (trendEnd − trendStart) × 7,700 / spanDays`
6. **Blend with the formula** while data is thin: `w = min(1, countedDays / 28)`, `estimate = w × dataMaintenance + (1 − w) × formulaTDEE`.
7. `proposed = roundToTen(estimate + dailyDelta)` (A3), with the same safety floors.
8. **Change slowly:** move the target at most 150 kcal per check-in (*tunable*).
9. Save a `checkins` row. The user sees the trend change, the estimated maintenance and the proposed target, and taps **Use it** or **Keep my target**. Accepting recalculates macros (A4).

| Case | Expected |
|---|---|
| Average intake 2,500; trend 80.0 → 79.1 kg over 21 days | data maintenance 2,830 kcal |
| Same, 21 counted days, formula TDEE 2,600, CUT 0.5 % at 79.1 kg, current target 2,200 | estimate 2,772.5; delta −435; proposed 2,340 (change +140, within the limit) |
| Only 9 counted days | no proposal |

*As built in M1.7:* the food window is the 28 days before today (today is still being logged); weigh-ins count up to today. Targets are not stored per day, so "that day's target" in step 2 is the current target. When the profile is incomplete (no formula TDEE), the estimate is the data maintenance alone. One check-in is saved per week (Monday); opening the check-in screen runs it if Monday's job hasn't.

## A8. Meal targets

`mealTarget = dailyTarget × mealSplit[meal] / 100`, for calories and each macro. Meals the member doesn't eat (for example no breakfast) are removed and their share is spread over the other meals in proportion.

| Case | Expected |
|---|---|
| 2,400 kcal, lunch 30 % | 720 kcal |
| Split 20/30/30/20, no breakfast | lunch 37.5 %, dinner 37.5 %, snack 25 % |

## A9. Batch nutrition and learned yield

- Nutrients of the whole pot = sum over `batch_ingredients` of the food's per-100 g values × grams / 100.
- **Per 100 g cooked** = pot nutrients ÷ `cookedGrams` × 100.
- **Net weight** = full pot weight − saved empty pot weight. Reject a result ≤ 0 or more than 5 times the raw weight.
- **Learned yield** = `cookedGrams / rawGrams`. Update the recipe: first time `learnedYield = latest`; afterwards `learnedYield = 0.5 × learnedYield + 0.5 × latest`, and increase `learnedYieldCount`.
- **Estimated cooked weight** before cooking (for planning) = raw grams × `learnedYield`, or, when nothing has been learned yet, a default per recipe tag: 1.0 (*tunable*). Planning then works in servings until the first weighing.

| Case | Expected |
|---|---|
| Chili: 3,600 kcal in the pot, 3,000 g cooked | 120 kcal per 100 g |
| Pot weighs 4,850 g full, empty 1,850 g | net 3,000 g |
| Yield 0.90, new cook 0.80 | learned yield 0.85 |

## A10. Splitting a batch into boxes

Three modes:

1. **Equal:** `n` boxes, `cookedGrams / n` each, rounded to 5 g; the last box takes the rounding remainder.
2. **By calorie target:** box grams `g = target / (kcalPer100g / 100)`; `n = floor(cookedGrams / g)`; leftover = `cookedGrams − n × g`. If the leftover is at least 40 % of `g`, offer it as an extra smaller box, otherwise offer to spread it over the boxes.
3. **By person:** for each chosen person and meal, grams = that person's meal target ÷ density. If the pot is too small, scale every box down by the same factor and warn. The leftover becomes an extra box.

Box codes: the recipe's `shortCode` plus the next free number (`C1`, `C2` …). If two active recipes share a letter, use two letters.

| Case | Expected |
|---|---|
| 3,000 g at 120 kcal/100 g, target 600 kcal | 6 boxes of 500 g, no leftover |
| Same pot, by person: four 600 kcal lunches and two 450 kcal lunches | 4 × 500 g + 2 × 375 g, leftover 250 g offered as an extra box |
| 1,000 g, equal, 3 boxes | 335 g, 335 g, 330 g |

## A11. Box solver (boxes made of several parts)

For a box made of several batches (for example chicken, rice and broccoli), find the grams of each flexible part.

- **Variables:** grams `x_i` of each flexible part, each within `[min_i, max_i]` (default 0–600 g). Fixed parts (for example 100 g broccoli) are added as constants.
- **Objective:** minimise `Σ_k w_k × ((predicted_k − target_k) / target_k)²` over `k ∈ {kcal, protein, carbs, fat}`. Default weights: kcal 4, protein 2, carbs 0.5, fat 0.5 (*tunable*). A nutrient with no target or weight 0 is skipped.
- **Method:** projected gradient descent (clamp to the bounds after each step) from a start that hits the kcal target with equal grams, about 500 iterations; then round to 5 g and try ±5 g on each variable while the objective improves. At most 4 flexible parts, so this takes milliseconds.
- **Output:** grams per part, the predicted totals, and a flag when the result misses kcal by more than 5 % or protein by more than 10 % ("can't reach the target with these parts").

| Case | Expected |
|---|---|
| Chicken (165 kcal, 31 g P, 0 g C, 3.6 g F per 100 g) and rice (130, 2.7, 28, 0.3) flexible; broccoli 100 g (35, 2.4, 7.2, 0.4) and olive oil 10 g (884 kcal, 100 g F per 100 g) fixed; targets 700 kcal and 50 g protein only | chicken 130 g, rice 280 g (±5 g) → about 702 kcal, 50.3 g protein |
| One part only, 120 kcal/100 g, target 600 kcal | 500 g |
| Rice only (max 600 g), target 50 g protein | flag raised: target can't be reached |

## A12. Eat-by dates and colours

Dates are local calendar dates.

- **Fridge:** `eatByDate = cookedDate + fridgeDays`. The default `fridgeDays` is 3 (Czech and US advice for cooked leftovers is 3–4 days; we use the safer end), and each recipe can override it.
- **Several parts:** the earliest `eatByDate` of the parts.
- **Freezing** stops the clock. A box can't be frozen after its eat-by date.
- **Thawing** in the fridge: `eatByDate = thawDate + 1` (eat within about 24 hours).
- **Freezer quality:** after `frozenDate + freezerMonths` (default 3), show "best quality is over, still safe if kept frozen".
- **Colour:** red if `eatByDate < today`; yellow if `eatByDate` is today or tomorrow; green otherwise. Red boxes can't be logged as eaten without a confirmation.
- **Cooling reminder:** 2 hours after cook mode ends, "Is the food in the fridge?"

| Case | Expected |
|---|---|
| Cooked Sunday 2026-11-08, 3 fridge days | eat by Wednesday 2026-11-11 |
| Same box, on Monday / Tuesday / Thursday | green / yellow / red |
| Chicken part cooked Sunday (3 days), rice part cooked Monday (1 day) | eat by Tuesday 2026-11-10 |
| Frozen Monday, thawed the next Tuesday | eat by Wednesday |

## A13. Freeze and thaw planning

- At portioning time, every box whose planned date is after its fridge `eatByDate` goes to the **freezer** immediately, and the screen says so.
- **Thaw reminder** at 19:00 (*tunable*) the evening before the planned date: "Move box C4 from the freezer to the fridge."
- When the user taps **Thawed**, apply A12.

| Case | Expected |
|---|---|
| Cooked Sunday, boxes planned Mon–Thu, 3 fridge days | Mon–Wed in the fridge; Thursday's box frozen; thaw reminder Wednesday 19:00 |

## A14. Cooking sessions

- If the member picked exactly `cookSessionsPerWeek` days, use them.
- Otherwise choose days that make the longest gap between sessions as short as possible, preferring weekend days for the longest session:

| Sessions per week | Default days |
|---|---|
| 1 | Sunday |
| 2 | Sunday, Wednesday |
| 3 | Sunday, Tuesday, Thursday |
| 4 | Sunday, Tuesday, Thursday, Saturday |
| 5 | Sunday, Monday, Wednesday, Thursday, Friday |
| 6 | Every day except Saturday |
| 7 | Every day |

- A session covers the prepped meals from the next meal after the session until the meal before the next session's first covered meal. *As built:* a session is in the afternoon, after lunch (*tunable*), so its first covered meal is dinner on the cooking day. With sessions on Sunday and Wednesday and lunches and dinners from boxes, Sunday covers Sunday dinner to Wednesday lunch and Wednesday covers Wednesday dinner to Sunday lunch.
- Meals in a session's range that fall after the fridge eat-by date are planned as freezer boxes (A13). With 1 session a week the app warns: "With one cooking day, about half of your boxes will be frozen."

## A15. Smart planner ("Plan my week")

**Input:** the members to plan for, their meal targets, preferences (members table), cooking sessions (A14), recipes, ratings, pantry, prices, existing locked slots, the cycle phase per day (M7, optional) and a seed.

**Step 1: hard filters** (a recipe is removed for a member if any fails):
- the diet allows all ingredients (`Diet` vs `AnimalFlag`: VEGAN none; VEGETARIAN no FISH, SHELLFISH, MEAT, PORK; PESCATARIAN no MEAT, PORK; NO_PORK no PORK),
- no ingredient carries one of the member's allergens,
- no ingredient is in the member's disliked foods, and the recipe is not rated −1,
- the recipe's equipment is a subset of the member's equipment (skip the check when the member set no equipment),
- the meal type fits the slot,
- for box slots: `mealPrepFriendly`, and either the fridge days cover the slot or the recipe is freezable.

**Step 2: score** each remaining recipe for each slot (all *tunable*):

| Factor | Points |
|---|---|
| Rated +1 by the member | +20 |
| Each liked food in the ingredients | +10 (at most +20) |
| Cuisine is one of the member's cuisines | +10 |
| Used in the last 7 days | −15 × (variety − 1), so no penalty at variety 1 |
| Protein fit: how close the recipe's protein share of calories is to the target share | 0 to +15 |
| Cost per serving compared with the budget per meal (only if a budget is set) | 0 to +15 |
| Uses pantry food that expires within 4 days (share of the recipe's grams) | 0 to +20 |
| Cycle phase bonus (A20) | 0 to +10 |
| Freezable, when the slot needs a frozen box | +10 |

**Step 3: build the week.**
1. Per session and meal type, pick `k` recipes: variety 1 → 1, variety 2 → 2, variety 3 → one per day the session covers. Fill greedily by score.
2. **Improve** with 200 iterations (seeded random): swap one recipe for another candidate and keep the change if the week's total improves. The week's total is the sum of recipe scores, minus a penalty for every day whose planned protein is more than 10 % off target, minus a penalty for sessions whose active cooking minutes exceed `minutesPerSession`.
3. Locked slots never change. **Regenerate** uses a new seed; **swap** re-runs the choice for one slot only.

**Step 4: sizes.** For each box slot, its grams from the slot's targets (A10 by person, or A11 for boxes with several parts). Sum per recipe and session → `plan_cooks` (portions, target cooked grams, recipe scale = target cooked grams ÷ (raw grams × yield)).

**Step 5: shopping list** (A16).

**Performance:** under 2 s on a mid-range phone for 300 recipes, 3 members, 7 days × 4 meals. JVM unit test: under 500 ms with 300 synthetic recipes. The same seed and input must give the same plan.

**Test cases**
- A vegan member never gets a recipe with an animal flag.
- An allergen (MILK) filters out every recipe with a milk ingredient.
- With variety 1 and 2 sessions, each session has one lunch recipe.
- A pantry item expiring in 2 days raises the recipes that use it.
- Same seed twice gives the same plan; a new seed changes at least one unlocked slot when there are alternatives.

**As built (M6.2):**
- *Which meals.* Every person's meals with a share in their meal split, from today to Sunday, minus the kept slots (locked, eaten, with a box, eat out, free). Prepped meals are boxes from the session that covers them (A14); if that session is already over, the meal is cooked fresh. Meal types, cooking days, prepped meals, minutes and variety are the cook's (the phone's user).
- *One pot per meal.* Everyone eating the same meal from the same session, or the same fresh meal, gets the same recipe when one passes everyone's filters. If none does, each person gets their own pot, and if that fails too, each day on its own. A meal nothing fits stays empty and is reported as unplanned.
- *Filters, details.* A recipe without meal types counts as lunch and dinner. A recipe without calories can't be sized and is left out. Disliked foods match the food key, the food or its shop section, optional ingredients included (like allergens and diet).
- *Scores, details.* Points are counted per meal (slot) and summed for a pot. Protein fit = 15 × (1 − |recipe share − target share| ÷ target share), at least 0; shares are protein kcal ÷ kcal, the target from the person's daily targets. Cost = the meal's cost (cost per kcal × the meal's kcal target) against the budget per meal (weekly budget ÷ planned meals of the week): 15 points at half the budget or less, 0 at 1.5 times or more, linear in between. Pantry food "expiring within 4 days": best-before from today to today + 4, not reserved; the share is min(ingredient grams, pantry grams) ÷ the recipe's grams. "Needs a frozen box": eaten after the session day + the default 3 fridge days.
- *Variety.* "Used in the last 7 days" counts the recipes each person had in the 7 days before the week and those already chosen earlier in the week (an earlier session or day). The `k` recipes of a session and meal are different from each other when there are enough candidates.
- *Improve.* Each round picks a pot at random and tries one of its 10 best other candidates.
- *Totals.* A day is "more than 10 % off" when |planned protein − target| > 10 % of the target, both summed over the person's planned meals that day (planned protein = the meal's kcal target × the recipe's protein per kcal); the penalty is 20 per person and day. A session over the minutes costs 30.
- *Regenerate* passes the last proposal: its recipes lose 15 points for those meals, and if the plan still comes out the same, the pot whose next best recipe loses least takes it. That is what makes "a new seed changes at least one unlocked slot" always true.
- *Ties* are broken by an order shuffled with the seed.

## A16. Shopping list

1. `need[food]` = sum of ingredient grams × scale over all `plan_cooks` of the plan that are not cooked yet.
2. `available[food]` = sum of `AVAILABLE` pantry items not reserved for another cook, whose best-before date is on or after the cook date (or that have no date). Pieces convert with `gramsPerPiece`.
3. `toBuy = max(0, need − available)`.
4. **Product:** the preferred `store_product` for this food at the store that is the default for the food's section (`stores.defaultForSections`); otherwise the cheapest known price per gram among products at favourite stores; otherwise no product (the line shows the generic food and "Pick a product").
5. `packs = ceil(toBuy / packAmount)`; without a product, show the grams.
6. Group by store, then sort by the store's `sectionOrder`. Manual items keep their place. Regenerating keeps manual items and ticks.

| Case | Expected |
|---|---|
| Need 1,150 g chicken, nothing at home, product of 500 g | 3 packs (350 g extra goes to the pantry when ticked) |
| Same, 200 g in the pantry with a good date | 2 packs (50 g extra) |
| Need 300 g, a pantry item of 400 g that expires the day before the cook | it doesn't count: buy 1 pack |

## A17. Pantry: using up and default dates

- **Cooking** takes ingredients from the pantry first-in-first-out: earliest best-before date first, then earliest bought. If the pantry is short, cook anyway and show "Your pantry had less X than the recipe needed; check your pantry."
- **Default best-before** when the user enters no date (rough defaults; the date printed on the pack always wins):

| Section | Days | Storage |
|---|---|---|
| MEAT, FISH | 2 | Fridge |
| DAIRY, VEGETABLES, FRUIT | 5 | Fridge (fruit: cupboard) |
| BAKERY | 3 | Cupboard |
| EGGS | 21 | Fridge |
| FROZEN | 90 | Freezer |
| Everything else | 180 | Cupboard |

- **After opening:** a fridge item gets at most 3 more days (*tunable*).

## A18. Costs and waste

- Price per gram = `priceHalers / packAmount` (in grams) from the latest price of the product.
- Batch cost = sum of ingredient grams × price per gram; marked "incomplete" when a price is missing.
- Box cost = batch cost × part grams ÷ cooked grams (summed over parts).
- Cost per 100 g protein = cost ÷ protein grams × 100.
- Wasted money = cost of wasted boxes (× remaining fraction) + cost share of wasted pantry items.
- Stats per month: boxes and kcal wasted, money wasted, money spent (ticked items with prices), cost per day.

| Case | Expected |
|---|---|
| Chicken 500 g for 139.90 Kč (a test value), 130 g used | 36.37 Kč |

## A19. Cycle prediction and phases

- **Average cycle length:** weighted average of the last cycles (at most 6), weights 1, 2, 3 … with the newest highest. Until 2 complete cycles exist, use `defaultCycleLength` (28).
- **Next period:** last start + average (rounded).
- **Range:** ± the population standard deviation of those cycle lengths, at least 1 day. If the longest and shortest differ by more than 7 days, mark the cycle **irregular** and use ± (max − min) / 2.
- **Phases:**
  - Period: start to start + period length − 1 (logged end, or the average period length, default 5).
  - Ovulation day = next start − 14; ovulation phase = ovulation day − 2 to + 1.
  - Follicular: after the period until the ovulation phase (can be empty).
  - Luteal: after the ovulation phase until the day before the next start.
- **Likely water-retention days:** next start − 5 to next start + 2 (*tunable*).
- **Pause predictions** (show logged bleeding only, no phases or tips) when contraception is hormonal (`PILL`, `HORMONAL_IUD`, `IMPLANT`, `OTHER_HORMONAL`), or when pregnant, breastfeeding, or in perimenopause or menopause.

| Case | Expected |
|---|---|
| Cycles 28, 30, 29 (oldest first) | average 29, range ± 1 day, regular |
| Last start 2026-10-01, average 28, period 5 days | next start 2026-10-29; period 10-01 to 10-05; follicular 10-06 to 10-12; ovulation 10-13 to 10-16; luteal 10-17 to 10-28; water days 10-24 to 10-31 |
| Cycles 24, 35, 28 | irregular, range ± 5.5 days |
| Contraception PILL | no phases, no tips |

## A20. Cycle tips and planner bonus

No calorie target changes. Research finds only a small rise in resting energy use in the luteal phase (about 40–45 kcal per day in a 2024 study), so the app doesn't change targets.

| Phase | Tip (strings) | Planner bonus per serving (*tunable*) |
|---|---|---|
| PERIOD | Iron-rich food with vitamin C; warm, easy meals if cramps are logged | +10 if iron ≥ 3 mg; +2 if vitamin C ≥ 20 mg |
| FOLLICULAR, OVULATION | none | none |
| LUTEAL | Filling snacks with protein and fibre; magnesium-rich food | +8 if magnesium ≥ 80 mg; +4 if fibre ≥ 8 g |

The bonus is capped at +10 per recipe. The planner only gets it when cycle tracking is on and predictions are not paused.

## A21. Research reports

Built every Monday for the previous ISO week (Monday–Sunday), only for the categories the user agreed to (M10). The exact JSON format and the full list of fields are in [`PRIVACY_POLICY.md`, appendix A](../PRIVACY_POLICY.md#appendix-a-research-report-fields); the code and that table must match.

Rules:
- **No** names, email, exact dates (only ISO weeks), food names, barcodes, free text, notes, household data, device identifiers or location finer than the Czech region (kraj).
- **Bands instead of exact values:** age in 5-year bands (18–24, 25–29 … 65+), BMI bands (< 18.5, 18.5–24.9, 25–29.9, 30–34.9, ≥ 35), body fat in 5 % bands.
- **Rounding:** kcal to 10, grams to 1, percentages to 1, weight change to 0.1 kg.
- **Nutrition** is sent only for weeks with at least 3 counted days (A7).
- **Cycle** summaries are sent per completed cycle (cycle length, period length, symptom counts per phase, average intake per phase), never with dates.
- The payload is stored in `research_reports.payload` before sending, so the "See what we send" screen shows exactly what leaves the phone.

## A22. Household sync

- **Hybrid logical clock (HLC):** `(wallMillis, counter, deviceId)`, compared in that order, encoded as `%013d-%05d-<deviceId>` so text order equals clock order.
- **Every change** to a shared table is written as an op: `{opId, deviceId, seq, hlc, table, rowId, changed columns}`. `seq` counts up per device.
- **Sync** between two devices: each sends its `{deviceId → maxSeqSeen}` map; the other answers with the ops the first is missing; both apply them.
- **Apply** an op: for each column, write it only if `op.hlc > sync_field_clock(table, rowId, column)`, then store the op's hlc there. Applying the same op twice changes nothing.
- **Taking the same box twice:** the earlier hlc wins. The later device keeps its diary entry (food was eaten), but the user is told "Anna had already taken box C3. Did you eat a different box?" with buttons to pick another box or keep it.
- **Compaction:** ops older than 30 days that every known device has seen may be deleted.

**Test cases:** two devices editing different columns of the same row both keep their change; the same column keeps the later hlc; ops applied in any order give the same final state (randomised test with 3 devices and 500 ops); a replayed op changes nothing.
