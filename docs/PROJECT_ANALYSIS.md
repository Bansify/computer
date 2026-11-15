# Gothwad Computer — project analysis & developer guide

**Review scope:** repository source and configuration, reviewed 2026-11-15. Yeh static code analysis hai, device par runtime/security audit nahi. Feature claims below refer to the checked-in implementation, not guarantees on every Android version or device. Findings are observations and recommendations; no application behavior was changed during this documentation work.

## 1. Project kya karta hai?

Gothwad Computer Android ke andar desktop-like interface deta hai: installed Android apps ka grid/start menu, taskbar, aur app ke andar managed floating windows. Built-in file manager, WebView browser aur HTML/JS based store available hain. Store “install” actually browser shortcut banata hai, Android APK install nahi. Yeh Windows, PC hardware ya Windows applications ka emulator nahi hai.

**Important distinction:** Android TV/remote-related code aur legacy TV configuration present hain, lekin [`nav_graph.xml`](../app/src/main/res/navigation/nav_graph.xml) ka ek hi destination `PcLauncherFragment` hai. Manifest mein `LAUNCHER` aur `LEANBACK_LAUNCHER` hain, `HOME` / `DEFAULT` Home intent filter nahi. Isliye default launcher replacement ya separate TV UI ka claim current code se establish nahi hota. Purane README ke Compose, Media3 video wallpaper, weather aur Apache license claims bhi current build/files se supported nahi the.

## 2. Repository map

```text
app/src/main/
  AndroidManifest.xml             components, permissions, intent filters
  java/com/gothwad/computer/
    GothwadApplication.kt         DPI setup, crash logging, process unlock flag
    MainActivity.kt               entry activity, config, lock, intent actions
    Actions.kt                    Android app launch and system-settings actions
    data/                         config, app scanning, status/media/usage helpers
    ui/pc/                        desktop, adapters, taskbar, settings
    ui/dialogs/                   search, notifications, PIN, dashboard, media
    ui/view/                      lock views, keyboard, cards, shape drawable
    apps/floating/                in-activity floating window manager
    apps/files/                   file manager
    apps/webapp/                  WebView browser, shortcut management, records
    apps/store/                   store WebView and JavaScript bridge
    service/                      notification listener and overlay taskbar
  assets/store/                   bundled HTML, CSS, JS and app catalogue
  res/                            XML layouts, navigation, images, resources
  baseline-prof.txt               bundled baseline profile
.github/workflows/                APK/AAB build and release workflows
AGENTS.md                         project UI/architecture rules
```

Single Gradle module: `:app`. Build dependencies and versions are in [`app/build.gradle.kts`](../app/build.gradle.kts) and [`build.gradle.kts`](../build.gradle.kts). There is no backend/server module in this checkout; `metadata.json` mentions a Gemini capability, but that alone does not establish an implemented integration.

## 3. Architecture & main flows

```text
Android -> GothwadApplication -> MainActivity -> NavHost -> PcLauncherFragment
                                  |                         |
                            ConfigStore <----------- config observer
                                  |                         |
                         DataStore JSON            AppRepository -> PackageManager
                                                            |
                         built-in IDs / pwa:// ------------ + -> FloatingWindowManager
                                                            |      -> FileManagerView
                                                            |      -> WebAppView
                                                            |      -> GothwadStoreView -> JS bridge
                                                            + -> Actions.launchApp -> Android app

NotificationListenerService -> NotificationManagerBridge -> UI
FloatingTaskbarService -> system overlay (when allowed) -> MainActivity actions
```

### Startup and state

