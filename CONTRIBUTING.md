# Contributing to Calore

Thanks for helping. Calore aims to stay small, private and pleasant to use.

## Ground rules

- **No ads, analytics, trackers or proprietary SDKs.** Pull requests that add them will not be merged.
- **Data stays on the device.** New network calls need a clear user-facing reason and must send only what that feature needs.
- Keep dependencies few. Prefer the Android platform and AndroidX over new libraries.

## Before opening a pull request

Run the checks and make sure they pass:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

If you changed database or UI code and have an emulator running, also run:

```bash
./gradlew connectedDebugAndroidTest
```

- Put pure logic in `domain/` and cover it with unit tests in `app/src/test`.
- User-visible text goes in `res/values/strings.xml`, never hard-coded, so it can be translated.
- Changing a Room entity means bumping the database version and adding a migration. Commit the updated schema JSON under `app/schemas/`.

## Translations

Copy `app/src/main/res/values/strings.xml` to `values-<language code>/strings.xml` (for example `values-de`) and translate the text between the tags. Keep placeholders such as `%1$s` exactly as they are.

## Reporting bugs

Include your Android version, phone model, what you did, what you expected and what happened. If a food from Open Food Facts shows wrong values, please fix it on [openfoodfacts.org](https://world.openfoodfacts.org) so everyone benefits.
