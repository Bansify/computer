# Phase 1: Home and recovery test protocol

These checks are **pending** until recorded on real devices. This build is not yet recommended as the only installed Home. Keep a working stock or alternative launcher installed; don't disable it. This app is a Home candidate only after the user chooses it in Android settings.

## Test record

Record commit/build, build variant/signature, date, device model/OEM, Android version, RAM, installed Home apps, input device and results for each case. Capture steps and limitations without passwords, browsing history or private notifications. A CI build does not replace TV/tablet testing.

## First install and rollback

1. Install the debug APK alongside the stock Home; do not uninstall/disable the stock launcher. Open the app once as an ordinary app.
2. In app settings > Apps > Default Home app, open system Home settings. Select Gothwad only with user consent. Depending on Android/OEM, the fallback may open general Android settings instead.
3. Press Home, open an Android app, return Home, reboot, and return again. Test from cold and warm starts and after an app update. Record actual behavior; neither a boot receiver nor a background relaunch is promised.
4. Open Default Home app again and choose the stock launcher. Verify Home returns to it. If the app is unusable, use the device's system settings or recovery procedure to switch Home; keep the alternative installed throughout. A Home selector may vary by OEM.

## Failure and recovery

- Verify settings read and an enabled launcher lock before desktop or recovery apps become accessible. Lock is a launcher UI feature, **not** a system-wide security boundary.
- In a disposable test installation, preserve a backup before introducing invalid config data using a controlled debug/test fixture. Confirm recovery reports unreadable settings, does not silently reset stored data, offers retry/system settings and does not list apps as unlocked. No in-app reset/export of corrupted config is currently implemented.
- On a **disposable debug installation only**, the debug source set provides a startup-crash intent. Ensure the stock launcher is installed and selected first; force-stop before each attempt so `onCreate` is called. Example with an attached ADB device:

  ```bash
  adb shell am force-stop com.gothwad.computer
  adb shell am start -n com.gothwad.computer/.MainActivity -a com.gothwad.computer.DEBUG_CRASH_STARTUP
  # Repeat force-stop + start once, then launch normally from the app list.
  ```

  This intentionally crashes the debug app; it is absent from the release implementation. Do not run it on a user's only Home. After two uncaught startup failures, manually relaunch: safe screen should offer retry, Android settings, default Home, explicit redacted diagnostics sharing and a simple app list (up to 100 entries) after successful lock check. After retry, 30 seconds continuously resumed without a startup failure should clear the recorded count. No automatic relaunch is attempted.
- Current detection covers recorded uncaught startup exceptions and synchronously caught desktop startup failures; it does **not** reliably detect process kills, ANRs, native crashes, exceptions before the handler is installed or crashes after the startup window. Safe mode is in the same app/process and can itself fail. Use alternate Home as fallback.
- Test remote focus, center/Back, touch, keyboard and mouse on recovery; lock/app-lock paths, denied overlay/notification/usage access, offline startup, missing system settings activities and OEM Home settings. Verify no unwanted data erase, lock bypass or restart loop.
- Phase 1 does not yet include config/icon/wallpaper fault-injection fixtures or measured real-device performance budgets. Basic emulator instrumentation exists; diagnostics sharing covers only the redacted v2 log, not old logs. Do not mark the phase gate complete until implemented or explicitly reviewed and real-device evidence is recorded.

## Measurements to capture before choosing budgets

On named real TV and tablet models, record cold start (force-stop, launch Home), warm Home return from another app, idle and startup memory, input-to-focus response, frame/jank traces, and failures over repeated launches. State measurement commands/tool versions, sample count, median/worst result and device conditions. Agree budgets using actual data; no numbers are implied by this plan.
