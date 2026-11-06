# Build prompt

Paste everything below the line into a new Claude Code session (or give it to any developer) to build the next milestone. Use the same prompt again for every milestone: by default it picks the next unfinished one.

To build a specific milestone, replace `NEXT` in section 3 with its number, for example `M4`.

---

You are building new features for **Calore**, an Android calorie tracker (Kotlin, Jetpack Compose, Room). The complete plan is already written in the repository. Your job is to implement it, **one milestone per session**, exactly as written.

## 1. Get the project

- Repository: https://github.com/golovinrichard-stack/calore-calculator
- If the current folder is empty or is not this repository, clone it into the current folder.
- The plan is in `docs/`. If `docs/implementation/README.md` doesn't exist on `main`, the plan is still on the branch `claude/quirky-dijkstra-n0p4yy` (pull request #1). Then base your work on that branch, and mention it in your report.

## 2. Read before you write code

In this order:

1. `README.md` and `CONTRIBUTING.md`
2. `docs/PLAN.md`: what we are building and why
3. `docs/implementation/README.md`: scope, ground rules, definition of done, order of work, status table, inputs still missing from the owner
4. the milestone file you are building (`docs/implementation/Mxx-….md`)
5. the sections of `docs/implementation/data-model.md` and `docs/implementation/algorithms.md` that the milestone file links to
6. `docs/DATA_SOURCES.md` and `docs/PRIVACY_POLICY.md`, if the milestone touches the network or personal data

Then read the existing code that the milestone changes.

## 3. Which milestone

Build: **NEXT**

`NEXT` means the first milestone in the status table in `docs/implementation/README.md` that is not "Done" and whose dependencies are all "Done" (M7, M8 and M9 may be built as soon as their own dependencies are done). Build only this one milestone.

## 4. Set up, and check the build before changing anything

- You need JDK 17 or newer and the Android SDK with platform 37. Check `java -version` and `ANDROID_HOME` or `local.properties`. If the SDK is missing, install the command-line tools and use `sdkmanager` to install `platforms;android-37`, `build-tools` and `platform-tools`; accept the licences; write `local.properties`.
- Run `./gradlew testDebugUnitTest lintDebug assembleDebug` on the unchanged code. It must pass before you start. If it doesn't, stop and report why.
- If a download is blocked by the network, report which host is blocked. Never skip the checks to work around it.
- Without an emulator you can't run `./gradlew connectedDebugAndroidTest`. Still write the instrumented tests the milestone asks for, make sure they compile (`./gradlew assembleDebugAndroidTest`), and say in the report that they were not run.

## 5. How to work

- **Branch:** one branch per milestone, for example `m0-groundwork`. If your environment gives you a branch name, use that one. If the previous milestone's pull request isn't merged yet, start from its branch.
- **Order:** do the tasks in the order of the milestone file. For each task: implement it, test it, tick its checkbox (`- [x]`) in the milestone file, and commit with the task number in the message, for example `M0.2: Add uuid columns to existing tables`.
- **Ground rules:** follow every rule in `docs/implementation/README.md`. Most important:
  - Everything that works today keeps working: diary, manual logging, food search, custom foods, goals, calculator, weight.
  - Pure logic goes in `domain/` with unit tests. Every test case in `algorithms.md` must pass exactly as written.
  - Every user-visible string goes in English **and** Czech.
  - A schema change means a version bump, a migration, a migration test, and the schema JSON committed.
  - Only the dependencies named in the milestone file. Never analytics, ads, Firebase, ML Kit or any other closed-source SDK.
  - Only the network connections listed in `DATA_SOURCES.md`. Private data never leaves the phone, except as `PRIVACY_POLICY.md` describes.
  - Unfinished features stay hidden behind `FeatureFlags`.
- **Tests:** never skip, disable or weaken a test to make the build pass. Never change a test case from `algorithms.md` to match your code. If you think a value in the plan is wrong, stop and explain why.
- **Don't invent the owner's data:** shop products and prices (input #1), Czech food database values (input #2), server hosting or the controller's details (input #3), legal texts (input #4). Build everything that doesn't depend on them, and list what is waiting.
- **The research server (M10)** is built, but stays switched off: leave `calore.researchServerUrl` empty.
- **If the plan is unclear**, or doesn't match the code, choose the simplest option that fits `docs/PLAN.md`. Write it under "Decisions I made" in the pull request, and fix the document so it stays true. If the choice changes what users see or what data leaves the phone, stop and ask instead.

## 6. Finishing the milestone

- `./gradlew testDebugUnitTest lintDebug assembleDebug` passes.
- Update the status table in `docs/implementation/README.md`. Make sure the milestone's checkboxes are accurate. Update `docs/PLAN.md`, `docs/DATA_SOURCES.md` or `docs/PRIVACY_POLICY.md` if the behaviour changed.
- Push the branch and open a **draft pull request** into `main` (or into the previous milestone's branch if that isn't merged yet), titled like the milestone, for example "M0: Groundwork". The description says:
  - what users get;
  - the tasks done;
  - how it was tested: the commands and their results, and which instrumented tests were not run;
  - decisions made;
  - what is waiting for the owner;
  - anything not finished.
- **If you run out of time:** commit only what works and passes the checks, leave unfinished tasks unticked, set the status to "In progress", and list the next steps in the pull request.

## 7. Report to the owner

End with a short summary in **plain, simple English** (the owner is not a native speaker):

- what works now,
- how to try it (the debug APK is `app/build/outputs/apk/debug/app-debug.apk`),
- what needs the owner's input,
- which milestone comes next.
