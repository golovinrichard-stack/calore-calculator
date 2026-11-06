# Data model

Every new and changed table, in the order the milestones add them. Names are proposals; keep them unless there is a good reason to change them, and then update this file.

The database is Room, today at **version 1** (`foods`, `diary_entries`, `weight_entries`). Each milestone that changes the schema adds **one** new version with a migration. The version numbers below assume the milestones land in order; if they don't, just use the next free number.

## Conventions

- **Ids.** New tables use `id TEXT PRIMARY KEY` holding a random UUID (lowercase, 36 characters). Generate ids in one helper, `domain/Ids.kt` (`UUID.randomUUID().toString()`), never in SQL, except for migration backfills. Auto-increment numbers would clash between phones once the household syncs (M11).
- **References** between tables use these text ids. The existing `foods` table keeps its number `id` for the current code, but new tables reference foods by `foods.uuid` (column name `foodUuid`).
- **Timestamps:** `createdAt` and `updatedAt` hold epoch milliseconds (`INTEGER NOT NULL`). `deletedAt INTEGER NULL` marks a soft delete; normal queries filter on `deletedAt IS NULL`. Rows soft-deleted more than 30 days ago may be purged, except in tables that sync (M11), where they are purged only after every device has seen the delete.
- **Dates without a time** are ISO text `yyyy-MM-dd`, as `diary_entries.date` is today. ISO weeks are `yyyy-Www`.
- **Enums** are stored as their `name` text, as today.
- **Money** is an `INTEGER` count of hellers (haléře, 1/100 Kč) plus a currency code (`CZK`). Never a floating-point amount.
- **Amounts** are `REAL` with a unit: `G` (grams), `ML` or `PIECE`.
- **Small lists** (allergens, tags, days of week) are JSON text, stored through a Room `TypeConverter` that uses kotlinx.serialization.
- **Nutrients.** Where a table "has nutrients", it has these columns: `kcal REAL NOT NULL`, `protein REAL`, `carbs REAL`, `fat REAL`, `fiber REAL`, `sugar REAL`, and from M2 also `ironMg REAL`, `magnesiumMg REAL` and `vitaminCMg REAL`. `NULL` means unknown, which is different from zero (as today).
- **Snapshots.** Anything that records what was eaten or cooked copies nutrient values at that moment, like `diary_entries` does today. History is never recalculated.
- **Backup.** Every table is part of the backup file (M0.5). Adding a table or column means adding it to the backup DTOs and raising the backup `version`.
- **Shared and private tables.** Tables marked **shared** will sync to the household in M11; they have `ownerMemberId TEXT NULL` (null means the whole household) and `visibility TEXT` (`MINE`, `SHARED`, `UP_FOR_GRABS`). Until M11 these just keep their defaults. Tables marked **private** never sync.

## Enums

