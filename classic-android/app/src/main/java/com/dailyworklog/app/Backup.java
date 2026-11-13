package com.dailyworklog.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backup and restore: everything (worklog.json and photos) in one .zip file, so work can move to another
 * phone, including to and from the new app on Android or iPhone. The same layout as the new app's
 * backup.dart: worklog.json at the root, photos/&lt;name&gt; stored as they are. Photos are streamed, never
 * all held in memory.
 */
final class Backup {
    private Backup() {}

    static final String DATA = "worklog.json", PHOTOS = "photos/";

    static String fileName(String isoDate) {
        return "DailyWorkLog-backup-" + isoDate + ".zip";
    }

    /** Writes the backup zip into dir (named for today) and returns it. */
    static File create(Store store, File dir) throws IOException {
        store.flush();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, fileName(Fmt.todayIso()));
        byte[] json;
        try {
            json = store.toJson().toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            zip.putNextEntry(new ZipEntry(DATA));
            zip.write(json);
            zip.closeEntry();
            File[] photos = store.photoDir() == null ? null : store.photoDir().listFiles();
            if (photos != null) {
                java.util.Arrays.sort(photos);
                for (File f : photos) {
                    if (!f.isFile()) continue;
                    // Photos are already compressed JPEGs: store them as they are. A stored entry needs
                    // its size and CRC before it's written, so read the file once for those.
                    ZipEntry e = new ZipEntry(PHOTOS + f.getName());
                    e.setMethod(ZipEntry.STORED);
                    e.setSize(f.length());
                    e.setCompressedSize(f.length());
                    e.setCrc(crc(f));
                    zip.putNextEntry(e);
                    try (InputStream in = new FileInputStream(f)) {
                        copy(in, zip);
                    }
                    zip.closeEntry();
                }
            }
        }
        return out;
    }

    private static long crc(File f) throws IOException {
        CRC32 c = new CRC32();
        byte[] buf = new byte[65536];
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) c.update(buf, 0, n);
        }
        return c.getValue();
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** What's in a backup, shown before restoring it. */
    static final class Info {
        final int projects, photos;
        final String userName;

        Info(int projects, int photos, String userName) {
            this.projects = projects;
            this.photos = photos;
            this.userName = userName;
        }
    }

    /** The backup's worklog.json, or null if there's none or it isn't readable. */
    private static JSONObject data(ZipFile z) {
        ZipEntry e = z.getEntry(DATA);
        if (e == null || e.isDirectory()) return null;
        try (InputStream in = z.getInputStream(e)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            copy(in, b);
            return new JSONObject(b.toString("UTF-8"));
        } catch (IOException | JSONException ex) {
            return null;
        }
    }

    /** A photo entry's file name, or null if it isn't one we'd write (empty, a folder, or with "/" or ".."). */
    static String photoName(ZipEntry e) {
        if (e.isDirectory() || !e.getName().startsWith(PHOTOS)) return null;
        String name = e.getName().substring(PHOTOS.length());
        if (name.isEmpty() || name.contains("/") || name.contains("..")) return null;
        return name;
    }

    /** null if the file isn't a backup (not a zip, or no worklog.json). */
    static Info inspect(File zip) {
        try (ZipFile z = new ZipFile(zip)) {
            JSONObject o = data(z);
            if (o == null) return null;
            org.json.JSONArray p = o.optJSONArray("projects");
            int photos = 0;
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(PHOTOS)) photos++;
            }
            return new Info(p == null ? 0 : p.length(), photos, o.optString("userName", ""));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Replaces everything in the store with the backup: keeps a copy of the current worklog.json next to
     * it first, writes the photos, then loads and saves the backup's data. false if the file isn't a backup.
     */
    static boolean restore(Store store, File zip) throws IOException {
        JSONObject o = unpack(store, zip);
        if (o == null) return false;
        load(store, o);
        return true;
    }

    /** The data part of a restore: replaces the store's data and saves. Run on the main thread in the app. */
    static void load(Store store, JSONObject o) {
        store.replaceWith(o);
        store.save();
        store.flush();
    }

    /**
     * The file part of a restore (safe off the main thread): keeps a copy of the current data and writes the
     * photos. Returns the backup's data for {@link #load}, or null if the file isn't a backup.
     */
    static JSONObject unpack(Store store, File zip) throws IOException {
        ZipFile z;
        try {
            z = new ZipFile(zip);
        } catch (IOException e) {
            return null;
        }
        try {
            JSONObject o = data(z);
            if (o == null) return null;
            store.flush();
            File current = store.dataFile();
            if (current.exists()) {
                File keep = new File(current.getParentFile(), "worklog-before-restore-" + System.currentTimeMillis() + ".json");
                try (InputStream in = new FileInputStream(current); OutputStream out = new FileOutputStream(keep)) {
                    copy(in, out);
                }
            }
            File photoDir = store.photoDir();
            //noinspection ResultOfMethodCallIgnored
            photoDir.mkdirs();
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                String name = photoName(e);
                if (name == null) continue;
                try (InputStream in = z.getInputStream(e); OutputStream out = new FileOutputStream(new File(photoDir, name))) {
                    copy(in, out);
                }
            }
            return o;
        } finally {
            z.close();
        }
    }
}
