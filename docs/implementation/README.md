# Implementation plan

This folder is the build plan for the features described in [`../PLAN.md`](../PLAN.md).
It is written for whoever builds them: a developer or an AI coding agent.

> **Status:** ready to start with M0. Nothing in this plan is built yet.
> **Date:** 6 November 2026

## Files

| File | What it contains |
|---|---|
| `README.md` | This page: scope, rules, order of work, inputs we still need |
| [`data-model.md`](data-model.md) | Every new or changed database table, by milestone |
| [`algorithms.md`](algorithms.md) | Every formula and calculation, with test cases |
| `M00-…` to `M11-…` | One file per milestone: tasks, acceptance criteria, tests |
| [`../DATA_SOURCES.md`](../DATA_SOURCES.md) | Where every piece of data comes from, and what goes over the network |
| [`../PRIVACY_POLICY.md`](../PRIVACY_POLICY.md) | Draft privacy policy; the research feature (M10) must match it |

**Before you start a milestone, read:** this README, the milestone file, the sections of `data-model.md` and `algorithms.md` that it links to, and [`CONTRIBUTING.md`](../../CONTRIBUTING.md).

## How the app is built today

Read these files first; the new code follows the same patterns.

| Area | Where | Notes |
|---|---|---|
| Domain models and logic | `app/src/main/java/com/calore/tracker/domain/` | Pure Kotlin. `Nutrients`, `CalorieCalculator`, `DiaryTotals`, input parsing (`NumberInput.kt` accepts `1,5` and `1.5`). |
| Database | `data/db/` | Room, version 1: `foods`, `diary_entries`, `weight_entries`. Diary entries copy nutrients ("history never changes"). |
| Settings | `data/prefs/SettingsStore.kt` | DataStore: goals and calculator inputs. Moves to the `members` table in M1. |
| Network | `data/remote/` | Open Food Facts search with `HttpURLConnection`, no networking library. |
| Repositories | `data/repository/` | Thin layer over DAOs, settings and the network client. |
| Dependency wiring | `AppContainer.kt` | Manual DI, one instance per process. |
| UI | `ui/` | Jetpack Compose, Material 3, one package per screen, string routes in `ui/navigation/Routes.kt`. |
| Tests | `app/src/test` (JVM), `app/src/androidTest` (Room DAO, Compose UI) | |

Build setup: AGP 9, Kotlin 2.4, KSP, Compose BOM, minSdk 26, targetSdk 36, compileSdk 37. Versions live in `gradle/libs.versions.toml`.

## Scope of this implementation

- **Android only.** Same app, same stack.
- **Czech Republic first.** Czech and English UI, Czech crowns (Kč), metric units, Czech shops, generic foods with Czech names. Keep country data in country folders (for example `assets/seed/cz/`) so other countries can be added later.
- **Distribution:** an APK on GitHub for family and friends. No Google Play or F-Droid release yet. The licence and the business model are decided after a stable version.
- **The smart planner matters most.** The meal-prep line (recipes → boxes → plan and shopping → smart planner) comes first. Household sharing comes last.
- **No paid servers for the app.** Household sharing works over home Wi-Fi with one phone as the hub. The only server is the optional research server (M10). It is built, but stays switched off until it has hosting.

**Not in this implementation** (ideas for later, see `PLAN.md` section 4): other countries, iPhone, store release, Health Connect, water tracking, a micronutrient dashboard, protein-per-meal check, QR stickers, seasonal food, eating-out mode, training-day targets, body measurements other than those for body fat, progress photos, guests, recipe import from websites, cooking turns, splitting costs, Open Prices, finding shops on OpenStreetMap.

## Ground rules

1. **Nothing that works today may break.** Diary, manual logging, Open Food Facts search, custom foods, goals, the calculator and the weight history must keep working. Keep the existing tests green and extend them.
2. **Pure logic goes in `domain/`**, with no Android imports, and unit tests in `app/src/test`. Every calculation in `algorithms.md` must pass the test cases listed there.
3. **Strings:** every user-visible text goes in `res/values/strings.xml` **and** `res/values-cs/strings.xml`. Use `<plurals>` for counts; Czech needs `one`, `few`, `many` and `other`.
4. **Database:** follow the conventions in `data-model.md`. Every PR that changes the schema bumps the version, adds a migration and a migration test, and commits the schema JSON under `app/schemas/`.
5. **Dependencies:** only the ones named in the milestone files. If you need another one, say why in the PR. Never add analytics, ads, Firebase, ML Kit or other closed-source SDKs.
6. **Network:** only the connections listed in `DATA_SOURCES.md`. Adding one means updating that file and `PRIVACY_POLICY.md` in the same PR.
7. **Private data** (diary, body, weight, cycle, supplements) never leaves the phone, except (a) research reports the user explicitly agreed to (M10), and (b) export files the user makes. Household sync never includes private data.
8. **Low battery and CPU use:** no polling loops. Background work uses WorkManager or alarms with flexible windows. The planner runs only when the user asks.
9. **Unfinished features stay hidden** in release builds behind a flag in `FeatureFlags.kt` (M0.7).
10. **If a task needs an input that is still missing** (see below), build everything that doesn't depend on it, and ask.
11. **Keep the docs true.** When behaviour changes, update `PLAN.md`, `DATA_SOURCES.md` or `PRIVACY_POLICY.md` as needed, and tick the task in its milestone file.

