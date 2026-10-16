package com.dailyworklog.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Who may use the app on this phone. Until the owner's key is built into the app, everyone can
 * (so the owner is never locked out). After that: the owner's phone, or a phone with a valid
 * approval code from the owner. A PIN can be added, with "keep me signed in" to skip it.
 */
final class Access {
    private Access() {}

    /** File the owner's phone saves to Downloads; build-termux.sh builds it into the app. */
    static final String KEY_FILE = "dailyworklog-owner-key.txt";
    private static final String ALIAS = "dailyworklog_owner";
    /** Locks a handed-over owner key while it's stored on this phone. */
    private static final String WRAP = "dailyworklog_wrap";
    private static final String PREFS = "access";
    /** Unlocked for as long as the app stays open. */
    private static boolean unlocked;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ----- the owner's key -----

    /** The owner's public key built into this copy of the app, or null if not set up yet. */
    static PublicKey builtInOwnerKey(Context c) {
        try (InputStream in = c.getAssets().open("owner_key.txt")) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            return n <= 0 ? null : AccessCodes.publicKey(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static PublicKey myOwnerKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) return null;
            return ks.getCertificate(ALIAS).getPublicKey();
        } catch (Exception e) {
            return null;
        }
    }

    /** True if this phone holds the owner's private key. */
    static boolean hasOwnerKey() {
        return myOwnerKey() != null;
    }

    /** True if this phone is the owner of this build of the app (the original, or handed over to). */
    static boolean isOwner(Context c) {
        if (prefs(c).getBoolean("handedOver", false)) return false;
        PublicKey built = builtInOwnerKey(c);
        if (built == null) return false;
        if (prefs(c).getString("ownerKey", null) != null) {
            String chain = prefs(c).getString("ownerChain", "");
            return !chain.isEmpty() && AccessCodes.chainEnd(built, chain) != null;
        }
        PublicKey mine = myOwnerKey();
        return mine != null && java.util.Arrays.equals(mine.getEncoded(), built.getEncoded());
    }

    /** Handovers that lead to this phone's owner key ("" for the original owner). */
    private static String ownerChain(Context c) {
        return prefs(c).getString("ownerKey", null) == null ? "" : prefs(c).getString("ownerChain", "");
    }

    /** Signs with this phone's owner key: a handed-over key if it has one, otherwise the original. */
    private static byte[] sign(Context c, byte[] data) throws Exception {
        PrivateKey key;
        String wrapped = prefs(c).getString("ownerKey", null);
        if (wrapped != null) {
            key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(unwrap(wrapped)));
        } else {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            key = (PrivateKey) ks.getKey(ALIAS, null);
        }
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    // ----- handing the owner role to another phone -----

    /**
     * Owner only: makes the text that moves the owner role to another phone, locked with
     * {@code code}. This phone stops approving people but keeps its own access for a year, and
     * every approval it already gave stays valid.
     */
    static String handOver(Context c, String code, String myName) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair next = g.generateKeyPair();
        String url = AccessCodes.keyUrl(next.getPublic());
        String chain = AccessCodes.extendChain(ownerChain(c), url, sign(c, AccessCodes.handoverPayload(url)));
        String text = AccessCodes.handoverCode(next.getPrivate().getEncoded(), chain, code);
        // Approve this phone for itself first, so it can still open the app afterwards
        String name = requestedName(c);
        if (name.isEmpty()) name = myName.trim().isEmpty() ? "Owner" : myName.trim();
        String expiry = yearFromNow();
        String self = AccessCodes.approvalCode(expiry, sign(c, AccessCodes.payload(installId(c), name, expiry)), ownerChain(c));
        prefs(c).edit().putString("name", name).putString("approval", self)
                .putBoolean("handedOver", true).putLong("handedOverAt", System.currentTimeMillis()).apply();
        return text;
    }

    static boolean handedOver(Context c) {
        return prefs(c).getBoolean("handedOver", false);
    }

    static long handedOverAt(Context c) {
        return prefs(c).getLong("handedOverAt", 0);
    }

    /** Kept on the old phone so the code can be read again until the handover is done. */
    static void rememberHandoverCode(Context c, String code) {
        prefs(c).edit().putString("handoverCode", code).apply();
    }

    static String handoverCode(Context c) {
        return prefs(c).getString("handoverCode", "");
    }

    /** Takes the owner role back on this phone (only sensible if the other phone didn't take it). */
    static void undoHandover(Context c) {
        prefs(c).edit().remove("handedOver").remove("handoverCode").apply();
    }

    /** Makes this phone the owner from a handover text and its code. Returns null if fine, or what's wrong. */
    static String acceptHandover(Context c, String text, String code) {
        PublicKey built = builtInOwnerKey(c);
        if (built == null) return "This copy of the app doesn't have an owner set up.";
        if (!AccessCodes.looksLikeHandover(text)) return "That doesn't look like a handover text. Paste the whole text you were sent.";
        AccessCodes.Handover h = AccessCodes.openHandover(text, code);
        if (h == null) return "Wrong code, or the text isn't complete. Check the 6-digit code on the owner phone.";
        PublicKey end = h.chain.isEmpty() ? null : AccessCodes.chainEnd(built, h.chain);
        if (end == null) return "That handover isn't from this app's owner.";
        try {
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(h.privateKeyPkcs8));
            byte[] test = "check".getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(key);
            s.update(test);
            Signature v = Signature.getInstance("SHA256withECDSA");
            v.initVerify(end);
            v.update(test);
            if (!v.verify(s.sign())) return "That handover isn't from this app's owner.";
            prefs(c).edit().putString("ownerKey", wrap(h.privateKeyPkcs8)).putString("ownerChain", h.chain)
                    .remove("handedOver").remove("handoverCode").apply();
            return null;
        } catch (Exception e) {
            return "Couldn't take over: " + e.getMessage();
        }
    }

    private static SecretKey wrapKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(WRAP)) return (SecretKey) ks.getKey(WRAP, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(WRAP, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return g.generateKey();
    }

    private static String wrap(byte[] secret) throws Exception {
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, wrapKey());
        byte[] ct = ci.doFinal(secret);
        return Base64.getEncoder().encodeToString(ci.getIV()) + ":" + Base64.getEncoder().encodeToString(ct);
    }

    private static byte[] unwrap(String wrapped) throws Exception {
        String[] p = wrapped.split(":");
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, wrapKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(p[0])));
        return ci.doFinal(Base64.getDecoder().decode(p[1]));
    }

    private static String yearFromNow() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    /**
     * Makes this phone the owner: creates the signing key inside the phone's secure key store
     * (it can't be copied out) and saves the public half to Downloads for the build.
     */
    static String becomeOwner(Context c) throws Exception {
        PublicKey k = myOwnerKey();
        if (k == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .build());
            k = g.generateKeyPair().getPublic();
        }
        String text = AccessCodes.encodeKey(k);
        saveToDownloads(c, KEY_FILE, text);
        return text;
    }

    private static void saveToDownloads(Context c, String name, String text) throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new Exception("needs Android 10 or newer");
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new Exception("couldn't save");
        try (OutputStream out = c.getContentResolver().openOutputStream(u)) {
            if (out == null) throw new Exception("couldn't save");
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Owner only: the approval code for a request, good for a year. */
    static String approve(Context c, AccessCodes.Request r) throws Exception {
        String expiry = yearFromNow();
        String code = AccessCodes.approvalCode(expiry, sign(c, AccessCodes.payload(r.installId, r.name, expiry)), ownerChain(c));
        // Keep a list of who's been approved
        JSONArray list = approvedList(c);
        JSONObject o = new JSONObject();
        o.put("name", r.name);
        o.put("at", System.currentTimeMillis());
        o.put("expiry", expiry);
        list.put(o);
        prefs(c).edit().putString("approved", list.toString()).apply();
        return code;
    }

    static JSONArray approvedList(Context c) {
        try {
            return new JSONArray(prefs(c).getString("approved", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // ----- this phone's approval -----

    static String installId(Context c) {
        String id = prefs(c).getString("installId", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs(c).edit().putString("installId", id).apply();
        }
        return id;
    }

    static String requestCode(Context c, String name) {
        prefs(c).edit().putString("name", name.trim()).apply();
        return AccessCodes.requestCode(installId(c), name);
    }

    static String requestedName(Context c) {
        return prefs(c).getString("name", "");
    }

    /** Checks and keeps an approval code. Returns null if fine, or what's wrong. */
    static String acceptApproval(Context c, String text) {
        AccessCodes.Approval a = AccessCodes.parseApproval(text);
        if (a == null) return "That doesn't look like an approval code. Paste the whole text you were sent.";
        String name = requestedName(c);
        if (!AccessCodes.verify(builtInOwnerKey(c), installId(c), name, a)) {
            return "That code isn't for this phone and name. Ask for a new one using the request from this phone.";
        }
        if (expired(a.expiry)) return "That approval has expired. Send a new request.";
        prefs(c).edit().putString("approval", text.trim()).apply();
        return null;
    }

    static boolean isApproved(Context c) {
        AccessCodes.Approval a = AccessCodes.parseApproval(prefs(c).getString("approval", null));
        return a != null && !expired(a.expiry)
                && AccessCodes.verify(builtInOwnerKey(c), installId(c), requestedName(c), a);
    }

    private static boolean expired(String expiry) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return today.compareTo(expiry) > 0;
    }

    // ----- PIN and "keep me signed in" -----

    static boolean hasPin(Context c) {
        return prefs(c).getString("pin", null) != null;
    }

    static void setPin(Context c, String pin, boolean remember) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
        prefs(c).edit().putString("salt", s).putString("pin", hash(s, pin)).putBoolean("remember", remember).apply();
        unlocked = true;
    }

    static boolean checkPin(Context c, String pin, boolean remember) {
        String s = prefs(c).getString("salt", "");
        if (!hash(s, pin).equals(prefs(c).getString("pin", ""))) return false;
        prefs(c).edit().putBoolean("remember", remember).apply();
        unlocked = true;
        return true;
    }

    /**
     * Clears the PIN and this phone's approval; the owner approves it again before a new PIN.
     * Only sign-in settings are touched: your saved work (worklog.json and photos, kept by Store)
     * is never deleted, and the install id stays the same so a new approval fits this phone.
     */
    static void forgotPin(Context c) {
        prefs(c).edit().remove("pin").remove("salt").remove("approval").putBoolean("remember", false)
                .putBoolean("hadApproval", true).apply();
        unlocked = false;
    }

    /** True if this phone was approved before (expired, or reset after a forgotten PIN). */
    static boolean hadApproval(Context c) {
        return prefs(c).getBoolean("hadApproval", false) || prefs(c).getString("approval", null) != null;
    }

    static void signOut(Context c) {
        prefs(c).edit().putBoolean("remember", false).apply();
        unlocked = false;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(salt.getBytes(StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(d.digest(pin.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /** What the sign-in screen needs to ask for, or NONE to go straight in. */
    enum Need { NONE, APPROVAL, NEW_PIN, PIN }

    static Need need(Context c) {
        boolean locked = builtInOwnerKey(c) != null;
        if (locked && !isOwner(c) && !isApproved(c)) return Need.APPROVAL;
        if (unlocked || prefs(c).getBoolean("remember", false)) return Need.NONE;
        if (!hasPin(c)) return locked ? Need.NEW_PIN : Need.NONE;
        return Need.PIN;
    }
}
