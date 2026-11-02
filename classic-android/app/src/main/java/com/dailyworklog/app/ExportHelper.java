package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generates the PDF/Word report in the background, then offers Share, Open or Save to phone. */
final class ExportHelper {
    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CSV = "text/csv";

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String> savePdf;
    private final ActivityResultLauncher<String> saveDocx;
    private final ActivityResultLauncher<String> saveXlsx;
    private final ActivityResultLauncher<String> saveCsv;
    private File pending;

    /** Must be created in onCreate (registers the "Save to phone" pickers). */
    ExportHelper(AppCompatActivity a) {
        activity = a;
        savePdf = a.registerForActivityResult(new ActivityResultContracts.CreateDocument(PDF), this::onSaveTo);
        saveDocx = a.registerForActivityResult(new ActivityResultContracts.CreateDocument(DOCX), this::onSaveTo);
        saveXlsx = a.registerForActivityResult(new ActivityResultContracts.CreateDocument(XLSX), this::onSaveTo);
        saveCsv = a.registerForActivityResult(new ActivityResultContracts.CreateDocument(CSV), this::onSaveTo);
    }

    interface Writer {
        void write(OutputStream os) throws Exception;
    }

    /** Just the Overtime tab, as a PDF or an Excel sheet, then Share / Open / Save to phone. */
    void exportOvertime(Models.Project p, boolean excel) {
        Store store = Store.get(activity);
        final String by = store.me();
        final long at = System.currentTimeMillis();
        String name = Fmt.fileSafe("Weekend overtime " + Fmt.weekendDay(p.overtime.weekOf, 0)
                + (p.name.isEmpty() ? "" : " " + p.name)) + (excel ? ".xlsx" : ".pdf");
        buildFile(excel ? "Building Excel sheet\u2026" : "Building PDF\u2026", name,
                os -> {
                    if (excel) OvertimeXlsx.write(p, by, at, os);
                    else ReportPdf.writeOvertime(p, by, at, os);
                },
                f -> offer(f, excel ? XLSX : PDF, excel ? saveXlsx : savePdf));
    }

    /** Just the Attendance tab, as a PDF or an Excel sheet, then Share / Open / Save to phone. */
    void exportAttendance(Models.Project p, boolean excel) {
        Store store = Store.get(activity);
        final String by = store.me();
        final long at = System.currentTimeMillis();
        String name = Fmt.fileSafe("Attendance " + Fmt.logDate(p.attendance.date)
                + (p.name.isEmpty() ? "" : " " + p.name)) + (excel ? ".xlsx" : ".pdf");
        buildFile(excel ? "Building Excel sheet\u2026" : "Building PDF\u2026", name,
                os -> {
                    if (excel) AttendanceXlsx.write(p, os);
                    else ReportPdf.writeAttendance(p, by, at, os);
                },
                f -> offer(f, excel ? XLSX : PDF, excel ? saveXlsx : savePdf));
    }

    /** Just the Sea Can Movements tab: format is "pdf", "xlsx", "csv" or "docx". */
    void exportSeaCans(Models.Project p, String format) {
        Store store = Store.get(activity);
        final String by = store.me();
        final long at = System.currentTimeMillis();
        String name = Fmt.fileSafe("Sea can movements" + (p.name.isEmpty() ? "" : " " + p.name) + " "
                + Fmt.logDate(Fmt.todayIso()).replace('/', '-')) + "." + format;
        String message = "xlsx".equals(format) ? "Building Excel sheet\u2026" : "csv".equals(format) ? "Building CSV\u2026"
                : "docx".equals(format) ? "Building Word document\u2026" : "Building PDF\u2026";
        buildFile(message, name,
                os -> {
                    switch (format) {
                        case "xlsx": SeaCanExport.writeXlsx(p, by, at, os); break;
                        case "csv": SeaCanExport.writeCsv(p, os); break;
                        case "docx": ReportDocx.writeSeaCans(p, by, at, os); break;
                        default: ReportPdf.writeSeaCans(p, by, at, os);
                    }
                },
                f -> {
                    switch (format) {
                        case "xlsx": offer(f, XLSX, saveXlsx); break;
                        case "csv": offer(f, CSV, saveCsv); break;
                        case "docx": offer(f, DOCX, saveDocx); break;
                        default: offer(f, PDF, savePdf);
                    }
                });
    }