1. `GothwadApplication` applies custom DPI when configured and registers an uncaught-exception handler in the main process. It appends stack traces to internal `gothwad_crash.log`, deleting the previous file when it exceeds 64 KiB before the next append.
2. `MainActivity` applies locale/DPI, inflates `ActivityMainBinding`, registers a package-change receiver, checks the launcher device lock and collects configuration with lifecycle-aware coroutines.
3. Navigation loads `PcLauncherFragment`, which observes config and status, builds desktop/taskbar lists and opens built-in windows. `onDestroyView()` closes its managed windows.
4. `ConfigStore` stores one serialized `LauncherConfig` JSON string in Preferences DataStore `launcher_config` / key `config_json`. Updates are transactional via `edit`. Unknown JSON keys are ignored; malformed/undecodable configuration falls back to default config. This fallback can make existing settings appear reset; investigate before editing persisted data.

### App discovery and launch

[`AppRepository.kt`](../app/src/main/java/com/gothwad/computer/data/AppRepository.kt) queries both leanback and ordinary launcher activities, deduplicates by package (leanback preferred), excludes this app, assigns categories and sorts labels. It rasterizes icons/banners into `cacheDir/app_art` WebP files keyed by package and update timestamp, and also keeps an in-memory list. Package changes cause rescans. `Actions.launchApp()` prefers a leanback launch intent, then the regular launch intent; external apps run as their own Android activities, not inside the built-in floating windows.

`PcLauncherFragment` adds synthetic entries for File Manager, Web Browser and Store, and `pwa://<URL>` entries from config. Built-in entries are intercepted and opened in `FloatingWindowManager`. Normal app launch from the desktop checks the configured launcher app lock first; see security limitations below.

### Windows, file manager, browser and store

- [`FloatingWindowManager.kt`](../app/src/main/java/com/gothwad/computer/apps/floating/FloatingWindowManager.kt) manages app-owned Views inside the desktop: drag, minimize, maximize, restore, z-order and close. These are not arbitrary Android apps running in OS-level freeform windows.
- [`FileManagerView.kt`](../app/src/main/java/com/gothwad/computer/apps/files/FileManagerView.kt) lists, filters, sorts, navigates and performs file operations; sharing/opening uses `FileProvider`. Actual access is constrained by Android scoped storage and device permissions, especially on newer Android versions. The manifest's provider roots include external storage, app internal files and cache; granted URI access should be limited to the intended recipient.
- [`WebAppView.kt`](../app/src/main/java/com/gothwad/computer/apps/webapp/WebAppView.kt) creates WebView tabs, browser controls, bookmarks/history/downloads, and URL shortcuts. Web app shortcuts still load in this app's WebView. Downloads use Android `DownloadManager`. Web content and favicons require network access.
- [`GothwadStoreView.kt`](../app/src/main/java/com/gothwad/computer/apps/store/GothwadStoreView.kt) loads bundled `assets/store/index.html`; its JavaScript interface invokes `WebAppManager` to add/remove/open shortcuts. [`WebAppManager.kt`](../app/src/main/java/com/gothwad/computer/apps/webapp/WebAppManager.kt) normalizes URLs, fetches metadata/icons, saves PNGs and updates config. `pwa://` is a local identity convention, not an Android package or proof of standards-compliant PWA installation/offline support.

### System integrations

`ComputerNotificationListenerService` receives notifications after the user grants notification access and publishes state to UI. `FloatingTaskbarService` requires overlay access; platform background-service restrictions can affect its lifetime. `UsageTracker` computes most-used packages over the previous seven days only with usage access. `BackgroundMediaTracker` polls at roughly 1.5-second intervals and attempts to pause active sessions; success depends on Android permissions and the other app. Bluetooth status is best-effort: the current implementation can report a bonded device as connected and uses an estimated 85% battery in one fallback, so it is not authoritative telemetry.

## 4. Storage and lifecycle

