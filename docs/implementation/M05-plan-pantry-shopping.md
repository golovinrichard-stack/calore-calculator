# M5: Weekly plan, pantry and shopping

**Goal:** plan the week by hand with your chosen number of cooking days, keep a pantry with dates, and get a shopping list that names the **exact product to buy in each shop**, with prices and statistics.
**Users get:** "Lidl: Kuřecí prsní řízky 500 g × 3", a shopping list without the things they already have, and what their food costs.
**Depends on:** M4.
**Read first:** [data-model.md](data-model.md) (M5), [algorithms.md](algorithms.md) A8, A10, A13, A14, A16–A18.
**New dependencies:** none.
**Waiting for:** owner input #1 (Czech shop product research) for the seed file in M5.6. Everything else can be built without it.

## Tasks

### M5.1 Plan, pantry and shop data (database version 7)

- [x] Tables `plans`, `plan_slots`, `plan_cooks`, `pantry_items`, `stores`, `store_products`, `prices` and `shopping_items` (data-model M5).

### M5.2 How often you cook

The user decides how many times a week to cook.

- [x] The first time the week plan opens, ask: **How many times a week do you want to cook?** (1–7), which days (optional; otherwise A14 picks them), minutes per session, and which meals come from prep boxes. Also editable in Me → Profile → Cooking.
- [x] Show what it means: "Sunday: Sunday dinner to Wednesday lunch; Wednesday: Wednesday dinner to Sunday lunch" (for lunches and dinners from boxes), and the freezer warning for one session a week (A14).

### M5.3 The week plan, by hand

- [x] Plan tab → **Week**: a grid of days × meals, for the chosen people (me plus people I cook for). Cooking days are marked.
- [x] Tap a meal: **Prep box** (choose a recipe), **Cook fresh** (choose a recipe), **Eat out**, or **Free** (log whatever you eat).
- [x] Every day shows planned calories and macros against the targets (A8), respecting hide-numbers mode.
- [x] Warnings: "This box will be 4 days old: it will go in the freezer" (A13).
- [x] **Repeat last week** and **Save as template** / **Use template**.

**Done when:** a week can be planned for two people with 2 cooking days, and the daily totals match the slot targets.

As built: a cooking session is in the afternoon (after lunch), so with Sunday and Wednesday the two sessions share the week's prepped meals exactly (see A14). A day's planned calories are the targets of its prep-box and cook-fresh slots; "Eat out" and "Free" count as unplanned. Templates keep their original dates and are moved to the chosen week when used. The Week tab stays behind `FeatureFlags.weekPlan` until M5 is finished.

### M5.4 From the plan to the pot

- [x] For every cooking day and recipe, compute `plan_cooks` from the planned slots: portions, target cooked grams, recipe scale (A15 step 4).
- [x] On the cooking day, **Cook** opens the M4 flow with everything filled in. After filling the boxes, each box is linked to its slot, frozen if needed, and thaw reminders are set (A13).

As built: the week shows "Cooking for this plan" (day, recipe, portions, target grams) with **Cook**. A cooking day can feed meals in the next week too (Sunday for Monday to Wednesday), so Cook reads the slots of that week and the next. Before the first weighing a recipe's cooked weight is estimated as its raw weight (A9 default). Boxes are sized with the density actually weighed; a slot without a target gets one serving. Eating a box marks its slot done (and Undo clears it). Thaw reminders come from the daily 19:00 job (M4.9).

### M5.5 Pantry

- [x] Kitchen tab → **Pantry**: items grouped by fridge, freezer and cupboard, coloured by best-before date with the same rules as boxes.
- [x] Add items by hand, by scanning a barcode (M2.4), or automatically when ticking the shopping list.
- [x] Edit amount, best-before date (the date on the pack wins over A17 defaults), opened date. Actions: **Used up**, **Throw away**.
- [x] Cooking takes ingredients out first-in-first-out (A17), with the "pantry was short" message.

**Done when:** ticking 3 packs of chicken adds 1,500 g to the pantry; cooking a recipe with 1,150 g leaves 350 g.

As built: the "pantry was short" message names only foods the pantry had some of; foods never added to the pantry are taken as not tracked. Pieces count with the food's grams per piece. Items without a food (toilet paper) default to the cupboard and 180 days.

### M5.6 Shops and exact products