    private void buildFile(String message, String fileName, Writer writer, Built done) {
        AlertDialog progress = progressDialog(message);
        progress.show();
        BG.execute(() -> {
            File out = null;
            try {
                File dir = new File(activity.getCacheDir(), "exports");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File f = new File(dir, fileName);
                try (OutputStream os = new FileOutputStream(f)) {
                    writer.write(os);
                }
                out = f;
            } catch (Exception e) {
                out = null;
            }
            final File result = out;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (result == null) {
                    Toast.makeText(activity, "Couldn't build the file. Please try again.", Toast.LENGTH_LONG).show();
                    return;
                }
                done.onBuilt(result);
            });
        });
    }

    private void offer(File f, String mime, ActivityResultLauncher<String> saver) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_share, "Share or email", () -> share(f, mime)));
        items.add(new Ui.SheetItem(R.drawable.ic_open, "Open", () -> open(f, mime)));
        items.add(new Ui.SheetItem(R.drawable.ic_download, "Save to phone", () -> {
            pending = f;
            saver.launch(f.getName());
        }));
        Ui.menu(activity, f.getName() + " is ready", items);
    }

    interface Built {
        void onBuilt(File f);
    }

    void export(Models.Project p, Models.DailyLog log, boolean word) {
        build(p, log, word, f -> choose(f, word, p, log));
    }

    /** The daily report as an Excel sheet, then Share / Open / Save to phone. */
    void exportExcel(Models.Project p, Models.DailyLog log) {
        Store store = Store.get(activity);
        final String by = store.me();
        final long at = System.currentTimeMillis();
        buildFile("Building Excel sheet\u2026", Fmt.fileSafe(log.displayTitle()) + ".xlsx",
                os -> ReportXlsx.write(p, log, by, at, os),
                f -> offer(f, XLSX, saveXlsx));
    }

    /** Builds the PDF, then opens your email app addressed to the project's report list. */
    void email(Models.Project p, Models.DailyLog log) {
        if (p.emails.isEmpty()) {
            editEmailList(activity, p, () -> {
                if (!p.emails.isEmpty()) email(p, log);
            });
            return;
        }
        build(p, log, false, f -> sendEmail(f, p, log));
    }

    static void editEmailList(Context c, Models.Project p, Runnable after) {
        Ui.prompt(c, "Report email list", "Emails, separated by commas", Fmt.join(p.emails), "Save", text -> {
            List<String> list = new ArrayList<>();
            for (String e : text.split("[\\s,;]+")) {
                String t = e.trim();
                if (t.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && !list.contains(t)) list.add(t);
            }
            p.emails = list;
            Store.get(c).save();
            Toast.makeText(c, list.isEmpty() ? "No valid email addresses found"
                    : "Saved " + Fmt.plural(list.size(), "address", "addresses"), Toast.LENGTH_SHORT).show();
            if (after != null) after.run();
        });
    }

    private void sendEmail(File f, Models.Project p, Models.DailyLog log) {
        Uri u = uri(f);
        String subject = log.displayTitle() + (p.name.isEmpty() ? "" : " - " + p.name);
        String body = subject + ": " + log.doneCount() + "/" + log.tasks.size() + " fields completed.\n"
                + "Report attached.\n\n" + Store.get(activity).me();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(PDF);
        i.putExtra(Intent.EXTRA_EMAIL, p.emails.toArray(new String[0]));
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Only offer email apps (Gmail, Outlook, Samsung Email...), not every sharing app.
        i.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
        try {
            activity.startActivity(Intent.createChooser(i, "Email report"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No email app found. Try Share instead.", Toast.LENGTH_LONG).show();
            share(f, PDF);
        }
    }

    private void build(Models.Project p, Models.DailyLog log, boolean word, Built done) {
        Store store = Store.get(activity);
        AlertDialog progress = progressDialog(word ? "Building Word report\u2026" : "Building PDF report\u2026");
        progress.show();
        final String by = store.me();
        final long at = System.currentTimeMillis();
        final File photoDir = store.photoDir();
        BG.execute(() -> {
            File out = null;
            try {
                File dir = new File(activity.getCacheDir(), "exports");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File f = new File(dir, Fmt.fileSafe(log.displayTitle()) + (word ? ".docx" : ".pdf"));
                try (OutputStream os = new FileOutputStream(f)) {
                    if (word) {
                        ReportDocx.write(p, log, by, at, photoDir, file -> PhotoUtil.encode(file, 1100), os);
                    } else {
                        ReportPdf.write(p, log, by, at, photoDir, os);
                    }
                }
                out = f;
            } catch (Exception e) {
                out = null;
            }
            final File result = out;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (result == null) {
                    Toast.makeText(activity, "Couldn't build the report. Please try again.", Toast.LENGTH_LONG).show();
                    return;
                }
                done.onBuilt(result);
            });
        });
    }

    private AlertDialog progressDialog(String message) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(activity, 24);
        row.setPadding(pad, pad, pad, pad);
        ProgressBar bar = new ProgressBar(activity);
        TextView t = new TextView(activity);
        t.setText(message);
        t.setTextSize(17);
        t.setPadding(pad, 0, 0, 0);
        row.addView(bar);
        row.addView(t);
        return new MaterialAlertDialogBuilder(activity).setView(row).setCancelable(false).create();
    }

    private void choose(File f, boolean word, Models.Project p, Models.DailyLog log) {
        String mime = word ? DOCX : PDF;
        List<Ui.SheetItem> items = new ArrayList<>();
        if (!word) {
            items.add(new Ui.SheetItem(R.drawable.ic_send, p.emails.isEmpty() ? "Email to report list (set up)"
                    : "Email to report list (" + p.emails.size() + ")", () -> {
                if (p.emails.isEmpty()) editEmailList(activity, p, () -> { if (!p.emails.isEmpty()) sendEmail(f, p, log); });
                else sendEmail(f, p, log);
            }));
        }
        items.add(new Ui.SheetItem(R.drawable.ic_share, "Share or email", () -> share(f, mime)));
        items.add(new Ui.SheetItem(R.drawable.ic_open, "Open", () -> open(f, mime)));
        items.add(new Ui.SheetItem(R.drawable.ic_download, "Save to phone", () -> {
            pending = f;
            (word ? saveDocx : savePdf).launch(f.getName());
        }));
        Ui.menu(activity, f.getName() + " is ready", items);
    }

    private Uri uri(File f) {
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", f);
    }

    private void share(File f, String mime) {
        Uri u = uri(f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.putExtra(Intent.EXTRA_SUBJECT, f.getName().replaceFirst("\\.[a-z]+$", ""));
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(i, "Send report"));
    }

    private void open(File f, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri(f), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No app on this phone can open that file. Try Share instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void onSaveTo(Uri target) {
        File src = pending;
        pending = null;
        if (target == null || src == null) return;
        BG.execute(() -> {
            boolean ok = true;
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = activity.getContentResolver().openOutputStream(target)) {
                if (out == null) throw new java.io.IOException("No output");
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Exception e) {
                ok = false;
            }
            final boolean saved = ok;
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    saved ? "Saved" : "Couldn't save the file", Toast.LENGTH_SHORT).show());
        });
    }
}