| Data | Location / owner | Notes |
| --- | --- | --- |
| Launcher preferences, hidden/locked apps, credentials, desktop layout, web-app shortcut metadata | `launcher_config` Preferences DataStore, JSON key `config_json` | `LauncherConfig`; credentials currently stored as plain strings. |
| Locale | SharedPreferences `locale`, key `lang` | Applied in `MainActivity.attachBaseContext`. |
| Browser bookmarks/history/download records | internal `browser_records.json` | `BrowserDataStore`; history keeps up to 100 entries. |
| Web-app icons | internal `filesDir/webapp_icons/<MD5-of-URL>.png` | MD5 is used as a filename identifier, not for credential protection. |
| Custom wallpaper | internal `wallpaper_pc.jpg`, fallback `wallpaper.jpg` | Referenced by desktop wallpaper loader. |
| App artwork | cache `app_art/` | Disposable and recreated during scans. |
| Crash trace | internal `gothwad_crash.log` | May contain sensitive context; handle as diagnostic data. |
| Cookies / WebView data | Android WebView-managed storage | Shared cookie manager; not a separate private browsing profile. |

Manifest sets `allowBackup="true"`; backup behavior depends on Android version and platform defaults. Review backup rules and sensitive data before release. Clearing application data removes app-owned settings/files, not separately installed apps. Avoid promising data removal from external download locations or Android-managed services without device testing.

## 5. Permissions and platform access

Actual declarations: [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml). Availability and prompts vary by API level, device policy and Android version.

| Access | Reason / caveat |
| --- | --- |
| `QUERY_ALL_PACKAGES` | Discover launchable apps; broad package visibility requires Google Play policy justification. |
| Network, Wi-Fi and Internet | Status, browser and web content; `usesCleartextTraffic=true` allows HTTP. |
| `PACKAGE_USAGE_STATS` | Special user-granted usage access, not just an ordinary runtime permission. |
| `RECORD_AUDIO` | Voice search support; recognition availability depends on device/provider. |
| Bluetooth and `BLUETOOTH_CONNECT` | Device status; runtime permission on applicable versions. |
| `SYSTEM_ALERT_WINDOW` | User-granted display-over-other-apps access for overlay taskbar. |
| Storage/media permissions | Image selection and older external storage; scoped storage still limits file access. |
| `REQUEST_DELETE_PACKAGES` | Launch user-confirmed package deletion flow. |
| Notification listener service binding | User must enable notification access in system settings. |

`Actions.close()` and `BackgroundMediaTracker.killPackage()` call `killBackgroundProcesses`, but `KILL_BACKGROUND_PROCESSES` is not declared in the manifest. Neither should be described as guaranteed force-stop: Android restricts process control and users may need system App Info.

## 6. Security, privacy and correctness findings

Priorities below are suggested engineering priorities, not verified exploit severity. Reproduce and test on supported devices before shipping fixes.

### P0 — don't market current locks/private browsing as security boundaries

1. **Plaintext credentials and backup exposure.** `LockCredential.value` in [`Config.kt`](../app/src/main/java/com/gothwad/computer/data/Config.kt) is serialized into DataStore; backup is enabled. A launcher PIN is not a system lock and does not protect against all routes to other apps. Move to a salted, slow password verifier, review backup exclusions and provide a migration/reset strategy. Prefer Android system credentials for device protection; never log secrets.
2. **Exported activity launch path bypasses launcher checks.** Manifest exports `MainActivity`; `onNewIntent` handles `OPEN_APP` by calling `Actions.launchApp` directly, bypassing the device and app lock checks in `handleAppLaunch`. Explicit external intents can reach exported activities. Reproduce with a separate test app/ADB and route all app opens through a single validated authorization path, or restrict external entry points. Built-in window branches in `PcLauncherFragment.handleAppLaunch` also precede its app-lock check. These locks cannot prevent launching the same Android app from outside this launcher.
3. **Incognito is not private.** `createTabWebView(isIncognito)` changes cache mode but uses the shared `CookieManager`; `onPageFinished` records history without checking `isIncognito`. Do not rely on it for private browsing. Suppress history and design real profile/session isolation and cleanup; WebView-wide cookies make this nontrivial. Test cookies, storage, downloads, history and simultaneous normal/private tabs.

