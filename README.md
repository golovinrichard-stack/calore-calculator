# Calore

A calorie and macro tracker for Android with **no ads, no tracking and no account**.

Log what you eat and Calore adds up calories, protein, carbs, fat, fiber and sugar for the day. Search millions of products from [Open Food Facts](https://world.openfoodfacts.org), or type in your own foods from the label.

<p>
  <img src="docs/diary.png" alt="Diary screen in light theme" width="270">
  <img src="docs/diary-dark.png" alt="Diary screen in dark theme" width="270">
</p>

## Features

- **Daily diary** grouped into breakfast, lunch, dinner and snacks, with a running total against your goals. Move between days or jump to any date.
- **Food search** in Open Food Facts, plus your own custom foods. Foods you use are kept on the phone, so they're there next time even offline.
- **Portions in grams**, with one-tap serving sizes when the product has one. Tap a logged entry to change the amount or remove it (with undo).
- **Daily goals** for calories, protein, carbs and fat, with a short setup on the first start.
- **Calorie calculator**: Mifflin-St Jeor, or Katch-McArdle when you know your body fat; goals by speed (lose, maintain, gain or recomp) showing kg per week and kcal per day; automatic macros from protein per kg, or your own split.
- **Body**: weight history with a smoothed trend line, body fat from a tape measure (US Navy method, with a short how-to) or a number you know.
- **Weekly check-in**: after a few weeks of logging, Calore compares your weight trend with what you ate and suggests a new target in small steps.
- **Profile** under Me: body, goal, meal split, diet and allergies, cooking habits (used by the planner later).
- **Hide-numbers mode**: log food without seeing calories and macros.
- **Optional reminders** for the weekly check-in and for measuring body fat.
- **Backup to a file:** export your foods, diary and weights, and import them on a new phone (Me → Settings → Backup).
- **English and Czech.** The app follows the phone's language; on Android 13 and newer you can also pick the app's language in Android's settings.
- Material You design with light and dark themes.

## Privacy

Everything you log is stored only in the app's private storage on your phone. There is no account, no analytics and no ad SDK. The only network request the app makes is the Open Food Facts search, which sends nothing but the words you typed.

The app's data is included in Android's normal backup and phone-to-phone transfer, so it follows you to a new phone if you use those. A backup file you export yourself contains your health data, so keep it somewhere safe.

## Building

Requirements:

- JDK 17 or newer (Android Studio's bundled JDK works)
- Android SDK with platform 37 (Android 17)

Open the folder in Android Studio, or build from the command line:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

`./gradlew assembleRelease` produces a shrunk, unsigned release APK (about 2 MB) in `app/build/outputs/apk/release/`. Sign it with your own key before distributing.

The app runs on Android 8.0 (API 26) and newer.

Signed release APKs are built by GitHub Actions when a version tag is pushed; see [docs/RELEASING.md](docs/RELEASING.md).

On Windows, use `gradlew.bat` and make sure `JAVA_HOME` points at a JDK 17 install. If the SDK is not found, create `local.properties` with `sdk.dir=<path to your Android SDK>`.

### Tests

```bash
./gradlew testDebugUnitTest          # domain logic and Open Food Facts parsing (JVM)
./gradlew connectedDebugAndroidTest  # database and UI tests (needs a device or emulator)
./gradlew lintDebug
```

## Project layout

```
app/src/main/java/com/calore/tracker/
├── domain/      Pure Kotlin: nutrients, calorie calculator, validation. No Android imports.
├── data/
│   ├── db/      Room database (foods, diary entries, weights)
│   ├── prefs/   DataStore for goals and calculator inputs
│   ├── remote/  Open Food Facts client and parser
│   └── repository/
└── ui/          Jetpack Compose screens, one package per screen
```

Diary entries store a copy of the nutrients for the portion that was eaten. Editing or deleting a food later never changes past days.

## Contributing

Bug reports, translations and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Calore is free and open source software, released under the [BSD Zero Clause License (0BSD)](LICENSE). You may use, copy, modify, distribute and sell it for any purpose, with or without changes, without asking permission and without keeping any notice or attribution.

Food data © Open Food Facts contributors, available under the [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).
