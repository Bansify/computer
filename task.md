# Gothwad Computer — 5-Phase Home Launcher Master Plan

**Status:** Phase 1 and 2 implementation and hardening underway; acceptance gates remain open. All checkboxes remain pending until the corresponding automated and real-device evidence is recorded.
**Product goal:** Android TV-first, desktop-style Android HOME launcher for TVs and tablets; fully usable with remote/D-pad, touch, physical keyboard and mouse. Built-in apps remain Android Views/WebView features, not Windows executable emulation.
**Source baseline:** `com.gothwad.computer`, min SDK 21, target/compile SDK 34, Kotlin + XML/ViewBinding. Confirm current files before implementation; this plan is not proof a feature is implemented or tested.
**Priority:** Prevent user lockout and preserve a recoverable Home first; remote-only usability second; feature breadth and polish after that.

> This is the **canonical plan**, superseding old task instructions. [`task-lock-system.md`](docs/task-lock-system.md) is historical, not an implementation specification. In particular, do not follow its claims of unbypassable accessibility locks or its instruction to discard old credentials. Never assume a launcher or accessibility service can control all Android/OEM behavior.

## Ground rules for all five phases

- [ ] Work sequentially: complete the phase gate and record evidence before starting the next. Fix regressions from earlier phases before continuing. No checkbox may be marked complete based only on code review.
- [ ] Follow [`AGENTS.md`](AGENTS.md): native Android Views/XML, ViewBinding, Fragments, RecyclerView and lifecycle-aware coroutines; **no Compose or Compose/View bridge**. Keep startup and background work lightweight for low-memory TVs.
- [ ] Use official, user-consented Android HOME selection and optional accessibility capabilities; no root, system-app privilege, hidden APIs, custom ROM requirement, coercive permission prompts, or assumed OEM exemptions. Treat special access as optional for basic Home/recovery.
- [ ] Never claim 100% crash-free behavior, universal shortcut interception, a guaranteed always-running service, or unbypassable launcher locks. Android/OEM controls system keys, process lifetime, background launches and fallback behavior.
- [ ] During development and rollout, keep a working stock/alternate Home installed and a tested way to reach system settings. Do not disable/remove it or repeatedly force the launcher to foreground. Same-package recovery is best-effort, not a substitute for an independent Home.
- [ ] Avoid aggressive automatic killing of other apps, periodic watchdog loops, forced foreground launches, exact-alarm restart loops and silent changes to device security. Recover with bounded, platform-compliant mechanisms and user choice.
- [ ] Preserve existing users' settings and credentials through upgrades; version and test migrations, rollback/recovery and backup/restore. No silent lock disable or data loss. Report failures without logging secrets, URLs, notification contents or sensitive file paths unnecessarily.
- [ ] Every phase includes unit/instrumentation tests where applicable, real-device checks, docs and a rollback plan. Permission denial, revoked access, no network, unavailable hardware and OEM restrictions must degrade gracefully.
- [ ] Record for each gate: build/commit, device model/OEM, Android version, input method, commands, actual results, known limitations, issue links and responsible reviewer. Blank evidence = gate not passed.

### Definition of done for *every* task

Implementation + relevant automated tests + device verification + accessibility/focus review + failure handling + migration/security impact + documentation are complete. Keep existing functionality working or explicitly document and approve a deliberate change.

---

## Phase 1 — Build foundation, safe HOME entry and recovery

**Outcome:** A testable Android HOME candidate that opens a minimal usable screen even when optional components fail, with a real escape route. Do not recommend it as the user's only Home yet.

