package com.dailyworklog.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Documents and form files kept in the photos folder: opening, viewing, sharing, copying in, and reading their text. */
final class Docs {
    private Docs() {}

    static int icon(String kind) {
        switch (kind) {
            case Models.Document.PHOTO: return R.drawable.ic_image;
            case Models.Document.PDF: return R.drawable.ic_picture_pdf;
            case Models.Document.DOCX: return R.drawable.ic_description;
            case Models.Document.XLSX: return R.drawable.ic_grid;
            default: return R.drawable.ic_file;
        }
    }

    static String mime(String fileName) {
        switch (Models.Document.kindOf(fileName)) {
            case Models.Document.PHOTO:
                return fileName.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
            case Models.Document.PDF: return "application/pdf";
            case Models.Document.DOCX: return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case Models.Document.XLSX: return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default: return "*/*";
        }
    }

    private static Uri uri(Context c, File f) {
        return FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
    }

    /** Photo: full screen. PDF: its pages. Anything else: the phone's "Open with". */
    static void open(Activity a, File f, String kind, String title) {
        if (!f.exists()) {
            Toast.makeText(a, "That file is missing", Toast.LENGTH_LONG).show();
            return;
        }
        if (Models.Document.PHOTO.equals(kind)) Ui.showPhoto(a, title, f);
        else if (Models.Document.PDF.equals(kind)) showPdf(a, f, title);
        else openWith(a, f);
    }

    static void openWith(Activity a, File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri(a, f), mime(f.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, "Open with"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app on this phone can open that file", Toast.LENGTH_LONG).show();
        }
    }

