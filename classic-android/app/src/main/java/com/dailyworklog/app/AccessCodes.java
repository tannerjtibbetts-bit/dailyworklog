package com.dailyworklog.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

    /**
     * Finds a request code anywhere in pasted text: the whole text message, or a whole email, where the
     * code may have been wrapped onto the next line and lines may start with "> " quote marks.
     */
    static Request parseRequest(String text) {
        if (text == null) return null;
        for (int at = text.indexOf(REQ); at >= 0; at = text.indexOf(REQ, at + 1)) {
            List<String> parts = requestParts(text, at);
            // Longest first, so a wrapped code isn't read as a shorter one with a cut-off name.
            for (int n = parts.size(); n >= 1; n--) {
                Request r = decodeRequest(String.join("", parts.subList(0, n)));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** A wrapped line is long; a short one ends the code. */
    private static final int WRAPPED_LINE = 60;

    /**
     * The code's pieces starting at "DWL-REQ-": the rest of its line, then each following line that is
     * nothing but code characters (after "> " quote marks and spaces), while the line before was long
     * enough to have been wrapped. A blank line or any other text ends it.
     */
    static List<String> requestParts(String text, int at) {
        List<String> parts = new ArrayList<>();
        int i = at + REQ.length(), start = i;
        while (i < text.length() && isCodeChar(text.charAt(i))) i++;
        parts.add(text.substring(start, i));
        int lineStart = text.lastIndexOf('\n', at) + 1;
        while (true) {
            int lineEnd = text.indexOf('\n', i);
            if (lineEnd < 0) break;
            // The code must run to the end of its line (only spaces after it) ...
            if (!text.substring(i, lineEnd).trim().isEmpty()) break;
            // ... and the line must be long enough to have been wrapped.
            if (text.substring(lineStart, lineEnd).replace("\r", "").length() < WRAPPED_LINE) break;
            int next = lineEnd + 1, j = next;
            while (j < text.length() && (text.charAt(j) == '>' || text.charAt(j) == ' ' || text.charAt(j) == '\t')) j++;
            int k = j;
            while (k < text.length() && isCodeChar(text.charAt(k))) k++;
            if (k == j) break;  // blank or not code
            int end = text.indexOf('\n', k);
            String rest = text.substring(k, end < 0 ? text.length() : end);
            if (!rest.trim().isEmpty()) break;  // more words on that line: not part of the code
            parts.add(text.substring(j, k));
            lineStart = next;
            i = k;
        }
        return parts;
    }

    private static boolean isCodeChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    /** The request in a code's characters (after "DWL-REQ-"), or null if they aren't exactly a request. */
    private static Request decodeRequest(String b64) {
        if (b64.isEmpty()) return null;
        try {
            byte[] raw = unb64(b64);
            // Exactly what an app would have written: same characters back, and proper text.
            if (!b64(raw).equals(b64)) return null;
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw)).toString();
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0x20) return null;
            String[] p = s.split("\\|", 3);
            if (p.length != 3 || !p[0].equals("v1") || p[1].isEmpty() || p[2].trim().isEmpty()) return null;
            return new Request(p[1], p[2].trim());
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException e) {
            return null;
        }
    }

    // ----- asking by email -----

    /** The owner's email from assets/owner_email.txt: its first line, trimmed; "" if blank or not an email. */
    static String ownerEmailFrom(String raw) {
        if (raw == null) return "";
        String line = raw.replace("\r", "").trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        return Fmt.isEmail(line) ? line : "";
    }

    /** The text message (or share) version of a request, as before. */
    static String requestText(String name, String code) {
        return "Daily Work Log access request from " + name + ":\n" + code;
    }

    static String requestSubject(String name) {
        return "Daily Work Log access request from " + name;
    }

    static String requestEmailBody(String name, String code) {
        return requestText(name, code) + "\n\nTo approve: copy this whole email, open Daily Work Log, tap the person icon, "
                + "More\u2026, Approvals / hand over, and paste it.";
    }

    /** mailto:email?subject=…&body=… with every special character percent-encoded (spaces as %20, not +). */
    static String mailto(String email, String subject, String body) {
        return "mailto:" + email + "?subject=" + enc(subject) + "&body=" + enc(body);
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
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
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] unb64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }
}