### P1 — hardening and release reliability

4. **Store JavaScript bridge trust boundary.** `GothwadStoreView` enables JavaScript/file access and attaches `AndroidStoreBridge`; its default `WebViewClient` does not explicitly restrict navigation. Bridge methods can modify shortcut state and open URLs. Audit all navigations and frames, constrain content to trusted local assets, validate bridge arguments/schemes, and encode values safely before `evaluateJavascript`. This is a potential attack surface; static review alone does not prove a reachable exploit.
5. **Web content policy is permissive.** General WebView enables JavaScript, file/content access, third-party cookies, mixed-content compatibility and autoplay; manifest permits cleartext. Review requirements, limit file/content access and URL schemes, prefer HTTPS, and make any needed exceptions explicit. Test login, downloads and trusted local assets after tightening.
6. **Data access and concurrency.** `AppRepository.scan` mutates a shared `HashSet` from multiple `Dispatchers.IO` async tasks; it is not thread-safe. Overlapping scans also share `memoryCache` and cache files. Use per-task results / synchronization and add concurrent-rescan tests. `BrowserDataStore` synchronizes individual reads/writes but read-modify-write sequences are not atomic across callers; exceptions on save are swallowed. Consider a single serialized transactional store and error reporting.
7. **Build configuration needs verification.** Wrapper points to Gradle 9.3.1 while AGP is 8.5.2; compatibility is not verified here. Pin and test a supported combination, including configuration cache and CI. Release lint is disabled and lint does not abort on errors. No tests under `app/src/test` or `app/src/androidTest` were found.
8. **Release workflow overlap.** `build_apk.yml` runs on pushes to `main`/`master` or manual dispatch and creates a release; `release.yml` runs on `v*` tags or manual dispatch and also creates one. They have independent run numbers/version defaults and may conflict or produce inconsistent version codes. Validate the chosen release flow and monotonic version codes. Both workflows require signing secrets and fetch a replacement wrapper JAR if missing/corrupt; verify provenance/checksums rather than silently relying on an unverified download.

### P2 — maintainability / product clarity

9. `launchOnBoot` and `pcStartupApps` are present in config/settings, but no boot receiver or execution path for startup apps was found. Treat them as unimplemented until behavior is added and tested. Other settings should be individually verified rather than assumed functional from UI labels.
10. Large UI files (e.g. `PcLauncherFragment`, `PcSettingsOtherTabs`, `WebAppView`, `FileManagerView`) combine UI, state and actions. Extract lifecycle-aware controllers/use cases, isolate persistence, and add automated tests before major refactors.
11. Old README claims did not match the current native-Views build. Keep docs tied to manifest, navigation and Gradle changes. Repository has no `LICENSE` file; owner should explicitly decide license.

## 7. Build, test aur CI

### Local setup