## Definition of done (every task)

- [ ] Code, plus unit tests for new domain logic. DAO, migration and UI tests where the milestone file asks for them.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` passes. If an emulator is available, `./gradlew connectedDebugAndroidTest` too.
- [ ] New strings exist in English and Czech.
- [ ] Schema changes: version bump, migration, migration test, schema JSON committed.
- [ ] Export and import (M0.5) include any new table.
- [ ] Screens that show calories or macros respect hide-numbers mode (M1.9).
- [ ] The task is ticked in its milestone file.

**Pull requests:** one task, or a few small related tasks, per pull request. Name branches `m4/box-solver` and so on. Describe what changed for the user, and how it was tested.

## Order of work

```
M0 Groundwork
├── M1 Profile, body and smart calorie tracking ──┬── M7 Cycle tracking
│                                                  └── M8 Supplements
└── M2 Foods, barcode scanner and manual logging
    └── M3 Recipes and dishes
        └── M4 Cooking, prep boxes and freshness   (also needs M1)
            └── M5 Weekly plan, pantry and shopping
                ├── M9 Home-screen widget
                └── M6 Smart planner
                    ├── M10 Research data program  (also needs M7, M8 and owner inputs)
                    └── M11 Household over home Wi-Fi
```

| Milestone | File | Depends on | Size |
|---|---|---|---|
| M0 Groundwork | [M00-groundwork.md](M00-groundwork.md) | none | M |
| M1 Profile, body and smart calorie tracking | [M01-profile-body-smart-calories.md](M01-profile-body-smart-calories.md) | M0 | L |
| M2 Foods, barcode scanner and manual logging | [M02-foods-scanner-manual-logging.md](M02-foods-scanner-manual-logging.md) | M0 | L |
| M3 Recipes and dishes | [M03-recipes-and-dishes.md](M03-recipes-and-dishes.md) | M2 | M |
| M4 Cooking, prep boxes and freshness | [M04-cooking-boxes-freshness.md](M04-cooking-boxes-freshness.md) | M1, M3 | L |
| M5 Weekly plan, pantry and shopping | [M05-plan-pantry-shopping.md](M05-plan-pantry-shopping.md) | M4 | L |
| M6 Smart planner | [M06-smart-planner.md](M06-smart-planner.md) | M5 | L |
| M7 Cycle tracking | [M07-cycle-tracking.md](M07-cycle-tracking.md) | M1 (planner tips need M6) | M |
| M8 Supplements | [M08-supplements.md](M08-supplements.md) | M1 | S |
| M9 Home-screen widget | [M09-widget.md](M09-widget.md) | M5 | S |
| M10 Research data program | [M10-research-data.md](M10-research-data.md) | M1, M2, M7, M8 and owner inputs | L |
| M11 Household over home Wi-Fi | [M11-household-wifi.md](M11-household-wifi.md) | M5, M6 | L |

Sizes are relative: S small, M medium, L large.

M7, M8 and M9 are off the main line. They can be built at any time after their dependencies, also in parallel with M4–M6.

## Navigation as it grows

The bottom bar ends up with five tabs: **Today · Plan · Kitchen · Shop · Me**.

| After | Bottom bar | What moves |
|---|---|---|
| Today (v0.1) | Diary · Weight · Goals | |
| M1 | Today · Me | Diary becomes **Today**. Weight and Goals move into **Me**, together with Body, Settings and About. |
| M3 | Today · Plan · Me | **Plan** shows recipes (the week plan joins in M5). |
| M4 | Today · Plan · Kitchen · Me | **Kitchen** shows boxes in the fridge and freezer (the pantry joins in M5). |
| M5 | Today · Plan · Kitchen · Shop · Me | **Shop** shows the shopping list. |

Cycle, Supplements, Research and Household live inside **Me** and appear only when they are switched on.

## Inputs still needed from the project owner

| # | Input | Needed for | What waits for it |
|---|---|---|---|
| 1 | Your research on Czech shop products: which products, at which shop, pack sizes, prices if you have them | M5.6, exact products | Only the seed file. The rest of M5, including picking products by hand, can be built. |
| 2 | Permission from ÚZEI to use the Czech food composition database (NutriDatabaze.cz) | M2.2, optional | Nothing. Use USDA data until there is an answer. |
| 3 | Hosting for the research server, and your name and contact as the data controller | M10.7 | Switching the research feature on (the server itself can be built and tested without it) |
| 4 | A legal review of `PRIVACY_POLICY.md`, and its Czech translation | M10 | Switching the research feature on |

## Status

Update this table when a milestone starts or ends.

| Milestone | Status |
|---|---|
| M0 Groundwork | In progress |
| M1 Profile, body and smart calorie tracking | Not started |
| M2 Foods, barcode scanner and manual logging | Not started |
| M3 Recipes and dishes | Not started |
| M4 Cooking, prep boxes and freshness | Not started |
| M5 Weekly plan, pantry and shopping | Not started |
| M6 Smart planner | Not started |
| M7 Cycle tracking | Not started |
| M8 Supplements | Not started |
| M9 Home-screen widget | Not started |
| M10 Research data program | Not started |
| M11 Household over home Wi-Fi | Not started |
