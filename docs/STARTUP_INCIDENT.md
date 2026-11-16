# Reported recovery on v1.0.21

The owner reported that Gothwad Computer entered its recovery screen immediately on a real device. The device model, Android version, recovery message and any diagnostics have not yet been provided. **The cause has not been confirmed.** The fact that debug emulator CI passed does not invalidate the real-device report. Keep a separate working Home selected; do not clear data or uninstall the app as a first troubleshooting step.

## What changed for investigation

- `release-home-smoke.yml` builds a locally debug-key-signed, minified release and launches it on Android emulators, checking that the desktop is visible rather than recovery. It also attempts to run the exact published v1.0.21 APK for comparison. These tests do not use the production signing key and cannot reproduce all OEM/TV conditions.
- Caught desktop startup errors, config read/observer failures, incomplete lock configuration, and recovery after earlier crashes now create local, bounded, redacted diagnostics. They record reason codes, exception/cause types and code locations, **not exception messages or user settings**. A recovery screen offers an explicit share action. Logs can include device-specific code locations and timestamps; users should review before sharing. Failures in v1.0.21 that were caught rather than uncaught were not added to its crash file, so its share action may say no diagnostics exist.
- Release build mapping is saved as an Actions artifact from the actual release workflow. Obfuscated stack frames may otherwise be difficult to interpret. Artifacts have a retention period; preserve the matching mapping for a release if needed.
- A debug-only desktop fault fixture tests caught-failure recovery, privacy of diagnostic output and retry. It is a no-op in release builds. Unit tests verify redaction and size bounds.

## Owner evidence and safe recovery

1. Record the TV/tablet model, Android version, installed APK version and whether it was a fresh install or an upgrade/restore. Photograph the entire recovery message and the options visible. Do not include passwords or private notifications.
2. If **Share redacted crash diagnostics** produces a report, review it and share it only if comfortable. It may have no report for a caught error in v1.0.21. If available, a developer can collect the relevant `AndroidRuntime`, `MainActivity` and `GothwadApplication` lines from `adb logcat`; raw logcat may include private information and should be reviewed first.
3. Keep the stock/alternate Home as default while investigating. Do not repeatedly restart, force Home, wipe data, or uninstall without a tested backup and explicit owner approval. The v1.0.21 signing change can also affect upgrade/rollback and uninstall can delete local data.

A green run is evidence only for its exact emulator/images and build. Resolution requires a cause, a regression test and the owner's device retest; recovery itself is not proof of an uncaught crash because it can also be entered for a caught startup/configuration problem.