| Enum | Values |
|---|---|
| `GoalType` | `CUT`, `MAINTAIN`, `BUILD`, `RECOMP` |
| `MacroMode` | `AUTO` (protein per kg, fat %, carbs fill the rest), `SPLIT` (manual % split, as today) |
| `EntryType` | `FOOD`, `QUICK_ADD`, `DISH`, `BOX`, `SUPPLEMENT` |
| `ShopSection` | `VEGETABLES`, `FRUIT`, `MEAT`, `FISH`, `DAIRY`, `EGGS`, `BAKERY`, `GRAINS`, `LEGUMES`, `CANNED`, `FROZEN`, `SPICES_SAUCES`, `OILS`, `SNACKS`, `DRINKS`, `SUPPLEMENTS`, `OTHER` |
| `Storage` | `FRIDGE`, `FREEZER`, `CUPBOARD` |
| `BoxState` | `READY`, `EATEN`, `PART_EATEN`, `WASTED` |
| `SlotKind` | `BOX`, `COOK_FRESH`, `EAT_OUT`, `FREE` |
| `Visibility` | `MINE`, `SHARED`, `UP_FOR_GRABS` |
| `Diet` | `OMNIVORE`, `VEGETARIAN`, `VEGAN`, `PESCATARIAN`, `NO_PORK` |
| `Allergen` (the 14 EU allergens) | `GLUTEN`, `CRUSTACEANS`, `EGGS`, `FISH`, `PEANUTS`, `SOY`, `MILK`, `NUTS`, `CELERY`, `MUSTARD`, `SESAME`, `SULPHITES`, `LUPIN`, `MOLLUSCS` |
| `AnimalFlag` | `DAIRY`, `EGG`, `FISH`, `SHELLFISH`, `MEAT`, `PORK` |
| `Equipment` | `OVEN`, `AIR_FRYER`, `SLOW_COOKER`, `RICE_COOKER`, `MICROWAVE`, `BLENDER` |
| `MemberKind` | `ME` (the phone's user), `MANAGED` (a person without the app, e.g. a child or someone you cook for), `HOUSEHOLD` (another member with their own phone, M11) |
| `CyclePhase` | `PERIOD`, `FOLLICULAR`, `OVULATION`, `LUTEAL` |
| `Unit` | `G`, `ML`, `PIECE` |

## M0: existing tables become sync-ready (version 2)

| Table | Change |
|---|---|
| `foods` | Add `uuid TEXT NOT NULL` with a unique index. Add `updatedAt INTEGER NOT NULL` (backfill from `lastUsedAt`). |
| `diary_entries` | Add `uuid TEXT NOT NULL` with a unique index. |
| `weight_entries` | Add `uuid TEXT NOT NULL` with a unique index. |

Backfill the new `uuid` columns inside the migration (see M0.2 for the SQL).

## M1: people and body (version 3)

### `members`

ME is private. MANAGED rows sync in M11 only if their owner shares them.

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `kind` | TEXT | `MemberKind` |
| `name` | TEXT | Empty until the user sets a name; the app then shows "Me" ("Já") |
| `colour` | INTEGER | ARGB, for name labels on boxes |
| `sex` | TEXT NULL | `Sex` (existing enum) |
| `birthDate` | TEXT NULL | ISO date. Replaces the stored age. |
| `heightCm` | REAL NULL | |
| `weightKg` | REAL NULL | Only for `MANAGED` members. The phone's user keeps using `weight_entries`. |
| `activity` | TEXT NULL | `ActivityLevel` (existing enum) |
| `goalType` | TEXT NULL | `GoalType` |
| `goalRatePct` | REAL NULL | Body weight % per week, a positive number. The direction comes from `goalType`. |
| `macroMode` | TEXT | `MacroMode`, default `AUTO` |
| `proteinPerKg` | REAL NULL | Default depends on the goal (algorithms A4) |
| `fatPct` | INTEGER NULL | % of calories, default 25 |
| `splitProteinPct`, `splitCarbsPct`, `splitFatPct` | INTEGER NULL | The manual split from today's calculator |
| `targetKcal`, `targetProteinG`, `targetCarbsG`, `targetFatG` | INTEGER NULL | Current daily targets. Replace the DataStore goals. |
| `mealSplit` | TEXT | JSON map `MealType` → %, default `{"BREAKFAST":20,"LUNCH":30,"DINNER":30,"SNACK":20}` |
| `diet` | TEXT | `Diet`, default `OMNIVORE` |
| `allergens` | TEXT | JSON list of `Allergen` |
| `likedFoods`, `dislikedFoods` | TEXT | JSON lists of food keys (`generic:…`) or `ShopSection` names |
| `cuisines` | TEXT | JSON list, e.g. `["czech","italian"]` |
| `equipment` | TEXT | JSON list of `Equipment`. Empty means "not set": don't filter. |
| `variety` | INTEGER | 1 = same meals all week is fine, 2 = some variety, 3 = different every day. Default 2. |
| `weeklyBudgetHalers` | INTEGER NULL | |
| `cookSessionsPerWeek` | INTEGER | 1–7, default 2 |
| `cookDays` | TEXT | JSON list of `DayOfWeek`; empty means the app chooses |
| `minutesPerSession` | INTEGER | Default 120 |
| `preppedMeals` | TEXT | JSON list of `MealType` that come from prep boxes. Default `["LUNCH","DINNER"]`. |
| `sharesTargets` | INTEGER | 0/1. Used in M11: may other phones see this person's meal targets? |
| `createdAt`, `updatedAt`, `deletedAt` | | |

The values in the current `SettingsStore` (goals and calculator inputs) are copied into the ME row once, in M1.1. DataStore stays for app settings that are not about a person.

### `diary_entries` (changed)

| Change | Notes |
|---|---|
| Add `memberId TEXT NULL` | `NULL` = ME. A parent can log for a MANAGED member later. |

### `body_measurements` (private)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `memberId` | TEXT | |
| `date` | TEXT | |
| `neckCm`, `waistCm`, `hipCm` | REAL NULL | `hipCm` only for women |
| `bodyFatPct` | REAL NULL | Calculated (NAVY) or typed in (MANUAL) |
| `method` | TEXT | `NAVY` or `MANUAL` |
| `createdAt`, `updatedAt`, `deletedAt` | | |

### `checkins` (private)

One row per weekly check-in (algorithms A7).

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `memberId` | TEXT | |
| `weekStart` | TEXT | Monday |
| `trendStartKg`, `trendEndKg` | REAL NULL | |
| `avgIntakeKcal` | REAL NULL | |
| `countedDays` | INTEGER | |
| `estimatedMaintenanceKcal` | INTEGER NULL | |
| `oldTargetKcal`, `proposedTargetKcal` | INTEGER | |
| `accepted` | INTEGER | 0/1 |
| `createdAt` | INTEGER | |

## M2: foods (version 4)

### `foods` (changed, shared)

| Change | Type | Notes |
|---|---|---|
| `key` | TEXT NULL, unique | Stable key for bundled generic foods, e.g. `generic:chicken_breast_raw` |
| `nameCs`, `nameEn` | TEXT NULL | Names of generic foods per language. The UI shows the one for the current locale and falls back to `name`. |
| `section` | TEXT | `ShopSection`, default `OTHER` |
| `isGeneric` | INTEGER | 0/1 |
| `sourceRef` | TEXT NULL | `usda:<fdcId>`, `off:<barcode>`, `label:<note>`, `user` |
| `allergens` | TEXT | JSON list of `Allergen` |
| `animalFlags` | TEXT | JSON list of `AnimalFlag` |
| `defaultPackGrams` | REAL NULL | A typical pack size, used until an exact product is chosen |
| `storage` | TEXT NULL | `Storage` once bought |
| `shelfLifeDays` | INTEGER NULL | Unopened, when the pack has no date |
| `gramsPerPiece` | REAL NULL | For foods counted in pieces (eggs, rolls) |
| `ironMgPer100g`, `magnesiumMgPer100g`, `vitaminCMgPer100g` | REAL NULL | |
| `datasetVersion` | INTEGER NULL | Version of the bundled dataset this row came from |

### `diary_entries` (changed)

| Change | Notes |
|---|---|
| Add `entryType TEXT NOT NULL DEFAULT 'FOOD'` | `EntryType`. A quick add is `QUICK_ADD` with `grams = 0` and `foodId = NULL`. |
| Add `ironMg`, `magnesiumMg`, `vitaminCMg` REAL NULL | Snapshot, like the other nutrients |

## M3: recipes (version 5)

### `recipes` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `key` | TEXT NULL, unique | Starter recipes: `starter:cz:<slug>` |
| `name` | TEXT | The user's own recipes |
| `nameCs`, `nameEn` | TEXT NULL | Starter recipes: names per language. The UI shows the one for the current locale and falls back to `name`. |
| `userEdited` | INTEGER | 0/1. A starter recipe the user changed is never overwritten by a newer starter pack. |
| `shortCode` | TEXT | 1–2 letters for box codes (`C` → `C1`, `C2` …) |
| `servings` | INTEGER | |
| `mealTypes` | TEXT | JSON list of `MealType` |
| `cuisine` | TEXT NULL | |
| `tags` | TEXT | JSON list |
| `equipment` | TEXT | JSON list of `Equipment` |
| `fridgeDays` | INTEGER | Default 3 |
| `freezable` | INTEGER | 0/1 |
| `freezerMonths` | INTEGER | Default 3 |
| `reheatNote` | TEXT NULL | |
| `mealPrepFriendly` | INTEGER | 0/1 |
| `learnedYield` | REAL NULL | Cooked g ÷ raw g, learned from weighing (algorithms A9) |
| `learnedYieldCount` | INTEGER | Default 0 |
| `ownerMemberId`, `visibility` | | |
| `createdAt`, `updatedAt`, `deletedAt` | | |

### `recipe_ingredients` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `recipeId` | TEXT | |
| `position` | INTEGER | |
| `foodUuid` | TEXT | |
| `grams` | REAL | Raw weight |
| `note` | TEXT NULL | e.g. "diced" |
| `optional` | INTEGER | 0/1 |

### `recipe_steps` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `recipeId` | TEXT | |
| `position` | INTEGER | |
| `text` | TEXT | The user's own steps |
| `textCs`, `textEn` | TEXT NULL | Starter recipes: the step per language, shown like recipe names |
| `minutes` | INTEGER NULL | |
| `passive` | INTEGER | 1 = needs no attention (oven, simmering), so cook mode can run other steps meanwhile |
| `equipment` | TEXT NULL | `Equipment`, or `STOVE` |

## M4: cooking and boxes (version 6)

### `pots` (shared)

`id` TEXT PK, `name` TEXT, `emptyGrams` REAL, timestamps.

### `batches` (shared)

One cooked pot or tray.

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `recipeId` | TEXT NULL | `NULL` for a single food cooked on its own, e.g. plain rice |
| `name` | TEXT | Recipe name, or e.g. "Rice" |
| `cookedAt` | INTEGER | Epoch ms |
| `cookedByMemberId` | TEXT NULL | |
| `rawGrams` | REAL | Sum of ingredients |
| `cookedGrams` | REAL | Net weight after weighing |
| nutrients per 100 g cooked | | Snapshot |
| `costHalers` | INTEGER NULL | Filled in M5 when prices are known |
| `ownerMemberId`, `visibility`, timestamps | | |

### `batch_ingredients` (shared)

What actually went in (the scaled recipe, after any changes), so the pantry and costs can be updated: `id`, `batchId`, `foodUuid`, `grams`.

### `boxes` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `code` | TEXT | e.g. `C3`; unique among boxes that are not eaten or wasted |
| `ownerMemberId` | TEXT NULL | Who the box is for |
| `visibility` | TEXT | Default `MINE` once it has an owner |
| `storage` | TEXT | `FRIDGE` or `FREEZER` |
| `state` | TEXT | `BoxState` |
| `remainingFraction` | REAL | 1.0 when full; after "ate part" the rest |
| `eatByDate` | TEXT | algorithms A12 |
| `frozenAt`, `thawedAt` | INTEGER NULL | |
| `plannedDate`, `plannedMeal` | TEXT NULL | Until M5 links boxes to plan slots |
| `planSlotId` | TEXT NULL | M5 |
| `wastedReason` | TEXT NULL | `EXPIRED`, `SPOILED`, `DIDNT_LIKE`, `OTHER` |
| timestamps | | |

### `box_parts` (shared)

A box holds one or more batches: `id`, `boxId`, `batchId`, `grams`.

### `diary_entries` (changed)

| Change | Notes |
|---|---|
| Add `boxId TEXT NULL` | `entryType = BOX` |
| Add `recipeId TEXT NULL` | `entryType = DISH` |

## M5: plan, pantry and shopping (version 7)

### `plans` (shared)

`id` TEXT PK, `weekStart` TEXT NULL (Monday; `NULL` for a template), `templateName` TEXT NULL (set for saved templates), `generatorSeed` INTEGER NULL (M6), timestamps.

### `plan_slots` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `planId` | TEXT | |
| `date` | TEXT | |
| `meal` | TEXT | `MealType` |
| `memberId` | TEXT | |
| `kind` | TEXT | `SlotKind` |
| `recipeId` | TEXT NULL | |
| `boxId` | TEXT NULL | Set when a box is assigned |
| `targetKcal`, `targetProteinG`, `targetCarbsG`, `targetFatG` | INTEGER NULL | |
| `locked` | INTEGER | 0/1, M6 |
| `done` | INTEGER | 0/1, set when eaten |
| timestamps | | |

### `plan_cooks` (shared)

What to cook, when: `id`, `planId`, `date`, `recipeId`, `portions` INTEGER, `targetCookedGrams` REAL NULL, `scale` REAL, `batchId` TEXT NULL (set after cooking), timestamps.

### `pantry_items` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `foodUuid` | TEXT NULL | |
| `storeProductId` | TEXT NULL | |
| `name` | TEXT | Shown when there is no food link |
| `amount`, `unit` | REAL, TEXT | |
| `storage` | TEXT | |
| `boughtDate` | TEXT | |
| `bestBeforeDate` | TEXT NULL | The date printed on the pack wins over defaults |
| `openedDate` | TEXT NULL | |
| `state` | TEXT | `AVAILABLE`, `USED_UP`, `WASTED` |
| `costHalers` | INTEGER NULL | |
| `reservedForCookId` | TEXT NULL | `plan_cooks.id` |
| `ownerMemberId`, `visibility`, timestamps | | |

### `stores` (shared)

`id`, `key` TEXT NULL (seed key, e.g. `cz:lidl`), `name`, `sectionOrder` (JSON list of `ShopSection`), `favorite` 0/1, `defaultForSections` (JSON list of `ShopSection` this shop is the default for, e.g. meat at Lidl), timestamps.

### `store_products` (shared)

An exact product at a shop.

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `key` | TEXT NULL, unique | Seed key |
| `storeId` | TEXT | |
| `foodUuid` | TEXT NULL | The generic food this product is used for |
| `name` | TEXT | As printed, e.g. "Kuřecí prsní řízky" |
| `brand` | TEXT NULL | |
| `barcode` | TEXT NULL | EAN |
| `packAmount`, `packUnit` | REAL, TEXT | |
| `preferred` | INTEGER | Preferred product for this food at this shop |
| `source` | TEXT | `OWNER_RESEARCH`, `USER`, `OFF` |
| `sourceNote` | TEXT NULL | |
| timestamps | | |

### `prices` (shared)

`id`, `storeProductId`, `priceHalers` INTEGER, `currency` TEXT, `date` TEXT, `source` (`USER`, `SEED`), timestamps.

### `shopping_items` (shared)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `foodUuid` | TEXT NULL | |
| `storeProductId` | TEXT NULL | |
| `storeId` | TEXT NULL | |
| `name` | TEXT | Display name; the only field for manual items |
| `amountNeeded`, `unit` | REAL, TEXT | |
| `packs` | INTEGER NULL | |
| `section` | TEXT | `ShopSection` |
| `fromPlanId` | TEXT NULL | |
| `manual` | INTEGER | 0/1 |
| `checkedAt` | INTEGER NULL | |
| `checkedByMemberId` | TEXT NULL | |
| `requestedByMemberId` | TEXT NULL | M11 "buy for me" |
| `priceHalers` | INTEGER NULL | Typed in when ticking |
| timestamps | | |

Waste is not a separate table: boxes have `state = WASTED` and pantry items have `state = WASTED`. Statistics read both.

## M6: smart planner (version 8)

### `recipe_ratings` (shared)

`id`, `memberId`, `recipeId`, `rating` (−1 or 1), `updatedAt`. Unique index on (`memberId`, `recipeId`).

## M7: cycle (version 9), private, never synced

### `cycle_periods`

`id`, `startDate`, `endDate` NULL, timestamps.

### `cycle_days`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `date` | TEXT, unique | |
| `flow` | TEXT NULL | `NONE`, `LIGHT`, `MEDIUM`, `HEAVY` |
| `symptoms` | TEXT | JSON list: `CRAMPS`, `BLOATING`, `CRAVINGS`, `LOW_MOOD`, `LOW_ENERGY`, `POOR_SLEEP`, `HEADACHE` |
| `note` | TEXT NULL | Stays on the phone, never in research reports |
| timestamps | | |

Cycle settings live in DataStore (`cycle_prefs`): `enabled`, `contraception` (`NONE`, `PILL`, `HORMONAL_IUD`, `COPPER_IUD`, `IMPLANT`, `OTHER_HORMONAL`), `pregnant`, `breastfeeding`, `menopause` (`NO`, `PERIMENOPAUSE`, `MENOPAUSE`), `defaultCycleLength` (28), `defaultPeriodLength` (5), `appLock`.

## M8: supplements (version 10), private

### `supplements`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `name` | TEXT | |
| `kind` | TEXT | `VITAMIN_D`, `IRON`, `MAGNESIUM`, `CREATINE`, `PROTEIN_POWDER`, `OMEGA_3`, `MULTIVITAMIN`, `OTHER` |
| `doseAmount`, `doseUnit` | REAL, TEXT | `MG`, `UG`, `IU`, `G`, `ML`, `CAPSULE`, `TABLET`, `SCOOP` |
| `times` | TEXT | JSON list of `HH:mm` |
| `daysOfWeek` | TEXT | JSON list; empty = every day |
| `startDate`, `endDate` | TEXT, TEXT NULL | |
| `remindersOn` | INTEGER | 0/1 |
| nutrients per dose | | All nullable. If `kcal` is set, taking it also logs a diary entry. |
| `active` | INTEGER | |
| timestamps | | |

### `supplement_logs`

`id`, `supplementId`, `scheduledAt` INTEGER NULL, `takenAt` INTEGER NULL, `status` (`TAKEN`, `SKIPPED`), `diaryEntryUuid` TEXT NULL, timestamps.

## M10: research (version 11), private

### `research_reports`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | Also the `reportId` sent to the server |
| `isoWeek` | TEXT | |
| `categories` | TEXT | JSON list: `NUTRITION`, `BODY`, `CYCLE`, `SUPPLEMENTS` |
| `payload` | TEXT | The JSON exactly as it is (or was) sent |
| `status` | TEXT | `PENDING`, `SENT`, `FAILED` |
| `attempts` | INTEGER | |
| `createdAt`, `sentAt` | INTEGER, INTEGER NULL | |

Consent lives in DataStore (`research_prefs`): `participantId`, `participantSecret` (random, 32 bytes, Base64), `consentVersion`, `consentedAt`, `categories`, `region` (optional Czech region code), `lastReportWeek`.

## M11: household (version 12)

| Table | Columns |
|---|---|
| `household` | `id`, `name`, `createdAt` |
| `household_devices` | `id` (device id), `memberId`, `name`, `isHub`, `lastSeenAt`, timestamps |
| `requests` (shared) | `id`, `type` (`COOK`, `BUY`, `CAN_I_HAVE`, `OTHER`), `fromMemberId`, `toMemberId` NULL (= anyone), `refTable` NULL, `refId` NULL, `message` NULL, `dueDate` NULL, `status` (`OPEN`, `ACCEPTED`, `DECLINED`, `DONE`, `CANCELLED`), timestamps |
| `activity` (shared) | `id`, `memberId`, `type` (`BOUGHT`, `COOKED`, `TOOK`, `THREW_AWAY`, `REQUESTED`, `ANSWERED`), `refTable`, `refId`, `summary`, `at` |
| `sync_ops` | `opId` TEXT PK, `deviceId`, `seq` INTEGER, `hlc` TEXT, `tableName`, `rowId`, `payload` (changed columns as JSON), `receivedAt` |
| `sync_field_clock` | (`tableName`, `rowId`, `column`) PK, `hlc` |
| `sync_peers` | `deviceId` PK, `maxSeqSeen` INTEGER |

The household key is not stored in the database. It goes in Android Keystore-protected storage (M11.2).

**Tables that sync in M11:** every table marked **shared** above, plus `members` rows of kind `HOUSEHOLD` and the `MANAGED` rows their owner shares. **Never synced:** `diary_entries`, `weight_entries`, `body_measurements`, `checkins`, `cycle_*`, `supplements`, `supplement_logs`, `research_reports` and all DataStore files.
