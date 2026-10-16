package com.dailyworklog.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Access request and approval codes, passed by text message. No server is involved:
 *
 *  - A new user's phone makes a request code from its install id and the user's name.
 *  - Only the app owner's phone holds the private key; it signs "install id + name + expiry".
 *  - Every copy of the app has the owner's public key built in, so it can check an approval is
 *    genuine (and for this phone and name) but can't make one.
 */
final class AccessCodes {
    private AccessCodes() {}

    static final String REQ = "DWL-REQ-", OK = "DWL-OK-";
    private static final Pattern REQ_CODE = Pattern.compile("DWL-REQ-([A-Za-z0-9_-]+)");

    static final class Request {
        final String installId, name;

        Request(String installId, String name) {
            this.installId = installId;
            this.name = name;
        }
    }

    static final class Approval {
        final String expiry;  // yyyy-MM-dd
        final byte[] signature;
        /** Owner handovers since the original owner ("" if signed by the original owner). */
        final String chain;

        Approval(String expiry, byte[] signature, String chain) {
            this.expiry = expiry;
            this.signature = signature;
            this.chain = chain == null ? "" : chain;
        }
    }

    static String requestCode(String installId, String name) {
        return REQ + b64("v1|" + installId + "|" + name.trim());
    }

    /** Finds a request code anywhere in pasted text (e.g. the whole text message). */
    static Request parseRequest(String text) {
        if (text == null) return null;
        Matcher m = REQ_CODE.matcher(text);
        if (!m.find()) return null;
        try {
            String[] p = new String(unb64(m.group(1)), StandardCharsets.UTF_8).split("\\|", 3);
            if (p.length != 3 || !p[0].equals("v1") || p[1].isEmpty() || p[2].trim().isEmpty()) return null;
            return new Request(p[1], p[2].trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** What the owner signs: ties the approval to one phone, one name and an end date. */
    static byte[] payload(String installId, String name, String expiry) {
        return ("v1|" + installId + "|" + name.trim() + "|" + expiry).getBytes(StandardCharsets.UTF_8);
    }

    static String approvalCode(String expiry, byte[] signature, String chain) {
        return OK + b64("v1|" + expiry + "|") + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
                + (chain == null || chain.isEmpty() ? "" : "." + chain);
    }

    /** Finds an approval code anywhere in pasted text. */
    static Approval parseApproval(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("DWL-OK-([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)(?:\\.([A-Za-z0-9_.*-]+))?").matcher(text);
        if (!m.find()) return null;
        try {
            String[] p = new String(unb64(m.group(1)), StandardCharsets.UTF_8).split("\\|", -1);
            if (p.length < 2 || !p[0].equals("v1") || !p[1].matches("\\d{4}-\\d{2}-\\d{2}")) return null;
            return new Approval(p[1], unb64(m.group(2)), m.group(3));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True if the owner really signed this approval for this phone and name. */
    static boolean verify(PublicKey owner, String installId, String name, Approval a) {
        if (owner == null || a == null) return false;
        PublicKey signer = chainEnd(owner, a.chain);  // the owner at the time, after any handovers
        if (signer == null) return false;
        try {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(signer);
            s.update(payload(installId, name, a.expiry));
            return s.verify(a.signature);
        } catch (Exception e) {
            return false;
        }
    }

    // ----- owner handover -----
    //
    // A chain is the list of handovers since the original owner: "key.sig*key.sig". Each key is a
    // new owner's public key, signed by the owner before it. The app trusts approvals signed by the
    // last key of a chain that leads back to the original owner built into the app.

    static byte[] handoverPayload(String keyUrl) {
        return ("v1|owner|" + keyUrl).getBytes(StandardCharsets.UTF_8);
    }

    static String keyUrl(PublicKey k) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(k.getEncoded());
    }

    static String extendChain(String chain, String keyUrl, byte[] sig) {
        String entry = keyUrl + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        return chain == null || chain.isEmpty() ? entry : chain + "*" + entry;
    }

    /** The current owner's key after following the chain from the original owner, or null if broken. */
    static PublicKey chainEnd(PublicKey root, String chain) {
        PublicKey signer = root;
        if (chain == null || chain.isEmpty()) return signer;
        try {
            for (String entry : chain.split("\\*")) {
                String[] kv = entry.split("\\.");
                if (kv.length != 2) return null;
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initVerify(signer);
                s.update(handoverPayload(kv[0]));
                if (!s.verify(unb64(kv[1]))) return null;
                signer = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(unb64(kv[0])));
            }
            return signer;
        } catch (Exception e) {
            return null;
        }
    }

    static final String OWNER = "DWL-OWNER-";
    private static final int ROUNDS = 120000;

    /** What travels by text: the new owner's key, locked with the 6-digit code, plus the chain. */
    static String handoverCode(byte[] privateKeyPkcs8, String chain, String code) throws Exception {
        byte[] salt = new byte[16], iv = new byte[12];
        java.security.SecureRandom r = new java.security.SecureRandom();
        r.nextBytes(salt);
        r.nextBytes(iv);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, codeKey(code, salt), new javax.crypto.spec.GCMParameterSpec(128, iv));
        c.updateAAD(chain.getBytes(StandardCharsets.UTF_8));
        byte[] ct = c.doFinal(privateKeyPkcs8);
        java.util.Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return OWNER + e.encodeToString(salt) + "~" + e.encodeToString(iv) + "~" + e.encodeToString(ct) + "~" + chain;
    }

    static final class Handover {
        final byte[] privateKeyPkcs8;
        final String chain;

        Handover(byte[] k, String chain) {
            privateKeyPkcs8 = k;
            this.chain = chain;
        }
    }

    static boolean looksLikeHandover(String text) {
        return text != null && text.contains(OWNER);
    }

    /** Unlocks a handover with its 6-digit code; null if the code is wrong or the text was changed. */
    static Handover openHandover(String text, String code) {
        if (text == null) return null;
        Matcher m = Pattern.compile("DWL-OWNER-([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_.*-]+)").matcher(text);
        if (!m.find()) return null;
        try {
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            c.init(javax.crypto.Cipher.DECRYPT_MODE, codeKey(code, unb64(m.group(1))),
                    new javax.crypto.spec.GCMParameterSpec(128, unb64(m.group(2))));
            c.updateAAD(m.group(4).getBytes(StandardCharsets.UTF_8));
            return new Handover(c.doFinal(unb64(m.group(3))), m.group(4));
        } catch (Exception e) {
            return null;
        }
    }

    private static javax.crypto.SecretKey codeKey(String code, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] k = f.generateSecret(new javax.crypto.spec.PBEKeySpec(code.trim().toCharArray(), salt, ROUNDS, 256)).getEncoded();
        return new javax.crypto.spec.SecretKeySpec(k, "AES");
    }

    static PublicKey publicKey(String base64) {
        if (base64 == null || base64.trim().isEmpty()) return null;
        try {
            byte[] der = Base64.getDecoder().decode(base64.replaceAll("\\s+", ""));
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            return null;
        }
    }

    static String encodeKey(PublicKey k) {
        return Base64.getEncoder().encodeToString(k.getEncoded());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] unb64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }
}
