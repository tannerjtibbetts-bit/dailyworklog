package com.dailyworklog.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps all data in memory and writes it to one JSON file on a background thread,
 * so saving never stutters the UI. Photos live in files/photos/.
 */
public final class Store {
    private static Store instance;

    public final List<Models.Project> projects = new ArrayList<>();
    public final List<Models.Link> links = new ArrayList<>();
    public String userName = "";
    /** Number the crew should call or text you on; added under your name in texts. */
    public String contactPhone = "";
    /** Who load securement photos are emailed to, as typed (commas between several). App-wide. */
    public String loadEmailTo = "";
    /** The Gmail address the app sends from. The app password is kept apart (see mailPassword). */
    public String mailFrom = "";
    /** Your profile: the task colours on the zone sheet that are your trade's (dark blue by default). */
    public final java.util.Set<String> myColours = new java.util.LinkedHashSet<>(java.util.Collections.singletonList(ZoneSheet.BLUE));
    /** Your usual answers for the Observation & Coaching form (Formstack links). */
    public final FormPrefill.Details formDetails = new FormPrefill.Details();
    /** Project tabs this person has hidden ("notify", "links", "overtime", "attendance", "notes", "seacans"). */
    public final java.util.Set<String> hiddenTabs = new java.util.LinkedHashSet<>();

    private final Context app;
    private final File dataFile;
    private final File photoDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context c) {
        app = c;
        dataFile = new File(c.getFilesDir(), "worklog.json");
        photoDir = new File(c.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        photoDir.mkdirs();
        load();
    }

    /** Name stamped on completions and photos. */
    public String me() {
        return userName.isEmpty() ? "Me" : userName;
    }

    public File photoFile(String name) {
        return new File(photoDir, name);
    }

    public File photoDir() {
        return photoDir;
    }

    public Models.Project project(String id) {
        if (id == null) return null;
        for (Models.Project p : projects) if (p.id.equals(id)) return p;
        return null;
    }

    public void deletePhoto(Models.Photo p) {
        File f = photoFile(p.file);
        ImageLoader.evict(f);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public void deletePhotos(Models.Task t) {
        for (Models.Photo p : t.photos) deletePhoto(p);
        for (Models.Photo p : t.securedPhotos) deletePhoto(p);
    }

    public void deletePhotos(Models.DailyLog l) {
        for (Models.Task t : l.tasks) deletePhotos(t);
    }

    public void deletePhotos(Models.Project p) {
        for (Models.DailyLog l : p.logs) deletePhotos(l);
        for (Models.Notice n : p.notices) deletePhotos(n);
        for (Models.Note n : p.notes) deletePhotos(n);
    }

    public void deletePhotos(Models.Note n) {
        for (Models.Photo p : n.photos) deletePhoto(p);
    }

    public void deletePhotos(Models.Notice n) {
        for (Models.Photo p : n.photos) deletePhoto(p);
    }

    // ----- email sending: the Gmail app password, encrypted with a key kept in the phone's key store -----

    private static final String MAIL_KEY = "dwl_mail";

    public String mailPassword() {
        String w = app.getSharedPreferences("mail", Context.MODE_PRIVATE).getString("appPassword", "");
        if (w.isEmpty()) return "";
        try {
            byte[] all = java.util.Base64.getDecoder().decode(w);
            javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(javax.crypto.Cipher.DECRYPT_MODE, mailKey(), new javax.crypto.spec.GCMParameterSpec(128, all, 0, 12));
            return new String(ci.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** Spaces are dropped: Google shows the app password in groups of four. */
    public boolean setMailPassword(String password) {
        String p = password.replaceAll("\\s", "");
        try {
            String w = "";
            if (!p.isEmpty()) {
                javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                ci.init(javax.crypto.Cipher.ENCRYPT_MODE, mailKey());
                byte[] iv = ci.getIV();
                byte[] ct = ci.doFinal(p.getBytes(StandardCharsets.UTF_8));
                byte[] all = new byte[iv.length + ct.length];
                System.arraycopy(iv, 0, all, 0, iv.length);
                System.arraycopy(ct, 0, all, iv.length, ct.length);
                w = java.util.Base64.getEncoder().encodeToString(all);
            }
            app.getSharedPreferences("mail", Context.MODE_PRIVATE).edit().putString("appPassword", w).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasMailSetup() {
        return !mailFrom.trim().isEmpty() && !mailPassword().isEmpty();
    }

    private static javax.crypto.SecretKey mailKey() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(MAIL_KEY)) return (javax.crypto.SecretKey) ks.getKey(MAIL_KEY, null);
        javax.crypto.KeyGenerator g = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new android.security.keystore.KeyGenParameterSpec.Builder(MAIL_KEY,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return g.generateKey();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try {
            JSONObject o = new JSONObject(read(dataFile));
            userName = o.optString("userName", "");
            contactPhone = o.optString("contactPhone", "");
            loadEmailTo = o.optString("loadEmailTo", "");
            mailFrom = o.optString("mailFrom", "");
            JSONArray mc = o.optJSONArray("myColours");
            if (mc != null) {
                myColours.clear();
                for (int i = 0; i < mc.length(); i++) myColours.add(mc.optString(i));
            }
            JSONArray ht = o.optJSONArray("hiddenTabs");
            for (int i = 0; ht != null && i < ht.length(); i++) hiddenTabs.add(ht.optString(i));
            JSONObject fd = o.optJSONObject("formDetails");
            if (fd != null) {
                java.util.Iterator<String> keys = fd.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    formDetails.answers.put(k, fd.optString(k, ""));
                }
            }
            JSONArray a = o.optJSONArray("projects");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject p = a.optJSONObject(i);
                    if (p != null) projects.add(Models.Project.fromJson(p));
                }
            }
            JSONArray ls = o.optJSONArray("links");
            if (ls != null) {
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject l = ls.optJSONObject(i);
                    if (l != null) links.add(Models.Link.fromJson(l));
                }
            }
        } catch (Exception e) {
            // Keep a copy of an unreadable file instead of overwriting it.
            //noinspection ResultOfMethodCallIgnored
            dataFile.renameTo(new File(dataFile.getParentFile(),
                    "worklog-unreadable-" + System.currentTimeMillis() + ".json"));
        }
    }

    private static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    public void save() {
        final String json;
        try {
            JSONObject o = new JSONObject();
            o.put("version", 2);
            o.put("userName", userName);
            o.put("contactPhone", contactPhone);
            o.put("loadEmailTo", loadEmailTo);
            o.put("mailFrom", mailFrom);
            JSONArray mc = new JSONArray();
            for (String c : myColours) mc.put(c);
            o.put("myColours", mc);
            JSONArray ht = new JSONArray();
            for (String t : hiddenTabs) ht.put(t);
            o.put("hiddenTabs", ht);
            JSONObject fd = new JSONObject();
            for (java.util.Map.Entry<String, String> e : formDetails.answers.entrySet()) fd.put(e.getKey(), e.getValue());
            o.put("formDetails", fd);
            JSONArray a = new JSONArray();
            for (Models.Project p : projects) a.put(p.toJson());
            o.put("projects", a);
            JSONArray ls = new JSONArray();
            for (Models.Link l : links) ls.put(l.toJson());
            o.put("links", ls);
            json = o.toString();
        } catch (JSONException e) {
            return;
        }
        io.execute(() -> {
            File tmp = new File(dataFile.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(dataFile);
        });
    }
}