**Progress (2026-11-15):** HOME manifest registration, Home settings route, guarded startup, non-destructive config decode, initial crash-count safe screen, a debug-only startup-fault fixture and build-only CI are implemented but not device-verified. Build, unit tests, lint and Android 29 emulator smoke tests passed on `35641ff` ([CI run](https://github.com/GothwadTech/computer/actions/runs/43670030903)); consult latest CI for later changes. Config/icon/wallpaper fault fixtures, real-device checks, performance budgets and full recovery/lock validation remain open. Initial emulator smoke tests and explicit redacted diagnostics sharing have been added; see the latest CI run for status. See [Phase 1 test protocol](docs/PHASE1_TESTING.md).

### 1A. Reproducible build and baseline

- [ ] Install/configure JDK 17, Android SDK and tools; validate and pin a supported Gradle/AGP/Kotlin/SDK combination (wrapper now 8.7 / AGP 8.5.2; verify current CI and device results). Verify wrapper integrity and distribution checksum. Fix `gradlew` executable mode.
- [ ] Establish CI on pull requests: clean debug/release builds, unit tests, actionable lint, dependency/wrapper checks; add instrumentation coverage on available emulators. Address lint failures rather than globally suppressing them.
- [ ] Inventory existing features, permissions, startup path, supported devices/API versions and baseline profile. Before setting numeric budgets, measure cold/warm Home startup, input responsiveness, frame times, memory and crashes on named TV/tablet devices. Record reproducible methods and agree budgets; do not claim historical memory figures without measurement.
- [ ] Create test fixtures and debug-only fault injection for config, icon, wallpaper, startup and optional service errors. Do not ship destructive fault triggers in production.

### 1B. HOME capability and user choice

- [ ] Add Android `MAIN`/`HOME`/`DEFAULT` registration in the correct exported activity while retaining appropriate ordinary and leanback launch entries; validate behavior with resolver/Home role on supported Android versions and OEMs. Do not assume an app can silently select itself.
- [ ] Provide a remote-accessible onboarding/status screen and system default-Home settings route, with graceful fallback if an intent/role API is unavailable. Explain how to restore another Home and keep an alternate launcher installed during tests.
- [ ] Define Home return behavior for cold start, warm start, external app return, repeated Home press, boot and app update; honor OS/OEM task behavior and never steal focus via a force-to-foreground loop. Do not promise boot receiver/background activity launches on all devices.

### 1C. Safe startup and bounded recovery

- [ ] Draw a minimal, usable Home before optional network, media, WebView, overlay and expensive artwork initialization. Keep Home usable offline and without accessibility, overlay, notifications or usage access.
- [ ] Handle config decode/corruption, icon/wallpaper decode errors, missing package/activity, permission denial and optional-service failures with explicit, bounded fallbacks. Preserve recoverable old config instead of silently overwriting it with defaults; expose an understandable recovery action.
- [ ] Implement persistent, bounded startup-failure detection with a clear reset after a successful healthy launch. On repeated failures offer/start a **minimal safe mode** where feasible: default background, no optional startup features, basic app launch, system settings and default-Home settings. Prevent restart loops; do not silently wipe data or disable credentials.
- [ ] Make recovery usable with remote alone, touch, keyboard and mouse. Design locked-state recovery explicitly: do not bypass a valid lock or trap a user without an honest documented recovery path. Keep an independent stock/alternate Home as the ultimate fallback; app-internal safe mode cannot guarantee recovery from process/package/OS failure.
- [ ] Improve bounded, privacy-conscious crash diagnostics: retain causal logs, avoid secrets, preserve normal Android error handling, give user-controlled export. No claim that catching exceptions recovers from ANR, native crash, OOM or process kill.

**Phase 1 gate**
- [ ] Clean debug/release builds + CI evidence and initial automated tests pass. Baseline measurements and agreed budgets documented.
- [ ] On at least one real Android TV device: select Home with consent, boot, open another app, return with Home, change back to alternate Home, and recover from injected startup/config/wallpaper failures without an endless loop. Test equivalent basic Home flows on a tablet.
- [ ] Deny special permissions/offline and confirm Home + recovery are usable with remote. Log device/OS-specific limitations and rollback procedure.

---

## Phase 2 — Remote-first desktop, focus and core reliability

**Outcome:** A remote-only user can navigate and recover the entire essential Home experience. Scanning and lifecycle work no longer jeopardize Home stability.

**Progress (2026-11-15):** App scans are serialized with bounded artwork concurrency; package-change refresh, remote focus/flyout handling, Home/Back behavior, sampled wallpaper decode, lifecycle cleanup, memory trimming and Bluetooth/status fallbacks were added. Unit and Android 29 emulator smoke tests passed on `35641ff` ([CI run](https://github.com/GothwadTech/computer/actions/runs/43670030903)). Full-screen remote accessibility, OEM/device tests, package-race stress, profiling and phase gates remain unverified; follow [Phase 2 test protocol](docs/PHASE2_TESTING.md).

### 2A. Focus/navigation contract

- [ ] Define a focus map for desktop grid, taskbar, start menu, search, settings, quick settings, notifications, app/context menus, dialogs, lock and safe mode: initial focus, transitions, boundaries, Back, modal trapping and restoration.
- [ ] Implement visible high-contrast TV-distance focus indicators; test overscan, scaling, long labels, RTL and 720p/1080p/4K layouts. Provide meaningful accessibility labels and states, not color-only focus cues.
- [ ] Ensure every essential operation works with D-pad up/down/left/right, center/enter, Back and a standard Android TV remote. Provide remote alternatives for right-click, long-press-only, drag-only and hover-only actions, including reorder and app menus.
- [ ] Preserve/recover focus on dialog open/close, empty lists, list refresh, package removal, rotation/configuration changes, external-app return, process recreation and remote disconnection. Prevent focus entering hidden/behind-modal views or becoming stranded.
- [ ] Review browser/file manager/floating windows: all Home-critical exit/close controls reachable via remote. If any secondary feature is not fully remote-accessible, label its limitation; it must never trap focus or block return Home.

### 2B. App discovery and lifecycle

- [ ] Fix `AppRepository.scan` shared `HashSet` mutation and overlapping scan/cache races. Bound concurrency, safely invalidate caches, tolerate package add/remove during scan and handle corrupt/missing artwork.
- [ ] Ensure package receiver, adapters, coroutines, dialogs, WebViews, windows and services are lifecycle-safe; avoid stale ViewBinding access, leaked listeners/context and blocking main-thread I/O. Validate app-list and focus restoration after package changes.
- [ ] Handle memory pressure by releasing **our own** caches/resources, bounding bitmap sizes and reducing concurrent work; do not indiscriminately kill other apps. Profile low-RAM devices and prevent large wallpaper/artwork OOMs.
- [ ] Verify notification and overlay service behavior under Android background limits; core Home works if service is stopped/denied. Fix Bluetooth UI to distinguish unknown, bonded and actually connected states; never display invented battery as measured.
- [ ] Audit current settings/feature claims. `launchOnBoot` and `pcStartupApps` have no confirmed execution path: implement only with user consent and platform-compliant behavior, or label/remove them. Make package close/force-stop claims honest and review missing `KILL_BACKGROUND_PROCESSES` permission and platform restrictions rather than promising force-stop.

**Phase 2 gate**
- [ ] Remote-only journey passes on a real TV: Home → app → Home → taskbar/start/search/settings → context menu → dialog → Back → recovery, with focus visible and restored at every step.
- [ ] Automated focus/lifecycle/scanning tests pass, including concurrent scans, package changes, corrupted artwork and process recreation. No new crash, focus trap or regression in Phase 1 tests.
- [ ] Record memory/startup/frame results against Phase 1 agreed budgets on at least one low-memory TV; investigate and resolve budget misses before proceeding.

---

## Phase 3 — Touch, keyboard, mouse and configurable shortcuts

**Outcome:** Four input types work together; shortcuts have explicit scope, conflict handling and honest Android/OEM limitations.

**Progress:** A limited opt-in, per-device foreground shortcut editor and mapping policy now exist for Start, Search and Quick settings, with conflict rejection and clear-all. Legacy mappings remain unchanged. Individual editing, optional cross-app behavior, full input parity, automation and physical-device validation remain open; see [release blockers](docs/REMAINING_RELEASE_WORK.md).

### 3A. Input parity

- [ ] Create a shared input/action layer separating physical events from launcher actions. Specify priority for focused text fields, dialogs, windows and system keys; consume only events actually handled. Keep accessibility semantics and existing D-pad behavior intact.
- [ ] Audit tablet touch targets, scrolling, long-press vs drag, resizing, gestures, on-screen keyboard and orientation. Provide alternatives to gestures; avoid accidental launches during drag.
- [ ] Audit keyboard Tab/Shift+Tab, arrows, Enter/Space, Escape/Back, text input and repeat events. Ensure focus never disappears during input-device changes.
- [ ] Audit mouse pointer, hover, left/right click, scroll, drag, double-click and context menu positioning. Test desktop/window controls and focus when switching between mouse and remote.

### 3B. Shortcut engine and configuration

- [ ] Inventory existing cheatsheet strings and implement only verified launcher-foreground actions (e.g. search, settings, start menu, file manager, window navigation) with configurable key/modifier mappings. Remove or label unsupported shortcuts rather than claiming they work.
- [ ] Add a remote/keyboard “press a key to learn” UI, conflict detection, per-device/key-code awareness, action selection, clear/reset defaults, persistence and accessible remote-only editing. Do not silently overwrite mappings; handle key-down/up/repeat and text-entry contexts.
- [ ] Treat existing `buttonMap` as legacy data: version/migrate safely if its format changes. Do not intercept Home, power, volume, accessibility/system-reserved keys or other events that Android/OEM does not deliver; never promise Win/Alt+Tab or dedicated remote buttons work universally.
- [ ] Add an **optional**, explicitly enabled accessibility service only where needed for supported cross-app shortcuts; declare/request applicable platform capabilities and follow current platform/store accessibility policy. Explain scope/data access, user consent, disable path and graceful behavior when unavailable/revoked. Never use it to force Home, spy on content or claim it enforces system-wide locks.
- [ ] Distinguish launcher-foreground vs best-effort global shortcuts in UI. Test actual delivered events across supported OEMs; pass through unmapped, unavailable and reserved events without breaking other apps. Core Home and recovery must work with accessibility off.

**Phase 3 gate**
- [ ] Same core Home/recovery journeys pass with remote, tablet touch, physical keyboard and mouse, including switching input mid-flow and text-field conflict tests.
- [ ] Automated shortcut mapping/conflict/migration tests pass. Real devices verify both foreground shortcuts and any optional cross-app shortcuts; document unavailable system/OEM keys and permission-off behavior.
- [ ] Accessibility service, if enabled, is user-controlled, policy-reviewed and demonstrably not required for Home startup or recovery. All previous phase gates remain green.

---

## Phase 4 — Security, privacy and built-in feature hardening

**Outcome:** No misleading lock or privacy promises; optional built-ins are isolated and do not take down Home.

### 4A. Launcher locks and data

- [ ] Centralize authorization for all launcher-originated app actions, including desktop, search, pinned/taskbar, built-ins, web shortcuts, overlay and `MainActivity.onNewIntent("OPEN_APP")`. Restrict/validate exported intents and explicitly test initial activity creation vs `onNewIntent`. Avoid confused-deputy launch paths.
- [ ] Keep device, app and hidden-app credentials independent, with remote/keyboard/touch accessible setup and entry. Clarify that these are launcher-level controls, not Android system security or a guarantee against external launch, notifications, recents or Settings.
- [ ] Replace plaintext credential/reveal-code storage with a reviewed salted, slow verifier where appropriate; design atomic, versioned migration and failure/rollback handling for existing users. Never log secrets; test interrupted migration, wrong credentials, upgrades and backup/restore. Define responsible recovery without silently unlocking or losing data.
- [ ] Review `allowBackup`/data-extraction rules and sensitive browser/config/diagnostics exposure across supported Android versions. Minimize data and request explicit consent before sharing diagnostics.

### 4B. WebView, privacy, files and data safety

- [ ] Fix or remove/clearly disable misleading incognito mode until private browsing isolation is demonstrably correct. Do not merely turn off cache: verify history, shared cookies, WebView storage, downloads, concurrent normal/private tabs and cleanup; disclose any platform limitations.
- [ ] Harden Store JavaScript bridge: trusted local content/navigation and frame policy, validate input URLs/schemes and origin assumptions, safely encode JS values, restrict bridge capabilities and test malicious/untrusted navigation. Treat reachability as a testable risk, not a proven exploit.
- [ ] Review general WebView JavaScript, file/content access, third-party cookies, mixed content, HTTP cleartext, autoplay and download handling. Restrict to minimum required; preserve known legitimate flows with tests and clearly explain exceptions.
- [ ] Make browser records read-modify-write transactional and errors observable; test concurrent bookmarks/history updates, corruption and migration. Keep user data on recoverable failures.
- [ ] Review FileProvider roots, MIME types, URI grants and recipients; test scoped storage, denied/revoked permission, path traversal, inaccessible folders, file operations and external media across supported Android versions. Fail safely without blocking Home.
- [ ] Validate third-party icon/network fetching, timeouts, malformed/oversized images and redirects. Store web apps are shortcuts, not APK/PWA installation guarantees; label accordingly. WebView/Store/File Manager crashes or hangs must not make Home unrecoverable.

**Phase 4 gate**
- [ ] Unit/instrumentation and real-device security tests cover every launcher-originated entry point, locked/recovery flow, migration, restore, WebView/store trust boundary, private-mode claims and file URI access. Explicitly document system-level limitations and remaining risks.
- [ ] Tests confirm optional built-ins fail independently and remote can always return to Home. No open critical/high security finding within the defined release threat model; earlier gates remain green.

---

## Phase 5 — Whole-product hardening, release and Home-readiness sign-off

**Outcome:** Release candidate has objective evidence for the declared device matrix, recovery paths, permissions and support; no unsupported blanket “works on every device / never crashes” claims.

### 5A. Full compatibility, resilience and regression

- [ ] Declare supported Android versions, TV/tablet classes, OEM models and input types **before** release. Include current min SDK 21 in validation or formally approve/document a min-SDK change and upgrade impact. Test real Android TV/Google TV remote, low-memory TV box, touchscreen tablet and physical keyboard/mouse; use emulators only as supplements. Do not claim specific OEM support without evidence.
- [ ] Run all Phase 1–4 journeys plus fresh install/default Home, reboot, upgrade/rollback, app process kill, repeated startup failure, system low memory, external app return, rapid Home/Back presses, permission revoke, offline, corrupt data, full storage, app install/update/remove and orientation/density changes. Simulated fault injection must not be shipped enabled.
- [ ] Run focused ANR/crash/memory/leak/frame profiling and extended repeated-use/soak tests on the declared low-end device and a representative tablet. Compare to agreed Phase 1 budgets; investigate all crashes, ANRs, lost-focus incidents and recovery failures. Preserve reproducible evidence and fixes.
- [ ] Validate all promised settings and shortcuts, accessibility disclosures, optional service behavior, notification/media limitations, Bluetooth status, boot/startup behavior, file restrictions and WebView privacy. Remove or explicitly label any feature that cannot meet its promise.
- [ ] Confirm no crash loop or mandatory permission prompt blocks Home; safe mode, system settings and alternate Home route are usable with remote-only. Verify that recovery does not silently bypass a valid credential or erase user data. Record OEM cases where platform restrictions limit recovery.

### 5B. Release engineering, docs and operations

- [ ] Consolidate/review `build_apk.yml` and `release.yml` triggers, version-code monotonicity, duplicate releases, signing-secret handling, wrapper verification and artifacts. Build/sign/verify debug, release APK and AAB; test clean install and upgrade from the actual previous release. Keep keystores/secrets out of git.
- [ ] Add release CI checks for build, lint, tests, security/dependency review and available device automation; document any manual gates that CI cannot cover. Restore actionable release lint.
- [ ] Publish user-facing Home selection/restore guide, input/shortcut compatibility, permission rationale, lock limitations, safe mode and troubleshooting. Update README and [`docs/PROJECT_ANALYSIS.md`](docs/PROJECT_ANALYSIS.md) to match the final implementation, not stale design claims. Confirm license with owner.
- [ ] Define privacy-conscious, user-consented diagnostics, issue triage, staged rollout, monitoring and rollback to prior signed build / alternate Home. Do not rely on telemetry or network for core Home.

### Final release gate — required evidence, not aspiration

- [ ] On **every device/input combination in the declared release matrix**, all essential Home, navigation, default-Home change and recovery journeys pass; record exact device/OS/build/results. A failure blocks support for that combination until fixed or explicitly removed from the published matrix.
- [ ] All automated checks pass; no unresolved release-blocking crash, ANR, focus trap, Home lockout, data-loss or security issue. Non-blocking issues have owner, workaround and documented acceptance.
- [ ] Measured startup, frame responsiveness and memory meet the budgets agreed in Phase 1; soak duration, cycles, methodology and results are recorded. Any threshold change requires explicit review, not silent adjustment.
- [ ] Upgrade/migration and signed release artifacts verified; fallback Home and recovery tested. Product/engineering owner signs off the evidence and staged rollout/rollback plan.

**Rock-solid means:** the above gates pass on the declared support matrix, known limitations are transparent, failures are isolated and recovery is tested. It does **not** mean Android/OEM failures or future crashes are mathematically impossible.
