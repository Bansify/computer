# Gothwad Computer

Gothwad Computer is a Kotlin Android application that presents a desktop-style environment with an app grid, taskbar, floating built-in windows, file manager, browser and web-app shortcuts. It runs on Android devices (including devices without a touchscreen); it is not a Windows emulator and does not run Windows executables.

> **Current state:** The manifest registers a Home candidate; the user must choose it in Android settings. Initial recovery support is not yet verified on real TVs or tablets: keep another launcher installed and do not rely on this as your only Home. Some settings and legacy TV-related configuration are incomplete. See the [analysis](docs/PROJECT_ANALYSIS.md) and [Phase 1 test protocol](docs/PHASE1_TESTING.md).

## What is in the app

- Desktop grid, start menu, taskbar, app search, notifications and settings.
- Launch installed Android apps; hide, pin and arrange desktop entries.
- In-app floating windows for File Manager, Web Browser and Gothwad Store.
- Browser-based shortcuts (`pwa://` entries), not separately installed Android packages.
- Optional launcher-level app/device locks, overlay taskbar, custom wallpaper and display settings. These are **not substitutes for Android system security**.

The UI uses **Android Views, XML, ViewBinding, Fragments and RecyclerView**. Do not add Compose: see [AGENTS.md](AGENTS.md).

## Project and tools

| Item | Current configuration |
| --- | --- |
| App ID / namespace | `com.gothwad.computer` |
| Minimum / target / compile SDK | 21 / 34 / 34 |
| Java / Kotlin | JDK 17 / Kotlin 2.0.20 |
| Android Gradle Plugin / wrapper | 8.5.2 / Gradle 8.7 |
| Default version | `1.0.6` (code `7`); overridable via Gradle properties |
| Persistence | Preferences DataStore, JSON files, SharedPreferences and internal files |

The Gradle/AGP pair above is what is checked in, not a compatibility guarantee; see [build limitations](docs/PROJECT_ANALYSIS.md#build-test-aur-ci).

## Build

Prerequisites: JDK 17, Android SDK Platform 34 and matching SDK Build Tools, plus network access to the Gradle distribution and the Google/Maven/plugin repositories. Open the project in a compatible Android Studio or configure `ANDROID_HOME` / `local.properties` yourself.

```bash
# Build the debug APK
./gradlew assembleDebug
```

Debug output: `app/build/outputs/apk/debug/`. Build release artifacts with:

```bash
sh ./gradlew assembleRelease bundleRelease
```

Release builds are unsigned by default. For local testing only, `-PlocalSign` uses a debug signing key; do **not** distribute that as a production release. `app/build.gradle.kts` also supports CI signing with `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The GitHub workflows instead build and sign in a separate step using repository secrets; see the [CI section](docs/PROJECT_ANALYSIS.md#build-test-aur-ci).

## Documentation

- [Full project analysis and developer guide](docs/PROJECT_ANALYSIS.md): architecture, flows, storage, permissions, security findings, build/CI and test plan.
- [Developer rules](AGENTS.md): native-Views-only architecture.
- [Five-phase Home launcher master plan](task.md): consolidated roadmap, acceptance gates and release-readiness criteria.
- [Historical lock design notes](docs/task-lock-system.md): superseded by the master plan; do not use as an implementation specification.

## License

The old README stated Apache 2.0, but this checkout has no `LICENSE` file. Confirm the intended license with the project owner before redistribution or adding a license file.