    static void share(Activity a, File f, String title) {
        Uri u = uri(a, f);
        Intent i = new Intent(Intent.ACTION_SEND).setType(mime(f.getName()))
                .putExtra(Intent.EXTRA_STREAM, u).putExtra(Intent.EXTRA_SUBJECT, title);
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, "Share " + title));
    }

    /** Full-screen, scrolling pages (images), drawn one by one. Tap the title bar to close. */
    private static LinearLayout pagesDialog(Activity a, String title) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF333333);
        TextView bar = new TextView(a);
        bar.setText(title + "   ✕");
        bar.setTextColor(Color.WHITE);
        bar.setTextSize(17);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(a, 20), Ui.dp(a, 36), Ui.dp(a, 20), Ui.dp(a, 14));
        bar.setBackgroundColor(Color.BLACK);
        bar.setContentDescription("Close");
        bar.setOnClickListener(v -> d.dismiss());
        root.addView(bar);
        ScrollView scroll = new ScrollView(a);
        LinearLayout pages = new LinearLayout(a);
        pages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pages);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        d.setContentView(root);
        d.show();
        return pages;
    }

    private static void addPage(Activity a, LinearLayout pages, Bitmap b) {
        ImageView iv = new ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 8);
        pages.addView(iv, lp);
    }

    /** A PDF's pages, drawn at the screen's width. */
    static void showPdf(Activity a, File f, String title) {
        LinearLayout pages = pagesDialog(a, title);
        int width = Math.max(600, a.getResources().getDisplayMetrics().widthPixels);
        PhotoUtil.BG.execute(() -> {
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer r = new PdfRenderer(fd)) {
                for (int i = 0; i < r.getPageCount(); i++) {
                    Bitmap b;
                    try (PdfRenderer.Page p = r.openPage(i)) {
                        int h = Math.round((float) width * p.getHeight() / p.getWidth());
                        b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
                        b.eraseColor(Color.WHITE);
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                    final Bitmap page = b;
                    a.runOnUiThread(() -> addPage(a, pages, page));
                }
            } catch (Exception | OutOfMemoryError e) {
                a.runOnUiThread(() -> Toast.makeText(a, "Couldn't show that PDF", Toast.LENGTH_LONG).show());
            }
        });
    }

    /** A form's pages: its page images, or the PDF / Word / Excel file it came from. */
    static void showForm(Activity a, Store store, Models.ReportForm f) {
        List<File> images = new ArrayList<>();
        File source = null;
        for (String n : f.files) {
            File file = store.photoFile(n);
            if (Models.Document.PHOTO.equals(Models.Document.kindOf(n))) images.add(file);
            else if (source == null) source = file;
        }
        if (images.isEmpty()) {
            if (source == null) Toast.makeText(a, "This form has no pages to show", Toast.LENGTH_LONG).show();
            else open(a, source, Models.Document.kindOf(source.getName()), f.name);
            return;
        }
        LinearLayout pages = pagesDialog(a, f.name);
        int width = Math.max(600, a.getResources().getDisplayMetrics().widthPixels);
        PhotoUtil.BG.execute(() -> {
            for (File img : images) {
                Bitmap b = ImageLoader.decode(img, width);
                if (b != null) a.runOnUiThread(() -> addPage(a, pages, b));
            }
        });
    }

    /** The picked file's own name ("Plan.pdf"), or "". */
    static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                return n == null ? "" : n;
            }
        } catch (Exception ignored) {
            // no name
        }
        return "";
    }

    /** A name without its extension ("Plan.pdf" -> "Plan"). */
    static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Copies a picked file into the photos folder as doc-<uuid>.<ext>; returns the new name, or null. Run off the UI thread. */
    static String copyIn(Context c, Store store, Uri uri, String originalName) {
        String n = originalName.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 && dot < n.length() - 1 ? n.substring(dot + 1).replaceAll("[^a-z0-9]", "") : "";
        if (ext.isEmpty()) {
            String type = c.getContentResolver().getType(uri);
            ext = type == null ? "bin" : type.contains("pdf") ? "pdf" : type.startsWith("image/") ? "jpg"
                    : type.contains("wordprocessing") ? "docx" : type.contains("spreadsheet") ? "xlsx" : "bin";
        }
        String name = Store.newDocFile(ext);
        File dest = store.photoFile(name);
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return null;
            Backup.copy(in, out);
            return name;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            return null;
        }
    }

    /** Renders each PDF page (about 200 dpi, at most 2400 px wide) to a PNG in the photos folder. Run off the UI thread. */
    static List<String> renderPdfPages(Store store, File pdf) throws Exception {
        List<String> out = new ArrayList<>();
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(fd)) {
            for (int i = 0; i < r.getPageCount(); i++) {
                try (PdfRenderer.Page p = r.openPage(i)) {
                    float scale = Math.min(200f / 72f, 2400f / p.getWidth());
                    Bitmap b = Bitmap.createBitmap(Math.round(p.getWidth() * scale), Math.round(p.getHeight() * scale), Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                    String name = Store.newDocFile("png");
                    try (OutputStream os = new FileOutputStream(store.photoFile(name))) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, os);
                    }
                    b.recycle();
                    out.add(name);
                }
            }
        }
        return out;
    }

    interface Lines {
        void onLines(List<String> lines, String error);
    }

    /** OCR of each picture in turn (on the phone), all lines in order. Calls back on the UI thread. */
    static void ocr(Context c, List<File> images, Lines done) {
        List<String> all = new ArrayList<>();
        ocrNext(c, images, 0, all, done);
    }

    private static void ocrNext(Context c, List<File> images, int i, List<String> all, Lines done) {
        if (i >= images.size()) {
            done.onLines(all, null);
            return;
        }
        ScanReader.readLines(c, Uri.fromFile(images.get(i)), new ScanReader.LinesResult() {
            @Override
            public void onLines(List<String> lines) {
                all.addAll(lines);
                ocrNext(c, images, i + 1, all, done);
            }

            @Override
            public void onError(String message) {
                done.onLines(all, "Couldn't read the text on page " + (i + 1));
            }
        });
    }

    /** Deletes a document's or form's files from the photos folder. */
    static void deleteFiles(Store store, List<String> names) {
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            //noinspection ResultOfMethodCallIgnored
            store.photoFile(n).delete();
        }
    }
}
