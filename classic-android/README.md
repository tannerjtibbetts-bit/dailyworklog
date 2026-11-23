# Daily Work Log 2.0

Same package name as your original app (`com.dailyworklog.app`), version 2.0.

## Build it
1. Unzip, then in Android Studio choose **File > Open** and pick the `DailyWorkLog` folder.
2. Let Gradle sync finish (first time downloads libraries, needs internet).
3. Press **Run** to try it on an emulator or your plugged-in phone,
   or **Build > Build App Bundle(s) / APK(s) > Build APK(s)** to get an APK.

If your phone refuses to install over the old app ("package conflicts"),
uninstall the old one first. The old app didn't save data, so nothing is lost.

## Testing checklist on the phone
- Projects: create, long-press to edit/delete, set your name (person icon).
- New log (+): date picker, then Blank / Carry over unfinished / Copy all / Template.
- Checklist: tap circles, filter chips, drag to reorder (press and hold),
  camera icon on a field, blue mic adds a field by voice, top bar title on scroll.
- Edit Field: notes + dictation, Assign User, Photos (camera + gallery),
  Photos required switch (blocks completing until a photo is added), remove photo.
  Load securement checkbox (shows a green chip on the card; only printed in reports when checked).
- Menu (...): Export to PDF and Word, then Share / Open / Save to phone.
- Rotate the phone mid-camera and confirm the photo still attaches.

## Where things are
- `Models.java` data, `Store.java` saving (files/worklog.json, photos in files/photos)
- `LogActivity.java` checklist screen, `TaskActivity.java` Edit Field
- `ReportPdf.java` / `ReportDocx.java` report layouts (colors and sizes at the top)
- `res/values/colors.xml` app colors
- `NoteActivity.java` Notes tab editor, `OvertimePane.java` Overtime tab
- `ScanReader.java` reads text in photos, `SheetImport.java` turns photos/Excel/CSV into rows,
  `ImportActivity.java` the review screen before fields are added