- [x] Seed `app/src/main/assets/seed/cz/stores.json` with the common Czech shops: Albert, Billa, Globus, Kaufland, Lidl, Penny, Tesco, Rohlik.cz, Košík.cz, and "Market / other". The user picks favourites, and a default shop per section (for example meat at Lidl, vegetables at the market).
- [x] **Exact products** come from three places, in this order:
  1. **The owner's research** (input #1), converted into `app/src/main/assets/seed/cz/store-products.json`:

     ```json
     { "version": 1, "country": "CZ", "products": [ {
       "key": "cz:lidl:chicken-breast-500",
       "store": "cz:lidl",
       "food": "generic:chicken_breast_raw",
       "name": "Kuřecí prsní řízky",
       "brand": null,
       "barcode": null,
       "packAmount": 500, "packUnit": "G",
       "priceCzk": null, "priceDate": null,
       "source": "owner-research"
     } ] }
     ```

     Leave `null` for anything the research doesn't say; don't invent values.
  2. **The user's own picks:** on any shopping line, **Pick a product**: search Open Food Facts (Czech products, the shop's tag if possible), scan the barcode in the shop, or type the name and pack size. It is saved as this shop's preferred product for that food and used from then on.
  3. **Open Food Facts** data for a scanned product (pack size from `product_quantity`).
- [x] Store product detail: name, brand, pack size, barcode, last price and its date.

As built so far: the shops seed (names only, a general section order) imports once per version; Settings → Shops sets favourites and a default shop per section. The loader for `store-products.json` is ready and runs only when the file exists (it isn't shipped until input #1); products keep their seed key, a user's pick stays preferred, and prices are stored only when the research has them. Picking a product on a shopping line comes with M5.7.

**Done when:** with the seed file present, the chicken line shows the seeded product; without it, the line shows the basic food and **Pick a product**, and after one pick the product appears every time.

### M5.7 The shopping list

- [x] **Shop** tab (bottom bar complete: **Today · Plan · Kitchen · Shop · Me**).
- [x] Generated from the plan with A16: what the plan needs, minus usable pantry food, rounded up to whole packs of the chosen product, grouped by shop and sorted by the shop's section order.
- [x] Each line shows the exact product name, brand, pack size × number of packs, and the estimated price if known. Tapping it shows which recipes need it.
- [x] Manual items can be added (toilet paper and so on).
- [x] **Tick** a line: it moves to "In the basket", becomes a pantry item (amount = packs × pack size, storage and best-before default from the food), and optionally asks for the price paid (one number field, can be skipped).
- [x] **Clear ticked items**. Regenerating keeps manual items and ticks.

**Done when:** the A16 test cases pass end to end in an instrumented test (plan → list → tick → pantry).

As built: the list updates from the plan when the Shop tab opens (and with the refresh button): the cooks still to do this week and next week, from today on. A product picked on a line (typed, from Open Food Facts search, or scanned) becomes that shop's preferred product for the food. The product detail on a line shows brand and barcode; prices show as an estimate on the line. The Shop tab stays behind `FeatureFlags.weekPlan` until M5 is finished.

### M5.8 Prices and costs

- [ ] Price history per product (from ticks, the seed, or typed in on the product).
- [ ] Costs with A18: per batch, per box (shown in the box detail), per meal, day and week in the plan. Cost per 100 g protein on recipes.
- [ ] If a weekly budget is set: the plan shows the estimated total and warns when it is over budget.

### M5.9 Statistics: waste and money

- [ ] Me → **Stats**, per month: boxes and calories thrown away, money thrown away, money spent, average cost per day, and your cheapest protein sources (cost per 100 g protein, from what you bought).
- [ ] Simple bar charts in the same style as the weight chart.

### M5.10 Share the list as text

- [x] **Share** sends the list (unticked items, grouped by shop) through the Android share sheet, so it can go to WhatsApp, Messenger or SMS. Czech formatting.

### M5.11 Today, and when plans change

- [ ] Today shows the planned meals: box slots with **Ate it** (M4.7), cook-fresh slots with a link to the recipe, eat-out slots with **Quick add**.
- [ ] **Skip / move** a planned box: move it to another day if its eat-by date allows; otherwise suggest freezing it (if it isn't past its date) or offer it to another person.

## Out of scope

Automatic plans (M6), sharing the list live with other phones (M11), Open Prices and other price sources (later).
