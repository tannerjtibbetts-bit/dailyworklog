package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** "Apprentice reports" or "Disciplinary action": the blank forms, adding one, filling one in, and the reports done. */
public class ReportsActivity extends FormScreen {
    private Models.Project project;
    private String group;
    private ExportHelper exporter;
    private ActivityResultLauncher<Uri> takePage;
    private ActivityResultLauncher<String[]> pickFile;
    /** Pages taken so far for a new form, and the one the camera is taking now. */
    private ArrayList<String> pages = new ArrayList<>();
    private String pendingPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        project = store.project(getIntent().getStringExtra("project"));
        group = getIntent().getStringExtra("group");
        if (project == null || group == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            ArrayList<String> p = savedInstanceState.getStringArrayList("pages");
            if (p != null) pages = p;
            pendingPage = savedInstanceState.getString("pendingPage");
        }
        exporter = new ExportHelper(this);
        takePage = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            String name = pendingPage;
            pendingPage = null;
            if (name == null) return;
            File f = store.photoFile(name);
            if (!Boolean.TRUE.equals(ok) || !f.exists() || f.length() == 0) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                if (!pages.isEmpty()) askAnotherPage();
                return;
            }
            PhotoUtil.BG.execute(() -> {
                PhotoUtil.normalize(f);
                runOnUiThread(() -> {
                    pages.add(name);
                    askAnotherPage();
                });
            });
        });
        pickFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            String original = Docs.displayName(this, uri);
            PhotoUtil.BG.execute(() -> {
                String name = Docs.copyIn(this, store, uri, original);
                runOnUiThread(() -> {
                    if (name == null) {
                        Toast.makeText(this, "Couldn't copy that file", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String kind = Models.Document.kindOf(name);
                    if (Models.Document.OTHER.equals(kind)) {
                        Docs.deleteFiles(store, java.util.Collections.singletonList(name));
                        Toast.makeText(this, "Pick a PDF, Word or Excel file, or a photo", Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<String> files = new ArrayList<>();
                    files.add(name);
                    nameForm(files, kind, Docs.withoutExtension(original));
                });
            });
        });
        setTitles(isApprentice() ? "Apprentice reports" : "Disciplinary action", project.name);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (project != null) render();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("pages", pages);
        out.putString("pendingPage", pendingPage);
    }

    private boolean isApprentice() {
        return Models.ReportForm.APPRENTICE.equals(group);
    }

    private void render() {
        body.removeAllViews();
        heading("Forms");
        List<Models.ReportForm> forms = new ArrayList<>();
        for (Models.ReportForm f : store.reportForms) if (group.equals(f.group)) forms.add(f);
        if (forms.isEmpty()) para("No forms yet. Tap + Add a form to take photos of a blank form or upload one.");
        for (Models.ReportForm f : forms) {
            card(f.name, Fmt.plural(f.questions.size(), "question", "questions") + " · " + ReportFill.day(f.createdAt),
                    v -> fillIn(f), v -> {
                        formMenu(f);
                        return true;
                    });
        }
        action("+  Add a form", v -> addForm());

        List<Models.FilledReport> done = new ArrayList<>();
        for (Models.FilledReport r : store.filledReports) {
            if (group.equals(r.group) && r.project.equals(project.name)) done.add(r);
        }
        done.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        if (!done.isEmpty()) {
            heading("Filled in");
            for (Models.FilledReport r : done) {
                card(r.person + " · " + r.formName, ReportFill.day(r.createdAt), v -> openReport(r, false), v -> {
                    reportMenu(r);
                    return true;
                });
            }
        }
    }

    // ----- forms -----

    private void formMenu(Models.ReportForm f) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Fill in", () -> fillIn(f)));
        items.add(new Ui.SheetItem(R.drawable.ic_reorder, "Edit questions", () -> startActivity(new Intent(this, QuestionsActivity.class)
                .putExtra("form", f.id))));
        items.add(new Ui.SheetItem(R.drawable.ic_open, "View the form", () -> Docs.showForm(this, store, f)));
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename", () -> Ui.prompt(this, "Rename", "e.g. Apprentice review", f.name, "Save", n -> {
            f.name = n;
            store.save();
            render();
        })));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete form", () -> Ui.confirm(this, "Delete this form?",
                "Reports already filled in stay.", "Delete", () -> {
                    store.reportForms.remove(f);
                    Docs.deleteFiles(store, f.files);
                    store.save();
                    render();
                }), true));
        Ui.menu(this, f.name, items);
    }

    private void addForm() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take photos of a form", () -> {
            pages = new ArrayList<>();
            takeAPage();
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_folder, "Upload a file", () -> pickFile.launch(new String[]{"application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "image/*"})));
        Ui.menu(this, null, items);
    }

    private void takeAPage() {
        pendingPage = Store.newDocFile("jpg");
        try {
            takePage.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", store.photoFile(pendingPage)));
        } catch (ActivityNotFoundException e) {
            pendingPage = null;
            Toast.makeText(this, "No camera app on this phone", Toast.LENGTH_LONG).show();
        }
    }

    private void askAnotherPage() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add another page?")
                .setMessage(Fmt.plural(pages.size(), "page", "pages") + " so far.")
                .setCancelable(false)
                .setPositiveButton("Add page", (d, w) -> takeAPage())
                .setNegativeButton("Done", (d, w) -> {
                    List<String> files = new ArrayList<>(pages);
                    pages = new ArrayList<>();
                    if (!files.isEmpty()) nameForm(files, Models.Document.PHOTO, "");
                })
                .show();
    }

    private void nameForm(List<String> files, String kind, String suggested) {
        Ui.prompt(this, "Name this form", "e.g. Apprentice review", suggested, "Save", n -> readForm(files, kind, n));
    }

    /** Reads the form's text into questions, saves it, then opens "Check the questions". */
    private void readForm(List<String> files, String kind, String name) {
        AlertDialog busy = busy("Reading the form…");
        Models.ReportForm f = new Models.ReportForm();
        f.group = group;
        f.name = name;
        f.kind = kind;
        f.files.addAll(files);
        Docs.Lines done = (lines, error) -> {
            busy.dismiss();
            FormReader.Result r = FormReader.read(lines);
            f.questions = r.questions;
            f.signatures = r.signatures.isEmpty() ? FormReader.defaultSignatures(group) : r.signatures;
            store.reportForms.add(f);
            store.save();
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, QuestionsActivity.class).putExtra("form", f.id));
        };
        File first = store.photoFile(files.get(0));
        if (Models.Document.PHOTO.equals(kind)) {
            List<File> images = new ArrayList<>();
            for (String n : files) images.add(store.photoFile(n));
            Docs.ocr(this, images, done);
            return;
        }
        PhotoUtil.BG.execute(() -> {
            List<String> lines = new ArrayList<>();
            List<File> images = new ArrayList<>();
            String error = null;
            try {
                if (Models.Document.DOCX.equals(kind)) lines = FormReader.docxLines(first);
                else if (Models.Document.XLSX.equals(kind)) lines = FormReader.xlsxLines(first);
                else if (Models.Document.PDF.equals(kind)) {
                    for (String n : Docs.renderPdfPages(store, first)) {
                        f.files.add(n);
                        images.add(store.photoFile(n));
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                error = "Couldn't read that file";
            }
            final List<String> found = lines;
            final String err = error;
            runOnUiThread(() -> {
                if (!images.isEmpty()) Docs.ocr(this, images, done);
                else done.onLines(found, err);
            });
        });
    }

    // ----- filling in -----

    private void fillIn(Models.ReportForm f) {
        List<String> people = ReportFill.people(project, group);
        if (people.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(isApprentice()
                            ? "Nobody on this project's crew is marked Apprentice. In Manage crew, tap a person and pick Apprentice."
                            : "Add your crew first: tap ⋯ at the top, then Manage crew.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Spinner who = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, people);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        who.setAdapter(a);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), 0);
        box.addView(who);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Who is this report for?")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> {
                    long now = System.currentTimeMillis();
                    Models.FilledReport r = new Models.FilledReport();
                    r.formId = f.id;
                    r.formName = f.name;
                    r.group = group;
                    r.person = people.get(Math.max(0, who.getSelectedItemPosition()));
                    r.project = project.name;
                    r.createdAt = now;
                    r.updatedAt = now;
                    ReportFill.autoFill(store, project, f, r, store.filledReports, now);
                    store.filledReports.add(r);
                    store.save();
                    openReport(r, true);
                })
                .show();
    }

    private void openReport(Models.FilledReport r, boolean ask) {
        if (store.form(r.formId) == null) {
            Toast.makeText(this, "The form for this report was deleted", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, FilledReportActivity.class).putExtra("report", r.id)
                .putExtra("project", project.id).putExtra("ask", ask));
    }

    private void reportMenu(Models.FilledReport r) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_open, "Open", () -> openReport(r, false)));
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export PDF", () -> exportPdf(this, exporter, store, r)));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete", () -> Ui.confirm(this, "Delete this report?",
                r.person + " · " + r.formName, "Delete", () -> {
                    store.filledReports.remove(r);
                    store.save();
                    render();
                }), true));
        Ui.menu(this, r.person + " · " + r.formName, items);
    }

    static void exportPdf(android.app.Activity a, ExportHelper exporter, Store store, Models.FilledReport r) {
        Models.ReportForm f = store.form(r.formId);
        if (f == null) {
            Toast.makeText(a, "The form for this report was deleted", Toast.LENGTH_LONG).show();
            return;
        }
        long now = System.currentTimeMillis();
        exporter.exportPdf("Building PDF…", ReportFill.pdfName(r, f.name), os -> FormPdf.write(f, r, now, os));
    }
}
