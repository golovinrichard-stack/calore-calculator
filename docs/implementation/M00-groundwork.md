# M0: Groundwork

**Goal:** prepare the code so later milestones can safely add tables, Czech text and new screens.
**Users get:** the app in Czech, and a backup to a file.
**Depends on:** nothing.
**Read first:** [data-model.md](data-model.md) (Conventions, M0).
**New dependencies:** `androidx.room:room-testing` (tests only), the Kotlin serialization compiler plugin (`org.jetbrains.kotlin.plugin.serialization`, same version as Kotlin; the JSON library is already a dependency).

## Tasks

### M0.1 Migration tests

- [x] Add `androidx.room:room-testing` as `androidTestImplementation` and make the exported schemas available to instrumented tests (`sourceSets["androidTest"].assets.srcDirs(files("$projectDir/schemas"))`).
- [x] Create `app/src/androidTest/.../data/db/MigrationTest.kt` with `MigrationTestHelper`.

**Done when:** a test can create a version-1 database, insert rows, migrate it and read the rows back.

### M0.2 Sync-ready ids on existing tables (database version 2)

- [x] Migration 1 → 2 as in data-model M0. Add each column with a default, fill it, then add the unique index:

```sql
ALTER TABLE foods ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE foods SET uuid = lower(
  hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' ||
  substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)));
CREATE UNIQUE INDEX index_foods_uuid ON foods(uuid);
-- the same for diary_entries and weight_entries
ALTER TABLE foods ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
UPDATE foods SET updatedAt = lastUsedAt;
```

- [x] Declare the same defaults in the entities (`@ColumnInfo(defaultValue = "")` and `"0"`), otherwise Room's schema check fails.
- [x] Entities, mappers and domain models carry the uuid; new rows get one from `domain/Ids.kt`.

**Done when:** the migration test passes, the existing `DaoTest` passes, and upgrading an installed v0.1 app keeps every food, diary entry and weight (check by hand once).

### M0.3 Shared code conventions

- [x] `domain/Ids.kt`: `newId(): String`.
- [x] `data/db/Converters.kt`: JSON list and map converters (kotlinx.serialization) for enums and strings.
- [x] `domain/Money.kt`: `Money(halers: Long, currency: String = "CZK")` with `plus`, `times(Double)` (rounded half up) and formatting with the locale (`139,90 Kč` in Czech).
- [x] A small helper for soft-delete queries (`deletedAt IS NULL`) and `updatedAt` stamping via the injected `Clock`. Built as `data/db/Stamps.kt`: `SoftDelete.NOT_DELETED` for queries, and `Stamper` with the `Stamped` interface for `createdAt`, `updatedAt` and `deletedAt`.

**Done when:** unit tests cover the converters (round trip, unknown enum value ignored), `Money` arithmetic and formatting in `cs-CZ` and `en`.

### M0.4 Czech translation

- [x] Add `res/values-cs/strings.xml` with every existing string (158 today) translated. Keep placeholders such as `%1$s` unchanged.
- [x] Add `res/xml/locales_config.xml` (`en`, `cs`) and `android:localeConfig` in the manifest, so Android 13+ offers a per-app language.
- [x] Use `<plurals>` wherever a number is followed by a noun. Czech needs `one`, `few`, `many` and `other`. (None of the 158 existing strings puts a counted number before a noun; the first plurals are the backup counts in M0.5.)
- [x] Dates use `DateTimeFormatter.ofLocalizedDate`; numbers already follow the locale (`Format.kt`), and input already accepts a decimal comma (`NumberInput.kt`).
- [x] Ask the owner to review the Czech text. (Asked in the M0 pull request.)

**Done when:** with the phone or app set to Czech, every screen is in Czech, and `lintDebug` reports no `MissingTranslation`.

### M0.5 Export and import (backup to a file)

- [x] Apply the serialization compiler plugin. Add `@Serializable` DTOs per table (not the Room entities themselves).
- [x] Format:

```json
{ "format": "calore-backup", "version": 1, "exportedAt": "2026-11-06T18:00:00Z",
  "appVersion": "0.2.0", "tables": { "foods": [ ... ], "diary_entries": [ ... ], "weight_entries": [ ... ] } }
```

- [x] **Export** with `ActivityResultContracts.CreateDocument("application/json")`, default name `calore-backup-yyyy-MM-dd.json`. Show the warning: "This file contains your health data. Keep it somewhere safe."
- [x] **Import** with `OpenDocument`: check `format` and `version`, show counts per table, confirm "This replaces all Calore data on this phone", then import in one transaction. If anything fails, nothing changes.
- [x] For now, put the entry under Goals → "Backup". It moves to Me → Settings in M1.
- [x] From now on, every new table is added to the backup (definition of done). When a table or column is added, raise the backup `version` (`BackupCodec.VERSION`): the app imports its own version and older ones (a table missing from an older file is empty) and refuses newer files with "update the app", so nothing is dropped silently.
- [x] Goals and calculator inputs are still in DataStore, so they are not in version 1 of the backup; the Backup screen says so. They join the backup when M1 moves them into the `members` table.

**Done when:** round-trip test (export, wipe, import, every row identical including uuids); a broken or foreign file is rejected with a clear message; an export with 10,000 diary entries works without freezing the UI.

### M0.6 App preferences

- [ ] New DataStore `app_prefs` for settings that are not about a person: `country` (`CZ`), `currency` (`CZK`), `firstRunDone`, and later `hideNumbers` (M1) and others.
- [ ] Leave `SettingsStore` (goals and calculator inputs) alone until M1 moves it.

### M0.7 Feature flags

- [ ] `FeatureFlags.kt`: one boolean per unfinished feature. Debug builds show everything; release builds show only finished features.

**Done when:** a feature behind a false flag doesn't appear in a release build.

### M0.8 Build and release on GitHub (recommended)

The app is shared as an APK on GitHub for now.

- [ ] GitHub Actions workflow: on every pull request run `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- [ ] On a version tag (`v*`), build `assembleRelease`, sign it with a keystore stored in GitHub Actions secrets, and attach the APK to a GitHub release. Document for the owner how to create the keystore and add the secrets. Never commit the keystore.

**Done when:** a test tag produces a signed APK that installs on a phone.

## Out of scope

New user features. M0 should change almost nothing visible except the language and the backup.
