# Plan: Calore as a meal-prep and nutrition manager

> **Status:** agreed plan, version 2, 6 November 2026. M0 (Czech language, backup to a file), M1 (profile, body and smart calorie tracking), M2 (basic foods, barcode scanner, quick add), M3 (recipes, starter pack, logging a dish), M4 (cooking, prep boxes, freshness), M5 (week plan, pantry, shopping list, prices and statistics; the shop product research still to come) M6 (the smart planner, "Plan my week") and M7 (cycle tracking) are built; the rest is not built yet.
> The owner's decisions are in [section 6](#6-decisions). The step-by-step build plan is in [`implementation/`](implementation/README.md).

## Contents

1. [The idea in one minute](#1-the-idea-in-one-minute)
2. [A week with the app](#2-a-week-with-the-app)
3. [Features](#3-features)
4. [Extra features and ideas for later](#4-extra-features-and-ideas-for-later)
5. [Roadmap](#5-roadmap)
6. [Decisions](#6-decisions)
7. [Risks](#7-risks)
8. [Other documents](#8-other-documents)

---

## 1. The idea in one minute

Today Calore is a calorie diary. You log what you eat and it adds up the numbers. **All of that stays**: the diary, manual logging, food search, custom foods, goals, the calorie calculator and the weight history.

On top of it, Calore becomes your **nutrition manager**. You tell it about yourself once, and every week it tells you:

- **what to cook**: recipes that fit your goal, your taste and the days you want to cook,
- **what to buy**: the exact products in your shops, minus what you already have,
- **how much to put in each prep box**: grams per box, so each box hits your calories and protein.

After you cook, it keeps track of:

- **how old each box is**, and warns you before food spoils,
- **what you ate**: you tap **Ate it** and the app logs the calories and macros by itself.

More parts:

- **Smart calorie tracking:** the app learns your real calorie needs from your weight and your food log.
- **Supplements (optional)**, with reminders if you want them.
- **Cycle tracking (optional)**, with food suggestions for each phase.
- **Household:** people at home share the shopping list, the fridge and the boxes over the home Wi-Fi.
- **Help research (optional):** people who agree send weekly summaries to a research database about how people eat.

**First version:** Android only, for the Czech Republic (Czech and English, Kč, Czech shops).

**Words used in this plan**

| Word | Meaning |
|---|---|
| Prep box | One container with one portion of cooked food |
| Batch | One pot or tray of food you cooked. It fills several boxes. |
| Pantry | Food at home that is not cooked yet: in the fridge, freezer or cupboard |
| Target | Your daily calories, protein, carbs and fat |
| Eat-by date | The last day a box should be eaten, calculated by the app |
| Maintenance | The calories that keep your weight the same |
| Hub | The family phone that keeps the household's shared data |

## 2. A week with the app

1. **Setup (once, about 2 minutes).** You enter sex, birth date, height, weight and your goal: cut, maintain or build. The first time you open the planner it asks about food (likes, allergies, your shops) and **how many times a week you want to cook**. You say: twice, Sunday and Wednesday.
2. **Friday: plan.** You tap **Plan my week**. The app suggests chicken with rice and a chili for Sunday, a curry for Wednesday, and overnight oats for breakfast. You swap the curry for goulash. Done.
3. **Friday: shop.** The shopping list shows exact products by shop: "Lidl: Kuřecí prsní řízky 500 g × 3", "Albert: …". The rice you have at home is already taken off. In the shop you tick items, and they go into your pantry with today's date.
4. **Sunday: cook.** **Cook mode** shows one timeline for both recipes, with timers. When the chili is done, you put the pot on the kitchen scale. The app knows the empty pot's weight and everything that went in, so it says: "Fill 6 boxes with 500 g each. Boxes 1–3 go in the fridge for Monday to Wednesday. Boxes 4–6 go in the freezer."
5. **Monday: eat.** Today shows "Lunch: chili, box C1". You eat it and tap **Ate it**. The diary gets the grams, calories and macros; the box disappears from the fridge list. A snack bar from the shop you log by hand, as always.
6. **Wednesday evening:** "Move box C4 from the freezer to the fridge for tomorrow."
7. **Every day:** boxes show green, yellow or red. If you skip a box, the app moves it to another day if it is still fresh, or tells you to freeze it.
8. **At home:** your partner's phone syncs with the family hub phone over the Wi-Fi. They see the same list and fridge. Your boxes say "Mine, don't touch". They send you "Can you cook dinner on Tuesday?".
9. **Monday morning check-in:** "Your weight trend went down 0.4 kg, as planned. Your target stays at 2,200 kcal."

## 3. Features

### 3.1 Profile: what you tell the app

Ask little at the start, more when it is needed.

| What you enter | Why | When it asks |
|---|---|---|
| Sex, birth date, height, weight | Calorie formula | Setup |
| Goal (cut, maintain, build, recomp) and speed | Calorie target | Setup |
| Activity level | Calorie target | Setup |
| Body fat % (optional, with a tutorial) | A better formula and protein target | Setup or later |
| Diet (everything, vegetarian, vegan, pescatarian, no pork) | Filter recipes | First plan |
| Allergies (the 14 EU allergens) | Never suggest them | First plan |
| Foods you like and dislike, favourite cuisines | Choose recipes you enjoy | First plan |
| Meals per day, and which ones come from prep boxes | Split the target into meals | First plan |
| **How many times a week you cook** (1–7), which days (optional), time per session | When to cook, how much to freeze | First plan |
| Kitchen equipment, variety, weekly budget | Better plans | First plan, optional |
| Your shops and which shop for what | Exact products, prices | First shopping list |

*Recomp* means losing fat and building muscle at the same time: eating at maintenance with high protein.

The calorie formulas need the body's sex. Cycle tracking is a separate switch: it is suggested when "female" is chosen, and anyone can turn it on or off.

### 3.2 Body and smart calorie tracking

- **The calculator stays**, and gets better: goals by speed (for example "cut 0.5 % of body weight per week = −440 kcal per day" at 80 kg), protein per kg of body weight, and the Katch-McArdle formula when body fat is known.
- **Body fat with a tutorial:** tape-measure method (US Navy) with pictures: where to measure the neck, waist and hips, and how. Or type in a number from a smart scale or a gym.
- **Weight trend line:** a smoothed line, so daily water-weight jumps don't worry you.
- **Smart maintenance:** after 2–3 weeks of logging, the app calculates your real maintenance from your weight trend and food log, and the **weekly check-in** suggests new targets in small steps.
- **Hide-numbers mode:** log food without seeing calories.
- **Safety:** the existing calorie minimums stay (1,200 kcal women, 1,500 kcal men), with warnings for very fast weight loss.

### 3.3 Recipes and dishes

- Recipes with ingredients in grams, steps (with times for cook mode), servings, storage (fridge days, can it be frozen), meal types, cuisine and allergens. Calories and macros per serving are calculated.
- **A starter pack of 30 recipes**, a random mix of Czech home cooking (guláš, čočka na kyselo, kuře na paprice …) and meal-prep classics (chicken with rice, chili, overnight oats …).
- Your own recipes; scale and swap ingredients.
- **Log a dish by hand:** "1.5 servings of my goulash".

### 3.4 Cooking and prep boxes: how much to put in each box

**Weigh the cooked food.** Raw and cooked weights are different (rice gets about 3 times heavier, chicken about 25 % lighter). The app knows what went into the pot. You weigh the finished food once (it remembers your empty pots), and it calculates the calories per 100 g and the grams for each box.

- **One-pot dish:** chili with 3,600 kcal weighs 3,000 g → 120 kcal per 100 g. Your 600 kcal lunch = **500 g**. Your partner's 450 kcal lunch = **375 g**, from the same pot.
- **Box with separate parts:** chicken, rice and broccoli. The app solves the grams of each part to hit calories and protein: **130 g chicken + 280 g rice + 100 g broccoli + 10 g oil ≈ 700 kcal and 50 g protein**.
- **Cook once for the family:** each person's box is sized from their own target, also for people without the app (for example children).
- **Cook mode:** one timeline for all recipes of the session, with timers.
- **Box codes** (C1, C2 …) to write on the lids; boxes that can't be eaten in time go straight to the freezer.

### 3.5 Fridge, freezer and pantry: freshness

- Every box has an **eat-by date**: cooking day + 3 days in the fridge by default (Czech and US advice for cooked food is 3–4 days; we use the safer end). Each recipe can change it.
- The freezer stops the clock; after thawing, eat within 24 hours; quality warning after about 3 months frozen.
- **Green / yellow / red** colours, an **eat first** list, and reminders: what to eat today, what to thaw tonight, "is the food in the fridge?" 2 hours after cooking.
- **Pantry:** groceries with amounts and dates, added by ticking the shopping list or by scanning a barcode, and used up automatically when you cook.
- **Waste log:** thrown-away boxes and food, with their calories and money.

### 3.6 Weekly plan and how often you cook

- **You decide how many times a week to cook** (1–7) and, if you want, on which days. The app shows what each cooking day covers ("Sunday covers Monday to Wednesday") and warns that with one cooking day about half the boxes will be frozen.
- A week grid: for each meal, a prep box, cook fresh, eat out, or free (you log what you eat).
- Each day shows planned calories and macros against your targets.
- Repeat last week; save templates.
- When plans change ("I'm eating out tonight"), the box moves to another day if it is still fresh, or goes to the freezer.

### 3.7 Smart planner: "Plan my week"

**The most important feature after the basics.** One button plans the week:

- picks recipes that fit your diet, allergies, likes, cuisines, equipment, variety and budget, and that use food from your pantry before it expires;
- spreads them over your cooking days so every box is eaten before its eat-by date (or frozen in time);
- sizes every box for its person's targets;
- creates what to cook on each cooking day and the shopping list.

You can **lock** meals you like, **swap** one meal (with the top 5 alternatives and why), or **regenerate** the rest. It runs on the phone, works offline, and needs no AI service. It gets better as you rate meals with 👍 / 👎. **Use it up** shows recipes for food that will expire soon.

### 3.8 Shopping list with exact products

- Made from the plan: **what the plan needs, minus what you have** (if it will still be good), **rounded to whole packs** of a real product.
- **Exact products per shop:** "Lidl: Kuřecí prsní řízky 500 g × 3". The product data comes from the owner's research on Czech shops (still to be added), from products you pick yourself (search, or scan the barcode in the shop), and from Open Food Facts. Once you pick a product, it is used every time.
- Grouped by shop and by section, in the order of your shop.
- Tick items while shopping: they go into the pantry. Type the price if you want.
- **Prices and money:** the cost per meal, day and week; the cost per 100 g of protein; money thrown away. Monthly stats.
- **Share the list as text** (WhatsApp, Messenger, SMS).

### 3.9 Tracking: the diary, manual logging and automatic logging

- **The diary stays as it is**, with the actual calories, protein, carbs, fat, fibre and sugar for the day against your targets, now also showing the planned meals.
- **Manual logging stays** for everything that isn't a prep box: search, barcode scan, custom foods, **log a dish by hand**, and **quick add** (type calories and macros, for example for a meal out).
- **Automatic logging:** when you eat a prep box, you tap **Ate it** (or **Ate part**). The app logs the exact nutrients, removes the box from the fridge, marks the planned meal as done and updates what's left for the day. Undo is one tap.
- **Home-screen widget:** today's calories and protein, and the next box with an **Ate it** button.
- **Backup:** export everything to a file and import it on a new phone.

### 3.10 Cycle tracking (optional)

- Log period days, flow and symptoms. The app predicts the next period and the phases as a **range of days**, and handles irregular cycles, hormonal contraception, pregnancy, breastfeeding and menopause (it pauses predictions when phases don't apply).
- **Food suggestions by phase, honest and based on research:**
  - Period: iron-rich food with vitamin C.
  - Luteal phase (before the period): filling snacks with protein and fibre, magnesium-rich food.
  - **No calorie target changes.** Research finds only a small rise in calorie use in the luteal phase (about 40–45 kcal per day).
- The planner gives a small bonus to recipes that fit the phase.
- The weight chart marks likely **water-weight** days, and smart maintenance ignores them.
- **Private:** stays on the phone, never shared with the household, optional app lock, one button deletes it. It goes into research reports only with its own separate consent.
- Not for birth control or for getting pregnant, and not medical advice.

### 3.11 Supplements (optional)

- A list of your supplements with dose, times and days.
- A daily checklist on Today: **Taken** / **Skip**.
- **Reminders only if you want them**, per supplement, with Taken / Skip right in the notification.
- Supplements with calories (protein powder) count in the diary.

### 3.12 Household over home Wi-Fi

- People who live together share the **shopping list, pantry, boxes, recipes and plans**.
- **No server, so it stays free.** One phone, the family "boss's", is the **hub**: it keeps the shared data, and the other phones sync with it over the home Wi-Fi. It does very little work, so it uses little battery. The app explains this to the host before hub mode starts.
- Changes made away from home sync when you are back home. Something urgent can be sent as a message (WhatsApp …).
- **Labels:** "Mine, don't touch", "Shared", "Up for grabs" (eat it before it spoils). Food reserved for a planned meal is marked.
- **Requests:** cook for me, buy for me, can I have it? With notifications.
- **Activity feed:** who bought, cooked or took what.
- **Cook once for everyone,** using each person's targets if they allow it.
- **Private, always:** diary, weight, body, cycle and supplements are never shared.

### 3.13 Help research (optional)

The owner wants a central database to study how well people eat. In the EU this is allowed with **clear opt-in consent**, especially for health and cycle data, so:

- it is **off by default**, for adults (18+) only;
- people choose what to share: food and nutrition, body, cycle (separately), supplements;
- every Monday the app sends a **summary of the week**, never food names, names or exact dates;
- people can **see exactly what is sent**, **stop** at any time, and **delete** their data from the server;
- the server is in the EU and stores no internet addresses; results are published only for groups of 10 or more people; the data is never sold or used for ads.

The full, detailed policy is in [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md). The research server needs hosting (a small cost), so the feature is built but stays switched off until it has hosting and a legal check.

## 4. Extra features and ideas for later

**Accepted, part of this implementation:**

| Feature | Where |
|---|---|
| Smart maintenance and the weekly check-in ("smart calorie tracking"), with the weight trend line | 3.2, M1 |
| Cook mode with one timeline and timers | 3.4, M4 |
| Barcode scanner | 3.9, M2 |
| Waste and money statistics | 3.5, 3.8, M5 |
| "Use it up" | 3.7, M6 |
| Cook once for the family | 3.4, M4 and M11 |
| Home-screen widget | 3.9, M9 |
| Export and import (backup) | 3.9, M0 |
| Hide-numbers mode | 3.2, M1 |
| Supplements with optional reminders | 3.11, M8 |

**Ideas for later, not in this implementation:** other countries and languages, iPhone, Health Connect, water tracking, a micronutrient dashboard, protein-per-meal check, QR stickers on boxes, seasonal food, eating-out mode, training-day targets, other body measurements and progress photos, a picture guide for body fat, guests, recipe import from websites, cooking turns, splitting costs between flatmates, Open Prices, finding shops on a map, tracking how many supplement pills are left.

**Not planned:** online ordering from supermarkets (no open way to do it), reading receipt photos, AI chat in the cloud.

## 5. Roadmap

Detailed tasks are in [`implementation/`](implementation/README.md).

| Milestone | What users get |
|---|---|
| M0 Groundwork | Czech language, backup to a file |
| M1 Profile, body and smart calorie tracking | Setup, body fat with tutorial, goals by speed, trend line, weekly check-in, hide numbers, the Me tab |
| M2 Foods, barcode scanner and manual logging | Basic foods with Czech names, Czech-first search, scanner, quick add |
| M3 Recipes and dishes | Recipe book, 30 starter recipes, log a dish by hand |
| M4 Cooking, prep boxes and freshness | Cook mode, weigh the pot, box sizes, fridge and freezer with colours, Ate it, reminders, people I cook for |
| M5 Weekly plan, pantry and shopping | Cooking frequency, week plan, pantry, shopping list with exact products, prices, stats, share as text |
| M6 Smart planner | Plan my week, lock / swap / regenerate, use it up |
| M7 Cycle tracking | Period and symptoms, predictions, phase tips, water-weight marks, app lock |
| M8 Supplements | Checklist and optional reminders |
| M9 Home-screen widget | Today on the home screen |
| M10 Research data program | Opt-in weekly summaries, the research server (switched on once it has hosting) |
| M11 Household over home Wi-Fi | Hub phone, shared list and fridge, labels, requests, activity feed |

M7, M8 and M9 can be built at any time after their dependencies, in parallel with the main line.

## 6. Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Platforms | **Android only** for this implementation. |
| 2 | Household sync | **Home Wi-Fi, with one family phone as the hub.** No paid server. Keep the hub's work light and tell users why it works this way. |
| 3 | Who is it for | **For now, an APK on GitHub** for family and friends. A stable version first; then decide on the licence and the business model, and maybe Google Play or F-Droid. |
| 4 | Country, shops, language | **Czech Republic first** (Czech and English, Kč, Czech shops). The rest of the world later; the code keeps country data separate so it can be added. |
| 5 | Order | **The smart planner comes before the household**: it is about five times more important. |
| 6 | Food-safety rules | **EU / Czech advice:** cooked food 3 days in the fridge by default, into the fridge within 2 hours, eat within 24 hours after thawing. |
| 7 | Starter recipes | **A random mix** of Czech home cooking and meal-prep classics (30 recipes). |
| 8 | Research database | **Yes, opt-in**, with a full privacy policy, a separate consent per kind of data, and deletion on request. Built in M10, switched on after hosting and a legal check. |
| 9 | Extra ideas | Only the extras the owner accepted (section 4). The rest waits. |

**Still open:**

- The owner's research on Czech shop products, needed for exact products (M5).
- Permission from ÚZEI to use the Czech food composition database (optional).
- Hosting for the research server, and the controller's details for the privacy policy (M10).
- A legal review and Czech translation of the privacy policy (M10).
- The licence and business model, after a stable version.

## 7. Risks

| Risk | What we do about it |
|---|---|
| The scope is very large | Strict milestones; each one is useful on its own; the household comes last |
| Food data quality (raw vs cooked, missing Czech foods) | Weigh the pot; curated basic foods with Czech names; users can add foods |
| Exact product data goes out of date | Every product and price has a date; users can re-pick a product in one tap |
| Food safety: the app says "fine" but food has gone bad | Careful defaults and the line "a guide, not a guarantee: when in doubt, throw it out" |
| Health claims (cycle, body fat) | Neutral wording, "not medical advice", not for birth control |
| Research data and the law (GDPR) | Opt-in per category, minimal summaries, EU hosting, deletion, a full policy, a legal check before launch |
| The hub phone is off or away | Every phone works offline and syncs later; urgent things can go by message |
| Android limits on background work (Android 15–17) | A test (spike) before building the household; low-power design |
| Unhealthy use (eating too little, obsessing over numbers) | Calorie minimums, warnings for fast loss, hide-numbers mode, neutral wording |

## 8. Other documents

- [`implementation/`](implementation/README.md): the build plan: rules, order, data model, calculations, and one file per milestone.
- [`DATA_SOURCES.md`](DATA_SOURCES.md): where product, food, price, recipe and health information comes from, and what goes over the network.
- [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md): the draft privacy policy, including the research program.
