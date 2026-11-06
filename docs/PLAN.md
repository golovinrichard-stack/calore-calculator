# Plan: Calore as a meal-prep and nutrition manager

> **Status:** draft for discussion. Nothing in this plan is built yet.
> **Date:** 6 November 2026
>
> **How to read it:** sections 1 to 6 explain what the app will do and what we still need to decide.
> Section 7 is the technical design for when we start building.

## Contents

1. [The idea in one minute](#1-the-idea-in-one-minute)
2. [A week with the app](#2-a-week-with-the-app)
3. [Features](#3-features)
4. [More feature ideas](#4-more-feature-ideas)
5. [Roadmap](#5-roadmap)
6. [Decisions we need from you](#6-decisions-we-need-from-you)
7. [Technical design](#7-technical-design)
8. [Risks](#8-risks)
9. [Sources](#9-sources)

---

## 1. The idea in one minute

Today Calore is a calorie diary. You log what you eat and it adds up the numbers.

The new goal: Calore becomes your **nutrition manager**. You tell it about yourself once. Then every week it tells you:

- **what to cook**: recipes that fit your goal, your taste and your cooking days,
- **what to buy**: a shopping list grouped by shop, without the things you already have,
- **how much to put in each prep box**: grams per box, so each box hits your calories and protein.

After you cook, it keeps track of:

- **how old each box is**, and warns you before food spoils,
- **what you ate**: one tap on a box adds its calories and macros to the diary.

Two more parts:

- **Household:** people who live together share the shopping list, the fridge and the boxes. They can mark food as "don't touch" and ask each other to cook or to buy something.
- **Cycle tracking (optional):** people who menstruate can track their cycle and get food suggestions that fit each phase.

Everything the app does today stays: diary, food search, goals, calculator and weight.

**Words used in this plan**

| Word | Meaning |
|---|---|
| Prep box | One container with one portion of cooked food |
| Batch | One pot or tray of food you cooked. It fills several boxes. |
| Pantry | Food at home that is not cooked yet: in the fridge, freezer or cupboard |
| Target | Your daily calories, protein, carbs and fat |
| Eat-by date | The last day a box should be eaten, calculated by the app |
| Maintenance | The calories that keep your weight the same |

## 2. A week with the app

A short story, to check that we mean the same thing.

1. **Setup (once, about 2 minutes).** You enter sex, birth date, height, weight and your goal: cut, maintain or build. The first time you open the planner, it asks about food: what you like, allergies, which shops you use and which days you cook.
2. **Friday: plan.** You tap **Plan my week**. The app suggests: on Sunday cook chicken with rice and a beef chili; on Wednesday cook turkey pasta; breakfast is overnight oats. You swap the pasta for a curry. Done.
3. **Friday: shop.** The shopping list is ready: "Lidl: chicken breast 3 × 500 g, rice 1 kg, ...". The app already removed the rice you have at home. In the shop you tick items off. Ticked items go into your pantry with today's date.
4. **Sunday: cook.** **Cook mode** shows one timeline for both recipes: "Start the rice. Put the chicken in the oven. While it bakes, chop onions for the chili." When the chili is done, you put the pot on a kitchen scale. The app knows the weight of the empty pot and everything that went in, so it knows the calories per 100 g. It says: "Fill 6 boxes with 500 g each. Boxes 1 to 3 are for Monday to Wednesday: fridge. Boxes 4 to 6 go in the freezer: one for Thursday, two for next week."
5. **Monday to Saturday: eat.** The Today screen shows "Lunch: chili, box C2". You eat it and tap **Ate it**. The diary gets the exact grams, calories and macros. On Wednesday evening the app reminds you: "Move the Thursday chili box from the freezer to the fridge."
6. **Freshness.** Every box has an eat-by date. The fridge screen shows green, yellow or red. If you skip a box, the app moves it to another day if it is still fresh, or tells you to freeze it.
7. **Household.** Your partner sees the same shopping list and fridge. Your boxes show your name: don't touch. Your partner sends "Can you cook dinner on Tuesday?" and you get a notification.
8. **Sunday check-in.** "Your weight trend went down 0.4 kg this week, as planned. Your targets stay the same."

## 3. Features

Priorities: **Must** = needed for the main idea. **Should** = important, can come a bit later. **Could** = nice extra.

### 3.1 Your profile: what you tell the app

Ask little at the start and more when it is needed. Nobody wants to fill in a 40-question form before seeing anything useful.

| What you enter | Why the app needs it | When it asks | Priority |
|---|---|---|---|
| Sex, birth date, height, weight | Calorie formula. Birth date instead of age, so age updates itself. | Setup | Must |
| Goal: cut, maintain, build or recomp, and how fast | Calorie target | Setup | Must |
| Activity level, training days | Calorie target | Setup | Must |
| Body fat % (optional, with a tutorial) | Better formula and protein target | Setup or later | Should |
| Country, units (kg or lb), currency, language | Food database, typical foods, seasonal food, prices | First time in the planner | Must |
| Diet: everything, vegetarian, vegan, pescatarian, halal, kosher, low-carb ... | Filter recipes | Planner | Must |
| Allergies and intolerances: gluten, milk, eggs, nuts, peanuts, fish, shellfish, soy, sesame ... | **Hard rule**: never suggested | Planner | Must |
| Foods you love and hate, favourite cuisines, how spicy | Choose recipes you will enjoy | Planner | Must |
| Meals per day (breakfast? snacks?) and eating times | Split the daily target into meals | Planner | Must |
| Cooking days and time, e.g. "Sunday 2 h, Wednesday 1 h" | When to cook and how many batches | Planner | Must |
| Variety: "the same lunch 5 days is fine" or "different every day" | How many recipes per week | Planner | Should |
| Kitchen equipment: oven, air fryer, slow cooker, rice cooker, microwave at work | Filter recipes, reheating advice | Planner | Should |
| Boxes you own (how many, what size), freezer space | Portions, how much to freeze | Planner | Should |
| Weekly food budget | Cheaper recipes, cost warnings | Planner | Should |
| Your shops, and which shop for what | Shopping list per shop, prices | Shopping list | Should |
| Cooking skill | Recipe difficulty | Planner | Could |

*Recomp* means losing fat and building muscle at the same time: eating at maintenance with high protein.

The calorie formulas need the body's sex. **Cycle tracking is a separate switch.** It is suggested when "female" is chosen, but anyone can turn it on or off.

### 3.2 Body: body fat, measurements and better targets

**Body fat with a tutorial (Should)**

Three ways to enter it:

1. **Tape measure (recommended).** The US Navy method needs neck, waist and height, plus hips for women. The in-app tutorial shows with pictures:
   - Measure in the morning, before eating. Stand relaxed and don't pull in your belly.
   - Neck: just below the Adam's apple.
   - Waist: men at the belly button, women at the narrowest point.
   - Hips (women): at the widest point of the bottom.
   - Keep the tape flat and level. Snug, but not pressing into the skin.
   - Measure 3 times and use the average.
   - It is an estimate and can be off by a few percent, but it is very good for following the trend.
2. **Picture guide.** Pick the picture that looks most like you. Rough, but better than nothing.
3. **"I know my number."** From a smart scale, calipers, a DEXA scan or a gym. Type it in.

The app asks you to measure again every 2 to 4 weeks and shows the trend.

**Better calorie targets (Must)**

- If body fat is known, use the **Katch-McArdle** formula. It is based on lean body mass and works better for muscular or very lean people. Otherwise keep **Mifflin-St Jeor** (what the app uses now).
- Goals by **speed** instead of a fixed -500 / +300 kcal:
  - Cut: 0.5 %, 0.75 % or 1 % of body weight per week.
  - Build: 0.25 % or 0.5 % per week.
  - Recomp: maintenance calories with high protein.
  - The app shows both numbers: "-0.4 kg per week = about -440 kcal per day".
- **Protein first**, by body weight (for example 1.6 to 2.2 g per kg; the higher end when cutting). Then a minimum for fat. Carbs fill the rest. You can still set a % split by hand.
- **Safety:** keep today's minimums (1,200 kcal for women, 1,500 kcal for men). New: warn when weight loss is faster than 1 % per week, and when body fat is already very low.

**Smart maintenance calories (Should, strongly recommended)**

Formulas are only a first guess. After 2 to 3 weeks of logging food and weight, the app calculates your real maintenance from your own data.

> Example: you ate 2,500 kcal per day on average and your weight trend went down 0.3 kg per week.
> Maintenance is about 2,500 + 0.3 × 7,700 ÷ 7 = **2,830 kcal**.

A weekly check-in suggests new targets, in small steps.

**Also**

- **Weight trend line (Should).** Daily weight jumps up and down because of water and salt. A smoothed line shows the real direction.
- **Body measurements (Could).** Waist, hips, chest, arms, thighs. Useful when the scale does not move (recomp).
- **Progress photos (Could).** Stored only on the phone.

### 3.3 Recipes (Must)

- A recipe has ingredients in grams, steps, a number of servings and tags.
- Ingredients come from the food database: new **generic foods** such as "chicken breast, raw" (see 7.4), or Open Food Facts products.
- Each recipe also stores:
  - prep time, cook time, equipment, difficulty,
  - meal (breakfast, lunch, dinner, snack), cuisine, diet tags,
  - allergens (calculated from the ingredients),
  - **storage:** days in the fridge, can it be frozen, months in the freezer, how to reheat,
  - **good for meal prep or not** (for example: dressing on the side; fried food gets soggy),
  - calories and macros per serving (calculated).
- Where recipes come from:
  1. A **starter pack** in the app: about 40 to 60 meal-prep classics that we write ourselves. They work offline.
  2. **Your own** recipes.
  3. **Import from a website link (Could).** Most recipe websites contain machine-readable recipe data.
- **Scale** a recipe (×2, ×0.5) and **swap** ingredients (for example chicken → tofu). The numbers update.
- **The app learns the real yield.** Each time you weigh the pot it remembers how much cooked food the recipe made ("chili: 3,000 g last time"), so the next plan is more exact.

### 3.4 Cooking and prep boxes: how much to put in each box (Must)

This is the heart of the plan.

**The problem:** raw and cooked weights are different. Rice gets about 3 times heavier, chicken about 25 % lighter. Labels give values for raw food. So "150 g of chicken" is not clear.

**The solution: weigh the cooked food.**

1. You cook a batch. The app knows what went in (the raw ingredients), so it knows the calories and macros of the whole pot.
2. You weigh the finished food. Save the empty weight of your pots once ("big red pot = 1,850 g"), then just put the full pot on the scale.
3. The app divides: calories of the pot ÷ cooked weight = calories per 100 g.
4. It tells you the grams for each box.

**Example 1: a one-pot dish (chili)**

- Everything in the pot: 3,600 kcal. Cooked weight: 3,000 g. So 120 kcal per 100 g.
- Your lunch target is 600 kcal → **500 g per box**.
- Your partner's lunch target is 450 kcal → **375 g per box**. Same pot, different boxes.

**Example 2: a box with separate parts (chicken, rice, broccoli)**

- Each part is its own batch. From the weighed batches: cooked chicken has 165 kcal and 31 g protein per 100 g; cooked rice has 130 kcal and 2.7 g protein per 100 g.
- The box target is 700 kcal and 50 g protein.
- The app calculates: **130 g chicken + 280 g rice + 100 g broccoli + 10 g olive oil** = about 700 kcal and 50 g protein.

With a one-pot dish you can only change the amount. The calories will be exact, but protein follows the recipe. If protein is too low, the planner suggests a side (Greek yoghurt, eggs) or a recipe change (more meat, fewer beans).

**More details**

- **Two ways to split:** "5 equal boxes", or "each box = 600 kcal" (the app tells you how many boxes you get).
- **Leftovers:** what is left after filling the boxes becomes a small snack box, goes to the freezer, or you eat it now.
- **Box codes:** each box gets a short code, like **C3** (chili, box 3). Write it on the lid with a marker. Later maybe QR stickers (see section 4).
- **Freezer at portioning time:** the app tells you right away which boxes will not be eaten while still fresh, so they go to the freezer.

### 3.5 Fridge, freezer and pantry: freshness (Must)

**Prep boxes**

Each box knows what is inside, its grams, calories and macros, the cooking date, where it is (fridge or freezer), its eat-by date and its owner.

How the eat-by date is calculated:

- **Fridge:** cooking date + the recipe's fridge days. Default **3 days**. (The USDA says cooked leftovers keep 3 to 4 days in the fridge; we use the safer end.)
- **Some foods need less, and advice is not the same in every country.** Example: the UK NHS says to keep cooked rice in the fridge no more than 1 day before reheating. So there is a setting: **Standard** or **Strict** food-safety rules. In strict mode, rice boxes for later days go straight to the freezer.
- A box with several parts uses the **earliest** eat-by date of its parts.
- **Freezer:** the clock stops. Best quality within about 3 months (the USDA says 3 to 4 months for leftovers). After that the app warns about quality, not safety.
- **After thawing** in the fridge: eat within 24 hours (a careful default that you can change).

On screen:

- Colours: **green** = fresh, **yellow** = eat today or tomorrow, **red** = past the eat-by date. For red boxes the app suggests throwing them away and does not log them as eaten without asking.
- An **"eat first"** list on the Today screen, oldest boxes first.

**Reminders (notifications)**

- "2 boxes must be eaten today."
- "Move Thursday's box from the freezer to the fridge tonight."
- After cook mode: "Is the food in the fridge? Cooked food should go in the fridge within 2 hours."

**Thrown away (Should).** A thrown-away box goes into a waste log: grams, calories, money. Monthly stats like "3 boxes thrown away this month = €6.40".

**Pantry: food that is not cooked yet (Should)**

- Each item has a name, amount, place (fridge, freezer, cupboard), date bought, best-before date and date opened (opened food lasts less).
- Items are added by ticking them on the shopping list, by scanning a barcode, or by hand.
- When you cook, the recipe's ingredients are taken out of the pantry. You can correct the amounts.
- **"Use it up":** items close to their date, with recipe ideas that use them.

### 3.6 Weekly plan (Must)

- A grid: days × meals (breakfast, lunch, dinner, snacks: the same meals as the diary).
- Each meal is one of: **prep box**, **cook fresh**, **eat out / skip**, or **free** (you log what you eat).
- Each day shows planned calories and macros against your targets. The daily target is split by meal, for example breakfast 20 %, lunch 30 %, dinner 30 %, snacks 20 %. You can change this.
- Cooking days come from your profile. The plan makes sure boxes are eaten before their eat-by date. If they can't be, it plans freezing and thawing.
- Two ways to fill the plan:
  1. **By hand (first version).** You choose the recipes. The app calculates portions, how much to cook and the shopping list.
  2. **Automatic, "Plan my week" (later).** The app chooses recipes for you (see 7.3). You can lock meals you like and plan the rest again, or swap just one meal.
- **When plans change:** "I'm eating out tonight" → the dinner box moves to another day if it is still fresh, or the app tells you to freeze it.
- **Repeat** a good week, and save **templates** (for example "my cutting week").
- **Guests (Could):** "2 guests on Saturday" → that meal is scaled up and added to the shopping list.

### 3.7 Shopping list and shops (Must)

- Made from the plan: **what the plan needs, minus what you already have** (only if it will still be good when you need it).
- **Rounded to real packs.** You need 1,150 g chicken and it is sold in 500 g packs → buy 3 packs. The extra 350 g goes into the pantry, and next week's plan tries to use it. Pack sizes come from the product data, or you enter them once.
- **Grouped by shop and by section** (vegetables, meat, dairy ...). You can put the sections in the same order as your shop.
- Tick items off while shopping. Ticked items go into the pantry with today's date.
- Add other items by hand (toilet paper and so on). It is also a normal shopping list.
- **Prices (Should).** Enter what you paid. The app learns prices per shop and shows the cost per week, per day, per meal, and **per 100 g of protein** (great for bulking on a budget). Later: prices from **Open Prices**, a free price database from Open Food Facts, if it has data for your country.
- **Your shops:** pick them from a list or type the names. No GPS tracking. Later, optional: find shops near you on OpenStreetMap.
- **Share the list as text** to WhatsApp, Messenger or SMS. Useful even before the household feature exists.
- There is no open way to order from supermarkets online or to see what is in stock, so the app cannot order for you.

### 3.8 Eating from prep boxes: the diary (Must)

- The Today screen shows the planned meals: "Lunch: chili, box C2, 500 g, 600 kcal". Buttons: **Ate it**, **Ate part** (grams or %), **Skip** (move to another day or freeze), **Swap**.
- **Ate it** adds a normal diary entry with a copy of the numbers, linked to the box. Old days never change, as today.
- Eating something that was not planned works exactly as now: search, scan or custom food.

### 3.9 Cycle tracking (opt-in, Should)

**Tracking**

- Log the start and end of the period, the flow (light, medium, heavy) and, if you want, daily symptoms: cramps, bloating, cravings, mood, energy, sleep, headache.
- The app predicts the next period and the phases (period, follicular, ovulation, luteal) from your last cycles. It shows a **range of days**, not one exact day. The range is wider when cycles are irregular.
- Settings for hormonal contraception (pill, IUD, implant ...), pregnancy, breastfeeding and perimenopause or menopause. The phases work differently then, so predictions and tips change or pause.
- **Not for birth control or for getting pregnant.** The app says this clearly. (Apps for birth control are regulated as medical devices.)

**Food suggestions by phase: honest and based on research**

Many "eat for your cycle" claims online are not well supported by research. We only suggest things that make sense:

| Phase | What the app does |
|---|---|
| Period | Suggests **iron-rich** food (red meat, lentils, beans, tofu, spinach) together with **vitamin C**, which helps the body absorb iron. Most useful with heavy flow. Warm, easy meals if you log cramps. |
| Follicular and ovulation | Nothing special. Normal plan. |
| Luteal (the days before the period) | Cravings are common, so the plan has filling snacks with protein and fibre. **Magnesium-rich** food (nuts, seeds, dark chocolate, beans, whole grains); some studies suggest magnesium may ease PMS symptoms. A little calorie freedom, but research finds only a **small** rise in resting calorie burn (about 40 to 45 kcal per day in a 2024 study), so targets don't change much. |
| Before and during the period | **Water weight.** The weight chart marks these days. The trend line and smart maintenance take this into account, so the app does not think you gained fat and does not change your targets because of water. |

The planner gives a small bonus to recipes that fit the phase, for example iron-rich recipes in the period week. This needs iron and magnesium values, which come from the generic food database (7.4).

**Privacy: the most important rule here**

Cycle data is the most private data in the app.

- It stays on the phone. It is **never** shared with the household and never sent to a server.
- Optional app lock (fingerprint or PIN).
- One button deletes all cycle data.

### 3.10 Household: several people (Should, built last)

**Who:** a household is the people who live together. Each person has their own profile, goals and private diary. Children or people without a phone can be **managed profiles**: a parent plans and logs for them.

**Shared:**

- the shopping list, live (you see what others tick off),
- fridge, freezer and pantry,
- prep boxes and who owns them,
- recipes,
- the meal plan, if you want (for example shared dinners),
- an activity feed: "Anna bought eggs and milk", "Tom cooked chili (6 boxes)", "Tom took 2 eggs".

**Private** unless you choose to share: diary, weight, body fat, measurements, goals, cycle data.

**"Don't touch" and "Up for grabs"**

- Every item and box is one of:
  - **Mine, don't touch** (lock icon, name and colour),
  - **Shared** (anyone can use it),
  - **Up for grabs** (please eat it before it spoils; good against waste).
- Food that is planned for a meal is reserved automatically ("chicken: reserved for Anna's curry on Wednesday").
- Need something that isn't yours? Send **"Can I have it?"** The owner taps Yes or No.

**Requests**

- **Cook for me:** "Can you cook dinner on Tuesday?", with a recipe and portions if you want. Accept, decline or suggest another time.
- **Buy for me:** "Please buy oat milk." It goes on the shared list with your name.
- **Can I have it?** (see above).
- **Cooking turns (Could):** whose turn it is to cook, with reminders.
- Notifications for requests, answers, and "your box was taken".

**Cook once, portion for everyone.** One pot of chili, and the app calculates each person's box from their own target (see example 1 in 3.4). For this, the cook's phone needs the other people's meal targets, so each person decides whether to share them.

**Money (Could).** Who paid for the shopping, and a simple split between flatmates: "You owe Tom €12.40".

**This needs a sync server.** That is the biggest technical change in the whole plan. See section 6, question 2, and section 7.5.

## 4. More feature ideas

Ideas on top of what you asked for, roughly sorted by value. Size is relative: **S** small, **M** medium, **L** large.

| Idea | Why it helps | Size |
|---|---|---|
| Smart maintenance calories (3.2) | Learns your real calorie needs from your own data | S |
| Weight trend line | Stops worry about daily water weight | S |
| Cook mode | One timeline for all recipes, timers, screen stays on | M |
| Barcode scanner | Fast logging, fast adding to the pantry | M |
| Waste and money stats | Less waste; cost per meal, cost per 100 g protein | S |
| "Use it up" ideas | Recipes for food that will expire soon | M |
| Export and import to a file | Backup; move to a new phone without Google backup | S |
| Home-screen widget | Today's calories, next box, boxes that expire | M |
| Training-day and rest-day targets | More carbs on training days | S |
| Health Connect | Read steps and workouts, share nutrition with other fitness apps | M |
| Micronutrients (iron, calcium, magnesium, vitamin D, B12, fibre) | Needed for cycle tips and vegan diets | M |
| Supplements checklist | Creatine, vitamin D, iron ... | S |
| Water tracking | Simple daily goal | S |
| Protein per meal check | Spreads protein over the day | S |
| QR stickers on boxes | Scan the box and it is logged | M |
| Seasonal food for your country | Cheaper and tastier | M |
| Eating-out mode | Quick estimate, then balance the rest of the day | M |
| "Hide numbers" mode | Log food without seeing calories, for people who don't want to focus on numbers | S |
| Your language | The app already supports translations | S |

**Not now:**

- **Ordering from supermarkets online.** There are no open interfaces for it.
- **Reading receipt photos.** It needs large or closed-source libraries. Typing prices comes first.
- **AI chat in the cloud for meal plans.** It goes against the app's privacy rules. The planner works offline with normal algorithms. (Maybe later as an option with your own key.)
- A smartwatch app and Bluetooth kitchen scales.

## 5. Roadmap

Each phase is useful on its own and builds on the one before.

### Phase 0: Foundations

- Profile and a short setup; the other questions come later, when they are needed.
- Body fat with the tape-measure tutorial; Katch-McArdle; cut, maintain or build by speed; protein per kg.
- Weight trend line.
- Generic food database (offline).
- Export and import to a file.
- Database made ready for sync (see 7.2).

**Done when:** setup takes about 2 minutes, and entering body fat changes the targets.

### Phase 1: Recipes and prep boxes (the first big win)

- Recipe builder and starter recipes.
- Weigh the pot; boxes by count or by calories; boxes with several parts.
- Fridge and freezer screen with colours; freeze and thaw.
- "Ate it" and "Ate part" to the diary; waste log.
- Reminders: boxes expiring, thawing, putting food in the fridge.

**Done when:** you cook on Sunday, fill the boxes with the app, log each meal with one tap, and get a warning before anything spoils.

### Phase 2: Weekly plan and shopping

- Week plan by hand, targets per meal, how much to cook.
- Pantry with dates; barcode scanner.
- Shopping list: minus the pantry, rounded to packs, grouped by shop; tick → pantry; share as text.
- Prices you enter; cost per meal and per week.
- Cook mode.

**Done when:** planning the week and getting the shopping list takes less than 10 minutes.

### Phase 3: Smart planner

- "Plan my week": lock meals, plan again, swap one meal.
- "Use it up" ideas.
- Smart maintenance and the weekly check-in.
- Optional: training-day targets, Open Prices.

**Done when:** most weeks you accept the plan with 0 to 2 swaps.

### Phase 4: Cycle tracking

- Period and symptoms; predictions; settings for contraception, pregnancy and menopause.
- Phase tips in the planner; water-weight marks on the weight chart.
- App lock.

**Done when:** predictions work for regular and irregular cycles, and tips show up in the right week.

**Phase 4 can start any time after phase 0.** It does not depend on phases 1 to 3; the tips just get better once the planner exists.

### Phase 5: Household

- Sync server; join with a QR code; members and managed profiles.
- Shared list, pantry, boxes, recipes and plan; don't touch / shared / up for grabs.
- Requests, notifications and the activity feed.
- Cook once, portion for everyone.
- Optional: cooking turns, splitting costs.

**Done when:** two phones see the same list within seconds, and a request from one phone arrives on the other.

**Why household last:** it is the largest change (the first server, joining, sync, encryption). If phases 0 to 3 are built sync-ready (see 7.2), the household can share everything without rebuilding it.

### Phase 6: Extras

Pick from section 4: widget, Health Connect, water, supplements, QR stickers, guests, seasonal food, recipe import, measurements and photos.

## 6. Decisions we need from you

These change the plan. My recommendation comes first.

1. **Do all household members use Android?**
   - Yes → Android only. Simplest.
   - Someone has an iPhone → they can't use an Android app. Options: (a) a small web page for household members with the shopping list, fridge and requests, or (b) later an iPhone app that shares the core code (Kotlin Multiplatform). That is a lot of extra work.
2. **How should household data sync?**
   - **Recommended: a small sync server with end-to-end encryption.** The server only passes on locked data and cannot read it. Members join by scanning a QR code; no email and no password. Someone has to run the server: a small rented server (a few euros per month) or a computer at home, such as a Raspberry Pi.
   - A ready-made cloud backend such as Firebase: fastest to build, but the provider can read the data, and Firebase is a closed-source Google library that the project's rules forbid (CONTRIBUTING.md).
   - Only over home Wi-Fi, no server: very private, but it only works when both phones are at home. A "buy milk" request would not reach someone who is in the shop.
3. **Only for you and your family, or a public app (Google Play, F-Droid)?** A public app needs more polish, translations, legal texts (health data falls under GDPR in the EU) and support for many countries and shops.
4. **Your country, shops and language.** This decides which food database and which shop chains we support first.
5. **Order of phases.** Is the household more important to you than the smart planner? Then we swap phases 3 and 5.
6. **Food-safety default:** Standard (USDA: leftovers 3 to 4 days) or Strict (UK NHS: for example rice only 1 day)? It changes how much the app puts in the freezer.
7. **Starter recipes:** what kind of food should the first 40 to 60 recipes be (cuisines, favourite dishes)?

## 7. Technical design

### 7.1 Rules we keep

From README.md and CONTRIBUTING.md:

- No ads, analytics, trackers or closed-source SDKs.
- Data stays on the device. New network calls need a clear reason and send only what is needed. → Household sync is opt-in and end-to-end encrypted. Cycle data is never synced.
- Few dependencies. Prefer the Android platform and AndroidX.
- Pure logic lives in `domain/` with unit tests. The planner, box solver, shelf life, cycle prediction, body fat, smart maintenance and shopping list are all pure Kotlin, so they are easy to test.
- All user-visible text goes in `strings.xml`.
- Every Room change bumps the database version, adds a migration, and commits the schema JSON in `app/schemas/`.
- Diary entries keep a copy of the nutrients, so history never changes. Boxes follow the same idea: a batch stores its own nutrients per 100 g when it is cooked.

### 7.2 Data model

**Sync-ready from day one**, even before the household exists, so nothing has to be rebuilt later:

- New tables use **UUID** text ids. Auto-increment numbers would clash between phones.
- Existing tables (`foods`, `diary_entries`, `weight_entries`) get an extra `uuid` column and keep their number ids.
- Every syncable row has `createdAt`, `updatedAt` and `deletedAt` (soft delete, so deletions can sync too).
- Rows that can belong to someone have `ownerMemberId` and `visibility` (`MINE`, `SHARED`, `UP_FOR_GRABS`).

**Profile and body**

| Table | Main fields |
|---|---|
| `members` | id, name, colour, isManaged, sex, birthDate, heightCm, activity, goalType, goalRatePct, proteinPerKg, mealSplit, diet, allergies, likes, dislikes, cuisines, cookingSchedule, budget, country, units. Replaces the calculator inputs now kept in DataStore; the phone's own user is the "me" member. |
| `body_measurements` | id, memberId, date, neckCm, waistCm, hipCm, other measures, bodyFatPct, method (`NAVY`, `VISUAL`, `MANUAL`). Weight stays in `weight_entries`. |

**Foods and recipes**

| Table | Main fields |
|---|---|
| `foods` (changed) | + uuid, category (shop section), packageGrams, isGeneric, sourceRef (USDA id or barcode), micronutrients (iron, calcium, magnesium ..., all optional), defaultStorage, shelfLifeDays. The per-100 g design stays. |
| `recipes` | id, name, servings, steps, prepMin, cookMin, equipment, mealTypes, tags, fridgeDays, freezable, freezerDays, reheatNotes, learnedYieldGrams |
| `recipe_ingredients` | recipeId, foodId, grams (raw), note, optional |
| `pots` | id, name, emptyGrams |

**Cooking and boxes**

| Table | Main fields |
|---|---|
| `batches` | id, recipeId (empty for a single food, e.g. plain rice), cookedAt, cookedByMemberId, totalCookedGrams, nutrients per 100 g (copied) |
| `boxes` | id, code (`C3`), ownerMemberId, visibility, location (`FRIDGE`, `FREEZER`), state (`READY`, `EATEN`, `PART_EATEN`, `WASTED`), frozenAt, thawedAt, eatBy, planSlotId |
| `box_parts` | boxId, batchId, grams (a box holds one or more batches) |

**Pantry, shops and shopping**

| Table | Main fields |
|---|---|
| `stores` | id, name, chain, sectionOrder |
| `pantry_items` | id, foodId or name, amount, unit, location, boughtAt, bestBefore, openedAt, ownerMemberId, visibility, reservedForSlotId |
| `shopping_items` | id, foodId or name, amount, packs, storeId, section, checkedAt, checkedBy, requestedBy, fromPlanId |
| `prices` | id, foodId or barcode, storeId, price, currency, packageGrams, date, source (`ME`, `OPEN_PRICES`) |

**Planning and diary**

| Table | Main fields |
|---|---|
| `plans` | id, weekStart |
| `plan_slots` | id, planId, date, meal, memberId, kind (`BOX`, `COOK_FRESH`, `EAT_OUT`, `FREE`), recipeId, target kcal and macros, boxId |
| `diary_entries` (changed) | + uuid, boxId (optional), memberId |

**Cycle (never synced)**

| Table | Main fields |
|---|---|
| `cycle_periods` | id, startDate, endDate |
| `cycle_days` | date, flow, symptoms, note |
| settings (DataStore) | contraception, pregnant, breastfeeding, menopause, manual cycle length |

**Household (phase 5)**

| Table | Main fields |
|---|---|
| `household` | id, name, key material |
| `requests` | id, type (`COOK`, `BUY`, `CAN_I_HAVE`, `OTHER`), fromMemberId, toMemberId (or anyone), item, message, dueAt, status (`OPEN`, `ACCEPTED`, `DECLINED`, `DONE`) |
| `activity` | id, memberId, type, item, at |
| `sync_outbox` | changes waiting to be sent |

### 7.3 Calculations

1. **Body fat, US Navy method** (cm, log base 10):
   - Men: `BF% = 495 / (1.0324 − 0.19077·log10(waist − neck) + 0.15456·log10(height)) − 450`
   - Women: `BF% = 495 / (1.29579 − 0.35004·log10(waist + hip − neck) + 0.22100·log10(height)) − 450`
2. **BMR:** Katch-McArdle `370 + 21.6 × lean mass (kg)`, where `lean mass = weight × (1 − BF% / 100)`. Without body fat: Mifflin-St Jeor (current code).
3. **Goal speed:** `daily kcal change = weight × rate% / 100 × 7,700 / 7`. Example: 80 kg at 0.5 % per week = 0.4 kg per week ≈ 440 kcal per day.
4. **Trend weight:** exponential moving average, for example `trend = trend + 0.1 × (today − trend)`.
5. **Smart maintenance:** over the last 14 to 28 days, with enough logged days: `maintenance ≈ average intake − (trend change in kg × 7,700 ÷ days)`. Skip days without a log. Give less weight to cycle water-weight days. Move targets slowly (at most about 100 to 150 kcal per week).
6. **Box solver:** for a box with 1 to 4 parts, find the grams of each part (between a minimum and a maximum) so the box is as close as possible to its target. Calories matter most, then protein, then carbs and fat. It is a tiny bounded least-squares problem: pure Kotlin, no library. Round to 5 g.
7. **Eat-by date:** `eatBy = earliest over parts (batch.cookedAt + fridgeDays)`. In the freezer the clock stops. After thawing: `thawedAt + thawWindow` (default 24 h). Quality warning after about 3 months frozen.
8. **Cycle prediction:** average of at most the last 6 cycle lengths, with recent cycles counting more. Next period = last start + average. Ovulation ≈ next period − 14 days. The range comes from how much past cycles varied. Use 28 days until 2 cycles are logged.
9. **"Plan my week" (phase 3):**
   1. Filter with hard rules: diet, allergies, disliked foods, equipment, time per cooking day.
   2. Score each recipe: likes, cuisine, variety (not the same as last week), cost, uses pantry food that will expire, protein per calorie compared to the target, cycle phase, good for meal prep.
   3. Fill the cooking days. Each cooking session covers the meals until the next one. Check eat-by dates; if a meal falls after them, plan freezing and thawing.
   4. Pick the best combination: a greedy choice, then a few rounds of "swap one recipe and keep the change if the score is better". The search space is small (a few hundred recipes), so it is fast on a phone, works offline and needs no AI.
   5. Run the box solver for each meal → batch sizes → raw ingredient amounts → shopping list.
10. **Shopping list:** add up raw grams per food over all batches → subtract pantry food that will still be good → round up to whole packs → choose the shop (your default shop for that section, or the cheapest known price) → sort by the shop's section order.

### 7.4 Data sources

| What | Source | Licence | Notes |
|---|---|---|---|
| Branded products and barcodes | Open Food Facts (already used) | ODbL | Barcode lookup for the scanner |
| Generic foods with micronutrients (raw chicken, rice, vegetables) | USDA FoodData Central (Foundation Foods, SR Legacy) | Public domain (CC0) | Ship a selected subset in the app (a few MB). Later, a national food database for your country, if its licence allows it. Names need translating. |
| Prices | Your own entries; later Open Prices (Open Food Facts) | ODbL | Coverage depends on the country |
| Shops | You pick or type them; optional OpenStreetMap search later | ODbL | No GPS tracking |
| Recipes | Starter pack written by us; your own; import from websites (schema.org `Recipe`) | Ours: 0BSD | Imported recipes are for personal use |
| Storage times | USDA FSIS, UK NHS | | Careful defaults, all editable |

### 7.5 Household sync (phase 5)

- **Local first.** The app works fully offline. Sync is an opt-in switch.
- **Server.** Small and self-hostable (for example a Docker container). It only stores and forwards encrypted changes for each household. It never sees food names, people's names or anything else.
- **Joining.** The first member creates the household and shows a QR code with the household id and the secret key. The others scan it. No email, no password.
- **Encryption.** Each change is encrypted on the phone with the household key, using a well-known open-source library (libsodium or Google Tink).
- **Changes, not copies.** Every edit is a small change record ("item 42 ticked by Anna at 18:02"). Phones send their changes and download the others'. Conflicts: the last change wins for each field. For "taking the last box", the first claim wins (server order) and the second person sees "already taken".
- **Notifications.** Instant request alerts need push messages. Google's push service (FCM) is closed source and does not work on phones without Google services. Open option: **UnifiedPush**, an open standard where the user picks a distributor app such as ntfy. Fallback: check every 15 minutes and when the app opens. To decide in phase 5.
- **What syncs:** shopping list, pantry, boxes, recipes, the shared parts of the plan, requests, activity, members' display names and colours, and meal targets only if the person allows it.
- **Never synced:** diary, weight, body measurements, cycle data. (Maybe later: "sync between my own devices", with a separate key.)

### 7.6 New libraries (all open source)

| Library | For | Phase |
|---|---|---|
| WorkManager (AndroidX) | Reminders, expiry checks, background sync | 1 |
| CameraX (AndroidX) + ZXing core (Apache 2.0) | Barcode scanning without Google Play services | 2 |
| Biometric (AndroidX) | App lock | 4 |
| libsodium binding or Tink | Household encryption | 5 |
| UnifiedPush connector (optional) | Instant household notifications | 5 |
| Server: Kotlin + Ktor (or similar) + SQLite or Postgres | Sync server | 5 |

Avoid: ML Kit barcode scanning (closed source) and Firebase.

### 7.7 Screens and code

**Bottom bar.** Today it is Diary · Weight · Goals. Proposal (5 tabs at most):

| Tab | Contains |
|---|---|
| Today | Diary, today's boxes, "eat first", quick log |
| Plan | Week plan, recipes, cook mode |
| Kitchen | Fridge, freezer and pantry: boxes and groceries with dates |
| Shop | Shopping lists |
| Me | Profile, goals and calculator, body (weight, body fat, measurements), cycle, household, settings, about |

**Code structure** (same style as now):

```
domain/
  body/        body fat, Katch-McArdle, goal speed, trend weight, smart maintenance
  recipe/      recipe nutrients, scaling, allergens
  prep/        batches, box solver, eat-by dates
  plan/        planner, meal split, scoring
  shopping/    adding up, rounding to packs
  cycle/       prediction, phases, tips
  household/   ownership rules, requests
data/
  db/          new entities and migrations
  sync/        phase 5
ui/            one package per screen, as now
server/        phase 5, separate module or repository
```

**Existing code that changes**

- `domain/CalorieCalculator.kt`: add Katch-McArdle, goals by speed (replacing the fixed `LOSE(-500)` and `GAIN(300)`), protein per kg.
- `data/prefs/SettingsStore.kt`: the profile moves to the `members` table, with a one-time copy of the saved DataStore values.
- `data/db/Entities.kt` and `CaloreDatabase.kt`: new columns and tables; version 1 → 2 and later, with migrations.
- `ui/weight/`: becomes the "Body" screen (weight, trend, body fat, measurements).
- `ui/navigation/CaloreNavHost.kt`: the new tabs.
- `AppContainer.kt`: the new repositories.

## 8. Risks

| Risk | What we do about it |
|---|---|
| The scope is huge: meal planner, pantry, cycle tracker and family app in one | Strict phases; each phase is useful alone; household last |
| Food data quality (generic foods, raw vs cooked) | "Weigh the pot" avoids guessing raw vs cooked; a curated generic food list |
| Food safety: the app says "fine" but the food has gone bad | Careful defaults; clear text: "a guide, not a guarantee. When in doubt, throw it out." |
| Health claims (cycle, body fat) | Neutral, educational wording; "not medical advice"; not for birth control |
| Private data (cycle, body) | Only on the phone; app lock; never shared with the household |
| Server cost and upkeep | Small, self-hostable, encrypted; the household feature is optional |
| Unhealthy use (eating too little, obsessing over numbers) | Keep the calorie minimums; warn about fast weight loss; "hide numbers" mode; neutral wording |
| The app gets bigger (food database) | Ship a selected subset of a few MB |
| Push notifications without Google | UnifiedPush or regular checks; decide in phase 5 |

## 9. Sources

- USDA Food Safety and Inspection Service, "Leftovers and Food Safety": put cooked food in the fridge within 2 hours; leftovers keep 3 to 4 days in the fridge, and 3 to 4 months in the freezer for best quality.
- NHS (UK): keep cooked rice in the fridge no more than 1 day before reheating; eat defrosted food within 24 hours.
- Benton, Hutchins and Dawes (2020), *Effect of menstrual cycle on resting metabolism: a systematic review and meta-analysis*, PLoS ONE: <https://pubmed.ncbi.nlm.nih.gov/32658929/>
- Löfberg et al. (2024), *Medicine & Science in Sports & Exercise*: resting energy use about 40 to 45 kcal per day higher in the luteal phase.
- Open Prices (Open Food Facts): <https://openfoodfacts.github.io/open-prices/>
- USDA FoodData Central: <https://fdc.nal.usda.gov/>
- UnifiedPush: <https://unifiedpush.org/>