- JDK 17; Android SDK Platform 34 and build tools; an Android emulator/device at API 21+ (test modern API levels too).
- Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties` points to 9.3.1; AGP 8.5.2, Kotlin 2.0.20. Wrapper distribution and dependencies require network access to their respective hosts.
- `gradlew` is checked in without executable bit. Use `sh ./gradlew ...`, or `chmod +x gradlew` locally.
- Android Studio can generate `local.properties` with `sdk.dir`; do not commit local SDK paths or keystores.

```bash
sh ./gradlew --version
sh ./gradlew assembleDebug
sh ./gradlew testDebugUnitTest
sh ./gradlew lintDebug
# Requires a connected device/emulator
sh ./gradlew connectedDebugAndroidTest
# Release is unsigned by default
sh ./gradlew assembleRelease bundleRelease
```

These are intended commands, not evidence of successful execution. **Review environment:** no `java` executable or configured Android SDK was available, and the sandbox network is limited to selected hosts; therefore no Gradle build, lint, unit test or device test was run. `gradlew` and the wrapper JAR are present. Validate the toolchain/versions in a full Android build environment first.

`app/build.gradle.kts` uses R8/resource shrinking for release, disables release lint checks and supports version overrides with `-PversionCode=... -PversionName=...`. By default local release artifacts are unsigned. `-PlocalSign` signs with the debug key for local testing only. Alternatively Gradle can use `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` for CI signing; keep them outside source control.

### GitHub Actions

- [`build_apk.yml`](../.github/workflows/build_apk.yml): push to `main`/`master` or manual dispatch; derives or accepts version; builds debug/release APK and release AAB, signs release outputs in a separate step, uploads artifacts and publishes a GitHub release.
- [`release.yml`](../.github/workflows/release.yml): `v*` tag push or manual dispatch; similar build, signing, artifacts and release publication.
- Signing steps use repository secrets `RELEASE_KEYSTORE_BASE64` (fallback `KEYSTORE_BASE64`), `KEYSTORE_PASSWORD`, `KEY_PASSWORD` and optionally `KEY_ALIAS` (otherwise auto-detected/fallback). They require signing material to succeed. Do not put secret values in documentation, logs or git.

## 8. Suggested test matrix

| Area | Minimum checks |
| --- | --- |
| Install/start | Fresh install, process recreation, rotate/change DPI and language, background/resume; API 21, 29, 33/34+ where available. |
| Desktop | Mouse/touch/D-pad focus; scan after install/update/uninstall; pin/hide/reorder, grid resize and restart persistence. |
| Windows | Open multiple built-ins; drag/minimize/maximize/close; activity recreation and WebView cleanup. |
| File manager | Permission denied/granted, scoped-storage restrictions, inaccessible directory, open/share/delete and external media. |
| Browser/store | URL normalization, offline/error, redirects, downloads, shortcuts, store JS boundaries and WebView lifecycle; explicitly verify private-tab behavior. |
| Security | Locked app via all UI and exported intent paths, process restart, back button, backup/restore, malformed config. Never use real credentials in fixtures. |
| Integrations | Overlay and notification access denied/granted/revoked; usage access; Bluetooth permission; media control when provider unavailable. |
| Release | Debug/release build, lint, shrinker smoke test, signature verification, upgrade from previous release, version-code monotonicity and artifacts. |

## 9. Recommended next steps

1. Address exported launch path, plaintext credentials and misleading private mode before treating locks/browser privacy as production-ready.
2. Establish a reproducible supported Gradle/AGP/SDK combination and add CI for debug build, lint and tests on pull requests.
3. Add tests for config migration, app scanning concurrency, browser records, shortcut operations and security entry points; follow with device/instrumentation coverage.
4. Audit WebView bridge and access policy, backup rules, permissions, store policy and Android background restrictions.
5. Decide whether to implement genuine Home registration, TV mode and boot/startup features, or remove/label unsupported controls. Confirm license with owner.

## 10. Troubleshooting

- **`Permission denied` running `./gradlew`:** use `sh ./gradlew` or set the executable bit.
- **Java/SDK not found:** install JDK 17 and Android SDK 34; configure `JAVA_HOME` and SDK path. Check Gradle/AGP compatibility separately.
- **Dependency download fails:** verify access to Gradle distribution, Google Maven, Maven Central and Gradle Plugin Portal; restricted networks may block them.
- **Missing apps:** confirm app has a launchable activity, package visibility policy and package-change refresh; the launcher excludes itself.
- **Overlay/notifications/usage unavailable:** check corresponding Android special-access settings and device restrictions. A manifest declaration alone is not a grant.
- **Files inaccessible:** check Android version, provider and scoped-storage restrictions; broad filesystem access is not guaranteed.
- **Build signs differently than expected:** inspect Gradle signing settings and the relevant workflow; `-PlocalSign` is debug-only, whereas workflows perform separate release signing.
