# Phase 2 — remote-first and reliability verification

Status: implementation in progress; no real-device sign-off yet. GitHub emulator smoke tests are supplementary, not proof of OEM/TV compatibility. Keep an alternate Home installed. Record build SHA, device/OEM, Android/API, screen/resolution, RAM, input device, permissions, steps, expected/actual results and diagnostics without private data. Attach each result to the Phase 1/2 gate in `task.md`.

## D-pad-only core journey

Do not touch the screen or use a mouse. Check focus is clearly visible from TV viewing distance and that center/select activates only the focused element.

1. Launch Home from cold start. Find the initial taskbar focus. Move through taskbar, desktop grid, Start and quick settings using D-pad; verify no hidden view captures focus.
2. Open Start; focus should enter its search control. Back should dismiss it and return focus to its opener. Repeat for quick settings. Test search, Android settings, notification sheet, dialogs and app context menu (long-press center on an app); after dismissal verify focus returns somewhere usable.
3. Launch a real app; press Home to return. Check external app resume, Home again, Back on desktop, and Home with built-in windows open. Back should close the top window/flyout before returning to desktop; it must not exit Home just because it was pressed twice.
4. In the desktop grid, focus an app, refresh/install/update/remove another package and verify the same app stays focused where possible; remove the focused package and check a sensible remaining focus. Test empty/many-app lists and reorder using the app context menu. Bluetooth/IR remotes and OEM key mappings may differ.
5. Open File Manager and Web Browser; verify the close/minimize controls and Back remain reachable, and Home never becomes trapped behind a window or overlay. Detailed full remote support of secondary content is not yet claimed; record any inaccessible controls.
6. Check lock, safe recovery, unreadable config and retry screens with D-pad alone. Do not set an untested launcher as the only Home or assume launcher locks prevent outside/system access.

## Faults, lifecycle and data

- Deny/revoke overlay, notification, usage and Bluetooth permissions; run offline and when Wi-Fi or audio services are unavailable. Home must remain usable. Bluetooth status can be unknown; a paired device is not proof it is connected and battery percentage must never be fabricated.
- Exercise package install, update, disable, uninstall and locale changes during repeated refreshes; stress app artwork and ensure no uncaught scan/cache failures. Remove or corrupt disposable app-art cache files and validate reload. Record if focus moves unexpectedly.
- Select huge and corrupt custom wallpaper on a disposable installation. Verify bounded decoding/fallback and that foreground Home remains usable; repeat with rapid wallpaper changes and activity recreation. Never use real user images or config as destructive fixtures.
- Open/close repeated web/store/file windows and dialogs, background/resume and recreate process; observe leaks, freezes and focus. Configure app locks and verify relevant routes; launcher-level locks are not system security.
- Measure cold/warm Home startup, input-to-focus latency, frame/jank and memory on named low-memory TV and tablet, under empty and large app lists. Use `adb shell am start -W`, Android Studio profiler / `dumpsys meminfo` / frame metrics where available, and record samples and method. Numeric release budgets must be agreed from actual data.

## Automation and outstanding gates

- Unit tests exercise bitmap sample-size selection, focus restoration policy, config decoding and startup recovery policy. CI builds debug/release/bundle and runs lint; lint currently does **not** fail the build on every error. Review lint artifacts.
- The CI emulator job runs instrumentation smoke tests for the HOME intent filter, remote-style Start/Back navigation and recovery screen, provided the emulator job is green. Read its latest run; emulator keyboard injection cannot prove physical remote behavior.
- No exhaustive instrumentation for all screens, package-update races, wallpaper fault injection, ANRs, native crashes, OEM Home behavior or long-run resource stability is claimed yet. Physical TV/tablet input testing, benchmarks and recovery tests are required before Phase 1 or 2 can be marked complete.
