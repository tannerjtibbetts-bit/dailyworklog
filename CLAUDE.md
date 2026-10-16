# Daily Work Log

Tanner's app for logging daily field work (checklists of fields, crew, texts, reports, overtime,
notes, sea can movements). Tanner is not technical and works from his phone: explain things in
plain words and give exact steps.

## The two versions (keep them in step)
- `classic-android/` — the original Android app (Java). Tanner builds it on his phone in Termux:
  he unzips a `DailyWorkLog-vX.Y.zip` of this folder (as `DailyWorkLog/`) into `~` and runs
  `bash ~/DailyWorkLog/build-termux.sh`, then installs and taps Update. Bump `versionCode` /
  `versionName` in `app/build.gradle` for every zip. JVM tests of its logic are in
  `classic-android/tests/`.
- `mobile/` — the new Flutter app for iPhone and Android (same features, same `worklog.json`
  format, same package id `com.dailyworklog.app` so it installs as an update). `flutter analyze`
  and `flutter test` must pass. CI (`.github/workflows/mobile.yml`) builds Android and iOS;
  `codemagic.yaml` makes signed iPhone builds for TestFlight.

Every feature change goes into BOTH versions unless Tanner says otherwise.

## The user manual — always update it
The in-app user manual (Support → User manual, on the opening screen, the sign-in screen and
profile → More…) is `mobile/assets/manual/manual.json`, with screenshots `mobile/assets/manual/*.png`.
The classic app shows the same files from `classic-android/app/src/main/assets/manual/`.

**Whenever anything in the app changes, update the manual in the same change:** edit or add the
sections in `manual.json` (quote button labels exactly as the app shows them; mark features only
in the new app `"newAppOnly": true`, only in the classic app `"classicOnly": true`), re-take any
screenshot that no longer matches, copy the manual folder into the classic app's assets, and run
`flutter test test/manual_test.dart` (checks links, pictures and search).
